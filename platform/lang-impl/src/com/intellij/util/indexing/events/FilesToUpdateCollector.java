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
import org.jetbrains.annotations.VisibleForTesting;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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

  /**
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
    synchronized (requestsLock) {
      long version = publishedVersion + 1;
      VersionedRequest versionedRequest = new VersionedRequest(request, version);

      myDirtyFiles.addFile(dirtyQueueProjects, fileId);
      myFilesToUpdate.put(fileId, versionedRequest);

      publishedVersion = version;

      modificationCount.incrementAndGet();
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

  public void removeFileIdFromFilesScheduledForUpdate(int fileId) {
    synchronized (requestsLock) {
      myFilesToUpdate.remove(fileId);
      myDirtyFiles.removeFile(fileId);
      modificationCount.incrementAndGet();
    }
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

  /** @return requests versioned _after_ {@code exclusiveVersion} */
  public @NotNull RequestsSnapshot collectRequestsNewerThan(long exclusiveVersion) {
    return collectRequestsNewerThan(exclusiveVersion, true);
  }

  /**
   * The method returns an empty snapshot when {@code exclusiveVersion} covers the current publication version.
   *
   * @param filterByRequestVersion if {@code true}, include only requests newer than {@code exclusiveVersion}.
   *                               If {@code false}, include all requests after the publication version changes.
   */
  public @NotNull RequestsSnapshot collectRequestsNewerThan(long exclusiveVersion,
                                                            boolean filterByRequestVersion) {
    synchronized (requestsLock) {
      long readUpToVersion = publishedVersion;
      if (exclusiveVersion >= readUpToVersion) {
        return new RequestsSnapshot(exclusiveVersion, readUpToVersion, Collections.emptyList());
      }

      //Materialising the snapshot below is an overhead, and something like a 'live Stream over myFilesToUpdate filtered by
      // version' is much cheaper alternative. But such a 'live' collection does not provide needed consistency: in a 'live
      // iteration' scenario new, concurrently added requests may be listed or not, depending on concurrent iterator
      // implementation details.
      // I.e., there is no guarantee that _all_ requests up to readUpToVersion are listed: some already added requests with
      // (.version < readUpToVersion) may be included but some may be skipped over.
      // But this invariant '_all_ requests up to readUpToVersion are included' is the important one: we advance per-project
      // cursors (='already seen versions') to readUpToVersion based on this exact invariant. If there _could_ be requests with
      // (.version < readUpToVersion) that are not listed by collectRequestsNewerThan() -- it means that 'all requests with version
      // <= readUpToVersion -- are already seen' is not a guarantee anymore.
      // Materialising the snapshot under the requestsLock enforces this invariant, though:
      List<FileIndexingRequest> requests = new ArrayList<>();
      for (VersionedRequest versionedRequest : myFilesToUpdate.values()) {
        if ((!filterByRequestVersion || exclusiveVersion < versionedRequest.version()) &&
            versionedRequest.version() <= readUpToVersion) {
          requests.add(versionedRequest.request());
        }
      }
      return new RequestsSnapshot(exclusiveVersion, readUpToVersion, requests);
    }
  }

  public boolean isScheduledForUpdate(VirtualFile file) {
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
    /** Retained for diagnostics; callers only need the upper boundary to advance their cursor. */
    private final long readAfterVersion;
    private final long readUpToVersion;
    private final @NotNull List<FileIndexingRequest> requests;

    /** Captures both boundaries and detaches the request list from the mutable collector. */
    private RequestsSnapshot(long readAfterVersion,
                             long readUpToVersion,
                             @NotNull List<FileIndexingRequest> requests) {
      this.readAfterVersion = readAfterVersion;
      this.readUpToVersion = readUpToVersion;
      this.requests = List.copyOf(requests);
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
}
