// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TripleFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND;

@Service(Service.Level.PROJECT)
public final class UnknownSdkTracker {
  private static final Logger LOG = Logger.getInstance(UnknownSdkTracker.class);

  public static @NotNull UnknownSdkTracker getInstance(@NotNull Project project) {
    return project.getService(UnknownSdkTracker.class);
  }

  private final @NotNull Project myProject;

  public UnknownSdkTracker(@NotNull Project project) {
    myProject = project;
  }

  private static boolean isEnabled() {
    return Registry.is("unknown.sdk") && UnknownSdkResolver.EP_NAME.hasAnyExtensions();
  }

  public @NotNull List<UnknownSdkFix> collectUnknownSdks(@NotNull UnknownSdkBlockingCollector collector,
                                                         @NotNull ProgressIndicator indicator) {
    if (!isEnabled()) {
      return List.of();
    }

    var snapshot = collector.collectSdksBlocking();
    var action = createProcessSdksAction(snapshot);
    return action == null ? List.of() : action.apply(indicator);
  }

  private @NotNull UnknownSdkTrackerTask newUpdateTask(@NotNull ShowStatusCallback showStatus,
                                                       @NotNull Predicate<? super UnknownSdkSnapshot> shouldProcessSnapshot) {
    return new UnknownSdkTrackerTask() {
      @Override
      public @Nullable UnknownSdkCollector createCollector() {
        if (!isEnabled() || !Registry.is("unknown.sdk.auto")) {
          showStatus.showEmptyStatus();
          return null;
        }
        return new UnknownSdkCollector(myProject);
      }

      @Override
      public void onLookupCompleted(@NotNull UnknownSdkSnapshot snapshot) {
        if (!shouldProcessSnapshot.test(snapshot)) {
          return;
        }

        var action = createProcessSdksAction(snapshot, showStatus);
        if (action == null) {
          return;
        }

        ProgressManager.getInstance()
          .run(new Task.Backgroundable(myProject, ProjectBundle.message("progress.title.resolving.sdks"), false, ALWAYS_BACKGROUND) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              action.run(indicator);
            }
          });
      }
    };
  }

  private final Predicate<UnknownSdkSnapshot> myIsNewSnapshot = new Predicate<>() {
    private UnknownSdkSnapshot myPreviousRequestCache = null;

    @Override
    public boolean test(UnknownSdkSnapshot snapshot) {
      //there is nothing to do if we see the same snapshot, IDEA-236153
      if (snapshot.equals(myPreviousRequestCache)) return false;
      myPreviousRequestCache = snapshot;
      return true;
    }
  };

  public void updateUnknownSdks() {
    UnknownSdkTrackerQueue.Companion.getInstance(myProject)
      .queue(newUpdateTask(new DefaultShowStatusCallbackAdapter(), myIsNewSnapshot));
  }

  private static boolean allowFixesFor(@NotNull SdkTypeId type) {
    return UnknownSdkResolver.EP_NAME.findFirstSafe(it -> it.supportsResolution(type)) != null;
  }

  private static @NotNull <E extends UnknownSdk> List<E> filterOnlyAllowedEntries(@NotNull List<? extends E> input) {
    List<E> copy = new ArrayList<>();
    for (E item : input) {
      SdkType type = item.getSdkType();

      if (allowFixesFor(type)) {
        copy.add(item);
      }
    }

    return copy;
  }

  private static @NotNull List<Sdk> filterOnlyAllowedSdkEntries(@NotNull List<? extends Sdk> input) {
    List<Sdk> copy = new ArrayList<>();
    for (Sdk item : input) {
      SdkTypeId type = item.getSdkType();

      if (allowFixesFor(type)) {
        copy.add(item);
      }
    }

    return copy;
  }

  private @Nullable Function<ProgressIndicator, List<UnknownSdkFix>> createProcessSdksAction(@NotNull UnknownSdkSnapshot snapshot) {
    //it may run on EDT, e.g. in default task
    //we cannot use snapshot#missingSdks here, because it affects other IDEs/languages where our logic is not good enough
    List<UnknownSdk> fixable = filterOnlyAllowedEntries(snapshot.getResolvableSdks());
    List<Sdk> usedSdks = filterOnlyAllowedSdkEntries(snapshot.getKnownSdks());

    if (fixable.isEmpty() && usedSdks.isEmpty()) {
      return null;
    }

    return indicator -> {
             try {
               List<UnknownInvalidSdk> invalidSdks = new ArrayList<>();
               Map<UnknownSdk, UnknownSdkLocalSdkFix> localFixes = new HashMap<>();
               Map<UnknownSdk, UnknownSdkDownloadableSdkFix> downloadFixes = new HashMap<>();

               if (!usedSdks.isEmpty()) {
                 indicator.pushState();
                 indicator.setText(ProjectBundle.message("progress.text.resolving.existing.sdks"));
                 invalidSdks = UnknownInvalidSdk.resolveInvalidSdks(usedSdks);
                 fixable.addAll(invalidSdks);
                 indicator.popState();
               }

               if (!fixable.isEmpty()) {
                 indicator.pushState();
                 indicator.setText(ProjectBundle.message("progress.text.resolving.missing.sdks"));
                 List<UnknownSdkLookup> lookups = collectSdkLookups(indicator);

                 if (!lookups.isEmpty()) {
                   indicator.setText(ProjectBundle.message("progress.text.looking.for.local.sdks"));
                   localFixes = findFixesAndRemoveFixable(indicator, fixable, lookups, UnknownSdkLookup::proposeLocalFix);

                   if (!fixable.isEmpty()) {
                     indicator.setText(ProjectBundle.message("progress.text.looking.for.downloadable.sdks"));
                     downloadFixes = findFixesAndRemoveFixable(indicator, fixable, lookups, UnknownSdkLookup::proposeDownload);
                   }
                 }

                 indicator.popState();
               }

               List<UnknownSdkFix> fixProposals = new ArrayList<>();

               fixable.removeAll(invalidSdks);
               for (UnknownInvalidSdk invalidSdk : invalidSdks) {
                 var localSdkFix = localFixes.remove(invalidSdk);
                 var downloadableSdkFix = downloadFixes.remove(invalidSdk);
                 fixProposals.add(invalidSdk.buildFix(myProject, localSdkFix, downloadableSdkFix));
               }

               var missingSdkSets = new HashSet<UnknownSdk>();
               missingSdkSets.addAll(downloadFixes.keySet());
               missingSdkSets.addAll(localFixes.keySet());
               missingSdkSets.addAll(fixable);

               var allMissingSdks = Set.copyOf(missingSdkSets);

               for (UnknownSdk unknownSdk : allMissingSdks) {
                 String name = unknownSdk.getSdkName();
                 if (name == null) continue;

                 var downloadFix = downloadFixes.get(unknownSdk);
                 var localSdkFix = localFixes.get(unknownSdk);

                 fixProposals.add(UnknownMissingSdk.createMissingSdkFix(myProject, unknownSdk, localSdkFix, downloadFix));
               }

               return List.copyOf(fixProposals);
             } catch (Throwable t) {
               if (t instanceof ControlFlowException) ExceptionUtil.rethrow(t);
               LOG.warn("Failed to complete SDKs lookup. " + t.getMessage(), t);
               return List.of();
             }
         };
  }

  private @Nullable Progressive createProcessSdksAction(@NotNull UnknownSdkSnapshot snapshot,
                                                        @NotNull ShowStatusCallback showStatus) {
    // it may run on EDT, for the standard task
    var task = createProcessSdksAction(snapshot);
    if (task == null) {
      return null;
    }

    return indicator -> {
      try {
        var result = task.apply(indicator);
        showStatus.showStatus(result, indicator);
      }
      catch (Throwable t) {
        if (t instanceof ControlFlowException) {
          showStatus.showInterruptedStatus();
          ExceptionUtil.rethrow(t);
        }

        LOG.warn("Failed to complete SDKs lookup. " + t.getMessage(), t);
        showStatus.showEmptyStatus();
      }
    };
  }

  public interface ShowStatusCallback {
    void showStatus(@NotNull List<? extends UnknownSdkFix> fixes, @NotNull ProgressIndicator indicator);

    default void showInterruptedStatus() {
      showStatus(Collections.emptyList(), new EmptyProgressIndicator());
    }

    default void showEmptyStatus() {
      showStatus(Collections.emptyList(), new EmptyProgressIndicator());
    }
  }

  private final class DefaultShowStatusCallbackAdapter implements ShowStatusCallback {
    @Override
    public void showStatus(@NotNull List<? extends UnknownSdkFix> fixes, @NotNull ProgressIndicator indicator) {
      fixes = applyAutoFixesAndNotify(fixes, indicator);
      UnknownSdkEditorNotification.getInstance(myProject).showNotifications(fixes);
    }
  }

  public @NotNull List<UnknownSdkFix> applyAutoFixesAndNotify(@NotNull List<? extends UnknownSdkFix> fixes, @NotNull ProgressIndicator indicator) {
    List<UnknownSdkFix> otherFixes = new ArrayList<>();
    List<UnknownMissingSdkFixLocal> localFixes = new ArrayList<>();

    for (UnknownSdkFix fix : fixes) {
      var action = fix.getSuggestedFixAction();
      if (action instanceof UnknownMissingSdkFixLocal) {
        localFixes.add((UnknownMissingSdkFixLocal)action);
      }
      else {
        otherFixes.add(fix);
      }
    }

    if (!localFixes.isEmpty()) {
      indicator.pushState();
      indicator.setText(ProjectBundle.message("progress.text.configuring.sdks"));

      for (UnknownMissingSdkFixLocal fix : new ArrayList<>(localFixes)) {
        try {
          fix.applySuggestionBlocking(indicator);
        }
        catch (Throwable t) {
          LOG.warn("Failed to apply SDK fix: " + fix + ". " + t.getMessage(), t);
          localFixes.remove(fix);
        }
      }

      UnknownSdkBalloonNotification.getInstance(myProject).notifyFixedSdks(localFixes);
      indicator.popState();
    }
    return otherFixes;
  }

  public boolean isAutoFixAction(@Nullable UnknownSdkFixAction fix) {
    return fix instanceof UnknownMissingSdkFixLocal;
  }

  public @NotNull Sdk applyAutoFixAndNotify(@NotNull UnknownSdkFixAction fix, @NotNull ProgressIndicator indicator) throws IllegalArgumentException {
    if (!isAutoFixAction(fix)) throw new IllegalArgumentException("The argument must pass #isAutoFixAction test");
    assert fix instanceof UnknownMissingSdkFixLocal : "Invalid fix: " + fix;

    indicator.pushState();
    indicator.setText(ProjectBundle.message("progress.text.configuring.sdks"));

    try {
      return fix.applySuggestionBlocking(indicator);
    }
    finally {
      indicator.popState();
      UnknownSdkBalloonNotification.getInstance(myProject).notifyFixedSdks(List.of((UnknownMissingSdkFixLocal)fix));
    }
  }

  private @NotNull List<UnknownSdkLookup> collectSdkLookups(@NotNull ProgressIndicator indicator) {
    List<UnknownSdkLookup> lookups = new ArrayList<>();
    UnknownSdkResolver.EP_NAME.forEachExtensionSafe(ext -> {
      UnknownSdkLookup resolver = ext.createResolver(myProject, indicator);
      if (resolver != null) {
        lookups.add(resolver);
      }
    });
    return lookups;
  }

  private static @NotNull <R> Map<UnknownSdk, R> findFixesAndRemoveFixable(@NotNull ProgressIndicator indicator,
                                                                           @NotNull List<UnknownSdk> infos,
                                                                           @NotNull List<? extends UnknownSdkLookup> lookups,
                                                                           @NotNull TripleFunction<? super UnknownSdkLookup, ? super UnknownSdk, ? super ProgressIndicator, ? extends R> fun) {
    indicator.pushState();

    Map<UnknownSdk, R> result = new LinkedHashMap<>();
    for (Iterator<UnknownSdk> iterator = infos.iterator(); iterator.hasNext(); ) {
      UnknownSdk info = iterator.next();
      for (UnknownSdkLookup lookup : lookups) {

        indicator.pushState();
        R fix = fun.fun(lookup, info, indicator);
        indicator.popState();

        if (fix != null) {
          result.put(info, fix);
          iterator.remove();
          break;
        }
      }
    }

    indicator.popState();
    return result;
  }
}
