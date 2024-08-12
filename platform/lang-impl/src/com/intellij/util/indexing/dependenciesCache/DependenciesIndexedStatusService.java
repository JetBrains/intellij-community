// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

import static com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;

/**
 * Service is used by platform to calculate which values provided by any of {@link AdditionalLibraryRootsProvider},
 * {@link IndexableSetContributor} or {@link com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy} were changed
 * to rescan only them on rootsChanged event with
 * {@link com.intellij.openapi.project.RootsChangeRescanningInfo#RESCAN_DEPENDENCIES_IF_NEEDED}
 * <p>
 * Note that non-null {@link SyntheticLibrary#getExcludeFileCondition()} is considered always changed, and then
 * {@link SyntheticLibrary} is rescanned incrementally only if its {@link AdditionalLibraryRootsProvider}
 * returns only one library. {@link SyntheticLibrary} with null {@link SyntheticLibrary#getExcludeFileCondition()}
 * and non-null comparisonId is always rescanned incrementally, matched by comparisonId,
 * and having constant exclusion condition {@link SyntheticLibrary.ExcludeFileCondition}.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
@ApiStatus.Experimental
public final class DependenciesIndexedStatusService {
  private static final Logger LOG = Logger.getInstance(DependenciesIndexedStatusService.class);
  @VisibleForTesting
  static final Key<Boolean> ENFORCEMENT_USAGE_TEST_MODE_FLAG = new Key<>("enforce.DependenciesIndexedStatusService.usage");


  public static @NotNull DependenciesIndexedStatusService getInstance(@NotNull Project project) {
    return project.getService(DependenciesIndexedStatusService.class);
  }

  public static boolean shouldBeUsed() {
    return Registry.is("use.dependencies.cache.service", false) || TestModeFlags.is(ENFORCEMENT_USAGE_TEST_MODE_FLAG);
  }


  private final Object LOCK = new Object();
  private volatile int statusVersionCounter = 0;
  private final @NotNull Project project;
  private @Nullable MyStatus lastIndexedStatus;

  private MyStatus currentlyCollectedStatus;
  private final ThreadLocal<Boolean> listenToStatus = ThreadLocal.withInitial(() -> Boolean.FALSE);

  DependenciesIndexedStatusService(@NotNull Project project) {
    this.project = project;
  }

  public boolean shouldSaveStatus() {
    return shouldBeUsed() && listenToStatus.get();
  }

  public void startCollectingStatus() {
    if (!shouldBeUsed()) return;
    listenToStatus.set(true);
    synchronized (LOCK) {
      currentlyCollectedStatus = new MyStatus(getVersion(), null, null, null);
    }
  }

  private int getVersion() {
    synchronized (LOCK) {
      return statusVersionCounter++;
    }
  }

  public @NotNull StatusMark finishCollectingStatus() {
    listenToStatus.set(false);
    MyStatus status;
    synchronized (LOCK) {
      status = currentlyCollectedStatus;
      currentlyCollectedStatus = null;
    }
    return status;
  }

  public void indexingFinished(boolean successfully, @Nullable StatusMark mark) {
    synchronized (LOCK) {
      if (successfully && mark != null) {
        String message = ((MyStatus)mark).collectionStatusMessage();
        if (message == null) {
          lastIndexedStatus = (MyStatus)mark;
        }
        else {
          LOG.error("Status of indexed iterators was not collected: " + message);
        }
      }
      currentlyCollectedStatus = null;
    }
  }


  public void saveExcludePolicies() {
    if (!shouldSaveStatus()) return;
    List<ExcludePolicyDescriptor> descriptors = ExcludePolicyDescriptor.collectDescriptors(project);
    synchronized (LOCK) {
      currentlyCollectedStatus = currentlyCollectedStatus.createWithExcludePolicies(getVersion(), descriptors);
    }
  }


  public @NotNull List<IndexableFilesIterator> saveLibsAndInstantiateLibraryIterators() {
    LOG.assertTrue(shouldSaveStatus());
    List<SyntheticLibraryDescriptor> libraries = collectAdditionalLibDescriptors(project);
    synchronized (LOCK) {
      currentlyCollectedStatus = currentlyCollectedStatus.createWithLibs(getVersion(), libraries);
    }
    return ContainerUtil.map(libraries, lib -> lib.toIndexableIterator());
  }

  private static @NotNull List<SyntheticLibraryDescriptor> collectAdditionalLibDescriptors(@NotNull Project project) {
    List<SyntheticLibraryDescriptor> libraries = new ArrayList<>();
    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      Set<String> comparisonIds = new HashSet<>();
      Collection<SyntheticLibrary> allLibs = provider.getAdditionalProjectLibraries(project);
      for (SyntheticLibrary library : allLibs) {
        String id = library.getComparisonId();
        if (id != null && !comparisonIds.add(id)) {
          LOG.error("Multiple libraries have comparison id " + id + ": " +
                    ContainerUtil.filter(allLibs, lib -> id.equals(lib.getComparisonId())));
        }
        libraries.add(new SyntheticLibraryDescriptor(library, provider));
      }
    }
    return libraries;
  }

  public @NotNull List<IndexableFilesIterator> saveIndexableSetsAndInstantiateIterators() {
    LOG.assertTrue(shouldSaveStatus());
    @NotNull List<IndexableSetContributorDescriptor> descriptors = IndexableSetContributorDescriptor.collectDescriptors(project);
    synchronized (LOCK) {
      currentlyCollectedStatus = currentlyCollectedStatus.createWithIndexableSets(getVersion(), descriptors);
    }
    return ContainerUtil.flatMap(descriptors, descriptor -> descriptor.toIndexableIterators());
  }

  public @Nullable Pair<@NotNull Collection<? extends IndexableIteratorBuilder>, @NotNull StatusMark> getDeltaWithLastIndexedStatus() {
    if (!shouldBeUsed()) return null;
    MyStatus statusBefore;
    synchronized (LOCK) {
      statusBefore = lastIndexedStatus;
    }
    if (statusBefore == null) {
      //rootsChanged event happened before the first scanning was finished
      return null;
    }
    MyStatus statusAfter = getCurrentStatus();
    Collection<? extends IndexableIteratorBuilder> iterators = getDependenciesIterators(project, statusBefore, statusAfter);
    return new Pair<>(iterators, statusAfter);
  }

  private @NotNull MyStatus getCurrentStatus() {
    List<SyntheticLibraryDescriptor> libraries = collectAdditionalLibDescriptors(project);
    List<IndexableSetContributorDescriptor> contributors = IndexableSetContributorDescriptor.collectDescriptors(project);
    List<ExcludePolicyDescriptor> excludePolicies = ExcludePolicyDescriptor.collectDescriptors(project);
    return new MyStatus(getVersion(), libraries, contributors, excludePolicies);
  }


  private static @NotNull Collection<? extends IndexableIteratorBuilder> getDependenciesIterators(@NotNull Project project,
                                                                                                  @NotNull MyStatus before,
                                                                                                  @NotNull MyStatus after) {

    List<IndexableIteratorBuilder> result = new ArrayList<>(
      RescannedRootsUtil.getUnexcludedRootsIteratorBuilders(project, before.libraries, before.excludePolicyDescriptors, after.libraries));

    MultiMap<AdditionalLibraryRootsProvider, SyntheticLibraryDescriptor> afterLibs = after.librariesToMap();
    MultiMap<AdditionalLibraryRootsProvider, SyntheticLibraryDescriptor> beforeLibs = before.librariesToMap();
    for (Map.Entry<AdditionalLibraryRootsProvider, Collection<SyntheticLibraryDescriptor>> entry : afterLibs.entrySet()) {
      result.addAll(RescannedRootsUtil.getLibraryIteratorBuilders(beforeLibs.get(entry.getKey()), entry.getValue()));
    }

    Map<IndexableSetContributor, IndexableSetContributorDescriptor> beforeContributors = before.contributorsToMap();
    for (IndexableSetContributorDescriptor contributorDescriptor : after.contributors) {
      result.addAll(RescannedRootsUtil.getIndexableSetIteratorBuilders(beforeContributors.get(contributorDescriptor.contributor),
                                                                       contributorDescriptor));
    }

    return result;
  }


  private record MyStatus(int version,
                          @Nullable List<? extends SyntheticLibraryDescriptor> libraries,
                          @Nullable List<? extends IndexableSetContributorDescriptor> contributors,
                          @Nullable List<? extends ExcludePolicyDescriptor> excludePolicyDescriptors) implements StatusMark {
    private MyStatus(int version,
                     @Nullable List<? extends SyntheticLibraryDescriptor> libraries,
                     @Nullable List<? extends IndexableSetContributorDescriptor> contributors,
                     @Nullable List<? extends ExcludePolicyDescriptor> excludePolicyDescriptors) {
      this.version = version;
      this.libraries = libraries == null ? null : List.copyOf(libraries);
      this.contributors = contributors == null ? null : List.copyOf(contributors);
      this.excludePolicyDescriptors = excludePolicyDescriptors == null ? null : List.copyOf(excludePolicyDescriptors);
    }

    private MyStatus createWithLibs(int version, @NotNull List<? extends SyntheticLibraryDescriptor> newLibraries) {
      return new MyStatus(version, newLibraries, contributors, excludePolicyDescriptors);
    }

    private MyStatus createWithIndexableSets(int version, @NotNull List<? extends IndexableSetContributorDescriptor> newContributors) {
      return new MyStatus(version, libraries, newContributors, excludePolicyDescriptors);
    }

    private MyStatus createWithExcludePolicies(int version, @NotNull List<? extends ExcludePolicyDescriptor> newExcludePolicyDescriptors) {
      return new MyStatus(version, libraries, contributors, newExcludePolicyDescriptors);
    }

    private @Nullable String collectionStatusMessage() {
      String message = "";
      if (libraries == null) {
        message += "No AdditionalLibraries data provided. ";
      }
      if (contributors == null) {
        message += "No IndexableSetContributor data provided. ";
      }
      if (excludePolicyDescriptors == null) {
        message += "No ExcludeDirectoryPolicy data provided. ";
      }
      return message.isEmpty() ? null : message;
    }

    public @NotNull MultiMap<AdditionalLibraryRootsProvider, SyntheticLibraryDescriptor> librariesToMap() {
      LOG.assertTrue(libraries != null);
      MultiMap<AdditionalLibraryRootsProvider, SyntheticLibraryDescriptor> result = new MultiMap<>();
      for (SyntheticLibraryDescriptor library : libraries) {
        result.putValue(library.provider, library);
      }
      return result;
    }

    public @NotNull Map<IndexableSetContributor, IndexableSetContributorDescriptor> contributorsToMap() {
      LOG.assertTrue(contributors != null);
      return ContainerUtil.map2Map(contributors, descriptor -> com.intellij.openapi.util.Pair.create(descriptor.contributor, descriptor));
    }
  }

  public interface StatusMark {
    static @Nullable StatusMark mergeStatus(@Nullable StatusMark one, @Nullable StatusMark another) {
      if (one == null) return another;
      if (another == null) return one;
      if (((MyStatus)one).version > ((MyStatus)another).version) return one;
      return another;
    }
  }
}