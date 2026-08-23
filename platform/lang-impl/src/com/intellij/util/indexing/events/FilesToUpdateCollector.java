// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;

@Internal
public class FilesToUpdateCollector {
  private static final Logger LOG = Logger.getInstance(FilesToUpdateCollector.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, 1000);

  private final ConcurrentIntObjectMap<VersionedRequest> myFilesToUpdate = createConcurrentIntObjectMap();

  /// This [DirtyFiles] container tracks the ([FileIndexingRequest] -> indexing) phase of the pipeline: `fileId` is
  /// tracked here from the moment [FileIndexingRequest] is created for it, until the moment [FileIndexingRequest] is
  ///  processed by the indexing pipeline, and the indexes are updated accordingly.
  /// The project(s) owner(s) for a `fileId` is typically transferred from the previous phase ([ChangedFilesCollector])
  private final DirtyFiles myDirtyFiles = new DirtyFiles();
  private final VisitedRequestsVersionPerProject projectCursors = new VisitedRequestsVersionPerProject();

  /** Increments on each request publication. */
  //@GuardedBy("requestsLock")
  private long publishedVersion;

  /**
   * Protects _joint_ modifications of ({@link #publishedVersion} + {@link #myDirtyFiles} + {@link #myFilesToUpdate}).
   * Still, we utilise {@link #myFilesToUpdate} concurrent nature where possible, to reduce the duration of locked regions.
   */
  private final Object requestsLock = new Object();

  /** Increments on each change in myFilesToUpdate content -- i.e., it is 'version' of myFilesToUpdate content */
  private final AtomicLong modificationCount = new AtomicLong(0);

  /** Registers the project cursor and its dirty-file state as one operation. */
  public void registerProject(@NotNull Project project) {
    synchronized (requestsLock) {
      if (projectCursors.registerProject(project)) {
        myDirtyFiles.addProject(project);
      }
    }
  }

  /** Removes the project cursor and its dirty-file state as one operation. */
  public void unregisterProject(@NotNull Project project) {
    synchronized (requestsLock) {
      if (projectCursors.unregisterProject(project)) {
        myDirtyFiles.removeProject(project);
      }
    }
  }

  /**
   * @param request            must be new instance every time: one should NOT re-use same request instance -- request identity is used to trace
   *                           request completion in a concurrent environment
   * @param containingProjects projects request.file is belong to. Used mostly for diagnostics
   * @param dirtyQueueProjects projects request.file is belong to. Used to actually put the file into
   *                           apt queue(s)
   */
  public void scheduleForUpdate(@NotNull FileIndexingRequest request,
                                @NotNull Set<Project> containingProjects,
                                @NotNull Collection<? extends Project> dirtyQueueProjects) {
    if (!request.isDeleteRequest() && request.getFile().isDirectory()) {
      THROTTLED_LOG.warn("Directory was passed for indexing unexpectedly: " + request.getFile().getPath(), new Throwable());
    }
    VirtualFile file = request.getFile();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!request.isDeleteRequest() && containingProjects.isEmpty()) {
        LOG.error("File without project should not be added to FilesToUpdateCollector because it will not be indexed " +
                  "(projects pick own update requests and all delete requests from this collector). " +
                  "File=" + file.getPath());
      }
    }
    IndexingEventsLogger.tryLog("ADD_TO_UPDATE", file);
    int fileId = request.getFileId();

    VersionedRequest replacedRequest;
    long version;
    synchronized (requestsLock) {
      version = publishedVersion + 1;
      VersionedRequest versionedRequest = new VersionedRequest(request, version);

      myDirtyFiles.addFile(dirtyQueueProjects, fileId);
      replacedRequest = myFilesToUpdate.put(fileId, versionedRequest);

      publishedVersion = version;

      modificationCount.incrementAndGet();
    }

    if (replacedRequest != null && LOG.isDebugEnabled()) {
      LOG.debug("Replaced indexing request #" + fileId + ": " + replacedRequest + " -> " + request);
    }
  }

  public @NotNull DirtyFiles getDirtyFiles() {
    return myDirtyFiles;
  }

  public void removeScheduledFileFromUpdate(@NotNull VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    VersionedRequest alreadyScheduledRequest = myFilesToUpdate.get(fileId);
    if (alreadyScheduledRequest != null && !alreadyScheduledRequest.request().isDeleteRequest()) {
      boolean removed = false;
      synchronized (requestsLock) {
        if (myFilesToUpdate.remove(fileId, alreadyScheduledRequest)) {
          myDirtyFiles.removeFile(fileId);
          modificationCount.incrementAndGet();
          removed = true;
        }
      }

      if (removed) IndexingEventsLogger.tryLog("PULL_OUT_FROM_UPDATE", fileId);
    }
  }

  /** Removes the request only if the supplied instance still the actual one */
  public boolean removeIfCurrent(@NotNull FileIndexingRequest expectedRequest) {
    int fileId = expectedRequest.getFileId();

    VersionedRequest currentRequest = myFilesToUpdate.get(fileId);
    if (currentRequest == null || currentRequest.request() != expectedRequest) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("stale removeIfCurrent(#" + fileId + ", expected: " + expectedRequest + ", current=" + currentRequest);
      }
      return false;
    }

    synchronized (requestsLock) {
      if (myFilesToUpdate.remove(fileId, currentRequest)) {
        myDirtyFiles.removeFile(fileId);
        modificationCount.incrementAndGet();
        return true;
      }
    }
    return false; //should also log 'stale removeIfCurrent...'?
  }

  /** @return whether the instance is still the actual one */
  public boolean isCurrent(@NotNull FileIndexingRequest request) {
    VersionedRequest currentRequest = myFilesToUpdate.get(request.getFileId());
    return currentRequest != null && currentRequest.request() == request;
  }

  public void clear() {
    synchronized (requestsLock) {
      myDirtyFiles.clear();
      myFilesToUpdate.clear();
      modificationCount.incrementAndGet();
    }
  }

  public long modificationCount(){
    return modificationCount.get();
  }

  public Iterator<@NotNull FileIndexingRequest> getFilesToUpdateAsIterator() {
    return ContainerUtil.mapIterator(myFilesToUpdate.values().iterator(), VersionedRequest::request);
  }

  public Collection<FileIndexingRequest> getFilesToUpdate() {
    return myFilesToUpdate.isEmpty()
           ? Collections.emptyList()
           : Collections.unmodifiableCollection(new RequestsView());
  }

  /** Captures the request suffix for the current project registration. */
  public @NotNull RequestsSnapshot requestsFor(@Nullable Project project,
                                               boolean filterByRequestVersion) {
    synchronized (requestsLock) {
      VisitedRequestsVersionPerProject.Registration registration = project == null ? null : projectCursors.registrationFor(project);
      long cursor = registration == null ? -1 : registration.cursor().orElse(-1);
      return collectRequestsNewerThanUnderLock(cursor, filterByRequestVersion, registration);
    }
  }

  //@GuardedBy(requestsLock)
  private @NotNull RequestsSnapshot collectRequestsNewerThanUnderLock(
    long exclusiveVersion,
    boolean filterByRequestVersion,
    @Nullable VisitedRequestsVersionPerProject.Registration registration
  ) {
    long readUpToVersion = publishedVersion;
    if (exclusiveVersion >= readUpToVersion) {
      return new RequestsSnapshot(exclusiveVersion, readUpToVersion, Collections.emptyList(), registration);
    }

    // Materialize the snapshot under the lock to keep its boundary consistent with all included requests.
    List<FileIndexingRequest> requests = new ArrayList<>();
    for (VersionedRequest versionedRequest : myFilesToUpdate.values()) {
      if ((!filterByRequestVersion || exclusiveVersion < versionedRequest.version()) &&
          versionedRequest.version() <= readUpToVersion) {
        requests.add(versionedRequest.request());
      }
    }
    return new RequestsSnapshot(exclusiveVersion, readUpToVersion, requests, registration);
  }

  /** Advances the cursor after every accepted request in the snapshot completes. */
  public void advanceCursor(@NotNull Project project, @NotNull RequestsSnapshot consumedSnapshot) {
    if (consumedSnapshot.registration != null) {
      synchronized (requestsLock) {
        if (consumedSnapshot.noRequestsIn(myFilesToUpdate)) {
          projectCursors.advanceCursor(project, consumedSnapshot.registration, consumedSnapshot.readUpToVersion);
        }
      }
    }
  }

  public boolean isScheduledForUpdate(@NotNull VirtualFile file) {
    return containsFileId(FileBasedIndex.getFileId(file));
  }

  public boolean containsFileId(int fileId) {
    return myFilesToUpdate.containsKey(fileId);
  }

  /** Keep {@link FileIndexingRequest} equality and serialization, while adding a version */
  private record VersionedRequest(@NotNull FileIndexingRequest request, long version) {
  }

  /** Immutable requests with versions in the {@code (readAfterVersion, readUpToVersion]} range. */
  public static final class RequestsSnapshot {
    private final @Nullable VisitedRequestsVersionPerProject.Registration registration;
    /** Retained for diagnostics; callers only need the upper boundary to advance their cursor. */
    private final long readAfterVersion;
    private final long readUpToVersion;
    private final @NotNull List<FileIndexingRequest> requests;

    /** Captures both boundaries and detaches the request list from the mutable collector. */
    private RequestsSnapshot(long readAfterVersion,
                             long readUpToVersion,
                             @NotNull List<FileIndexingRequest> requests,
                             @Nullable VisitedRequestsVersionPerProject.Registration registration) {
      this.readAfterVersion = readAfterVersion;
      this.readUpToVersion = readUpToVersion;
      this.requests = List.copyOf(requests);
      this.registration = registration;
    }

    /** @return inclusive upper publication boundary captured by this snapshot */
    @VisibleForTesting
    public long readUpToVersion() {
      return readUpToVersion;
    }

    /** @return immutable requests currently present between the snapshot boundaries */
    public @NotNull List<FileIndexingRequest> requests() {
      return requests;
    }

    /** Returns the same boundary with only requests accepted for processing. */
    public @NotNull RequestsSnapshot filter(@NotNull Predicate<? super FileIndexingRequest> predicate) {
      return new RequestsSnapshot(
        readAfterVersion,
        readUpToVersion,
        requests.stream().filter(predicate).toList(),
        registration
      );
    }

    private boolean noRequestsIn(@NotNull ConcurrentIntObjectMap<VersionedRequest> currentRequests) {
      for (FileIndexingRequest request : requests) {
        VersionedRequest currentRequest = currentRequests.get(request.getFileId());
        if (currentRequest != null && currentRequest.request() == request) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return "RequestsSnapshot[versions=(" + readAfterVersion + ", " + readUpToVersion + "], " +
             "requestsCount=" + requests.size() + ']';
    }
  }

  /** Preserves the collector's live read-only values view while keeping versions private. */
  private final class RequestsView extends AbstractCollection<FileIndexingRequest> {
    @Override
    public @NotNull Iterator<FileIndexingRequest> iterator() {
      return getFilesToUpdateAsIterator();
    }

    @Override
    public int size() {
      return myFilesToUpdate.size();
    }
  }

  /**
   * Tracks [project -> max(version of indexing requests already visited by the project)].
   * Not thread-safe: owner must supply a synchronization, if needed.
   */
  @Internal
  @VisibleForTesting
  public static final class VisitedRequestsVersionPerProject {
    /// Values:
    /// - `cursors[project].cursor = OptionalLong(actualCursorValue)` => project is open and registered, and its cursor is initialized
    /// - `cursors[project].cursor = OptionalLong.empty()`            => project is open and registered, but its cursor is not initialized
    /// - `cursors[project] = null`                                   => project is not open and registered
    private final Map<Project, Registration> cursors = new HashMap<>();

    /**
     * Registers a project for tracking; with cursor initially un-initialized.
     * Does not modify an existing registration.
     */
    public boolean registerProject(@NotNull Project project) {
      if (cursors.containsKey(project)) {
        return false;
      }
      cursors.put(project, new Registration());
      return true;
    }

    /** Removes a project from tracking. */
    public boolean unregisterProject(@NotNull Project project) {
      return cursors.remove(project) != null;
    }

    /** Returns the current registration, or {@code null} for an unregistered project. */
    @Nullable Registration registrationFor(@NotNull Project project) {
      return cursors.get(project);
    }

    /** @return the project cursor, or {@code defaultIfUnknown} if the project is uninitialized or unregistered */
    public long cursorFor(@NotNull Project project, long defaultIfUnknown) {
      Registration registration = cursors.get(project);
      return registration == null ? defaultIfUnknown : registration.cursor().orElse(defaultIfUnknown);
    }

    /** Advances a registered cursor to {@code version} */
    @VisibleForTesting
    public void advanceCursor(@NotNull Project project, long version) {
      Registration registration = cursors.get(project);
      if (registration != null) {
        advanceCursor(project, registration, version);
      }
    }

    /**
     * Advances the expected registration to {@code advanceToVersion}.
     * Does not update if a project was unregistered/registered back (i.e. a current registration != expectedRegistration)
     */
    void advanceCursor(@NotNull Project project,
                       @NotNull Registration expectedRegistration,
                       long advanceToVersion) {
      Registration currentRegistration = cursors.get(project);
      if (currentRegistration != expectedRegistration) {
        return;
      }

      OptionalLong current = currentRegistration.cursor();
      if (current.isEmpty() || current.getAsLong() < advanceToVersion) {
        currentRegistration.advanceTo(advanceToVersion);
      }
    }

    /**
     * @return the minimum cursor if all registered projects initialized their cursors;
     *         empty if a cursor is uninitialized or no project is registered.
     */
    public @NotNull OptionalLong minimumCursor() {
      if (cursors.isEmpty()) {
        return OptionalLong.empty();
      }

      long minimum = Long.MAX_VALUE;
      for (Registration registration : cursors.values()) {
        OptionalLong cursor = registration.cursor();
        if (cursor.isEmpty()) {
          return OptionalLong.empty();
        }
        minimum = Math.min(minimum, cursor.getAsLong());
      }
      return OptionalLong.of(minimum);
    }

    /**
     * Stores cursor for a project
     * Introduced to differentiate the same project if it is registered/unregistered multiple times
     */
    static final class Registration {
      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
      private OptionalLong cursor = OptionalLong.empty();

      @NotNull OptionalLong cursor() {
        return cursor;
      }

      void advanceTo(long version) {
        cursor = OptionalLong.of(version);
      }


      @Override
      public String toString() {
        return "Registration[cursor: " + cursor + ']';
      }
    }
  }
}
