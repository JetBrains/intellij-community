// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.cache;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.FileCodeStyleProvider;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class CodeStyleCachedValueProvider implements CachedValueProvider<CodeStyleSettings> {
  private final static Logger LOG = Logger.getInstance(CodeStyleCachedValueProvider.class);

  private final static int MAX_COMPUTATION_THREADS = 10;

  private final @NotNull FileViewProvider myViewProvider;
  private final @NotNull AsyncComputation myComputation;
  private final @NotNull Project myProject;
  private final @NotNull Lock myComputationLock = new ReentrantLock() {
    @Override
    public boolean equals(Object obj) {
      return obj instanceof ReentrantLock;
    }
  };

  private final static ExecutorService ourExecutorService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("CodeStyleCachedValueProvider", MAX_COMPUTATION_THREADS);

  CodeStyleCachedValueProvider(@NotNull FileViewProvider viewProvider, @NotNull Project project) {
    myViewProvider = viewProvider;
    myComputation = new AsyncComputation(project);
    myProject = project;
  }

  boolean isExpired() {
    return myComputation.isExpired();
  }

  @Nullable
  CodeStyleSettings tryGetSettings() {
    if (myComputationLock.tryLock()) {
      try {
        return CachedValuesManager.getManager(myProject).getCachedValue(myViewProvider, this);
      }
      finally {
        myComputationLock.unlock();
      }
    }
    return null;
  }

  void scheduleWhenComputed(@NotNull Runnable runnable) {
    myComputation.schedule(runnable);
  }

  @Nullable
  @Override
  public Result<CodeStyleSettings> compute() {
    CodeStyleSettings settings = myComputation.getCurrResult();
    if (settings != null) {
      logCached(settings);
      return new Result<>(settings, getDependencies(settings, myComputation));
    }
    return null;
  }

  public void cancelComputation() {
    myComputation.cancel();
  }

  Object @NotNull [] getDependencies(@NotNull CodeStyleSettings settings, @NotNull AsyncComputation computation) {
    List<Object> dependencies = new ArrayList<>();
    if (settings instanceof TransientCodeStyleSettings) {
      dependencies.addAll(((TransientCodeStyleSettings)settings).getDependencies());
    }
    else {
      dependencies.add(settings.getModificationTracker());
    }
    dependencies.add(computation.getTracker());
    return ArrayUtil.toObjectArray(dependencies);
  }

  private void logCached(@NotNull CodeStyleSettings settings) {
    LOG.debug(String.format(
      "File: %s (%s), cached: %s, tracker: %d", myViewProvider.getVirtualFile().getName(), Integer.toHexString(myViewProvider.hashCode()),
      settings, settings.getModificationTracker().getModificationCount()));
  }

  /**
   * Always contains some result which can be obtained by {@code getCurrResult()} method. Listeners are notified after
   * the computation is finished and {@code getCurrResult()} contains a stable computed value.
   */
  private final class AsyncComputation {
    private final             AtomicBoolean             myIsActive = new AtomicBoolean();
    private volatile          CodeStyleSettings         myCurrResult;
    private final @NotNull    CodeStyleSettingsManager  mySettingsManager;
    private final             SimpleModificationTracker myTracker  = new SimpleModificationTracker();
    private final             Project                   myProject;
    private                   CancellablePromise<Void>  myPromise;
    private final             List<Runnable>            myScheduledRunnables = new ArrayList<>();
    private                   long                      myOldTrackerSetting;
    private                   boolean                   myInsideRestartedComputation = false;

    private AsyncComputation(@NotNull Project project) {
      myProject = project;
      mySettingsManager = CodeStyleSettingsManager.getInstance(myProject);
      //noinspection deprecation
      myCurrResult = mySettingsManager.getCurrentSettings();
    }

    private void start() {
      if (isRunOnBackground()) {
        myPromise = ReadAction.nonBlocking(() -> computeSettings())
                              .expireWith(myProject)
                              .finishOnUiThread(ModalityState.any(), val -> notifyCachedValueComputed())
                              .submit(ourExecutorService);
      }
      else {
        ReadAction.run((() -> computeSettings()));
        notifyOnEdt();
      }
    }

    public void cancel() {
      if (myPromise != null && !myPromise.isDone()) {
        myPromise.cancel();
      }
      myCurrResult = null;
    }

    public boolean isExpired() {
      return myCurrResult == null;
    }

    private void schedule(@NotNull Runnable runnable) {
      if (myIsActive.get()) {
        myScheduledRunnables.add(runnable);
      }
      else {
        runnable.run();
      }
    }

    private static boolean isRunOnBackground() {
      final Application application = ApplicationManager.getApplication();
      return !application.isUnitTestMode() && !application.isHeadlessEnvironment() && application.isDispatchThread();
    }

    private void notifyOnEdt() {
      final Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        notifyCachedValueComputed();
      }
      else {
        application.invokeLater(() -> notifyCachedValueComputed(), ModalityState.any());
      }
    }

    private void computeSettings() {
      VirtualFile file = myViewProvider.getVirtualFile();
      PsiFile psiFile = getPsiFile();
      if (psiFile == null) {
        cancel();
        return;
      }
      try {
        myComputationLock.lock();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Computation started for " + file.getName());
        }
        CodeStyleSettings currSettings = getCurrentSettings(psiFile);
        myOldTrackerSetting = currSettings.getModificationTracker().getModificationCount();
        //noinspection TestOnlyProblems
        if (currSettings != mySettingsManager.getTemporarySettings()) {
          TransientCodeStyleSettings modifiableSettings = new TransientCodeStyleSettings(myViewProvider, currSettings);
          modifiableSettings.applyIndentOptionsFromProviders(myProject, file);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Created TransientCodeStyleSettings for " + file.getName());
          }

          for (CodeStyleSettingsModifier modifier : CodeStyleSettingsModifier.EP_NAME.getExtensionList()) {
            if (modifier.modifySettings(modifiableSettings, psiFile)) {
              LOG.debug("Modifier: " + modifier.getClass().getName());
              modifiableSettings.setModifier(modifier);
              currSettings = modifiableSettings;
              break;
            }
          }
        }
        if (myCurrResult != currSettings) {
          myCurrResult = currSettings;
          myTracker.incModificationCount();
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Computation ended for " + file.getName());
        }
      }
      finally {
        myComputationLock.unlock();
      }
    }

    @SuppressWarnings("deprecation")
    private CodeStyleSettings getCurrentSettings(@NotNull PsiFile file) {
      CodeStyleSettings result = FileCodeStyleProvider.EP_NAME.computeSafeIfAny(provider -> provider.getSettings(file));
      if (result != null) {
        return result;
      }
      return mySettingsManager.getCurrentSettings();
    }

    @Nullable
    public CodeStyleSettings getCurrResult() {
      if (myIsActive.compareAndSet(false, true)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Computation initiated for " + myViewProvider.getVirtualFile().getName());
        }
        start();
      }
      return myCurrResult;
    }

    private SimpleModificationTracker getTracker() {
      return myTracker;
    }

    void reset() {
      myScheduledRunnables.clear();
      myIsActive.set(false);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Computation reset for " + myViewProvider.getVirtualFile().getName());
      }
    }

    private void notifyCachedValueComputed() {
      @SuppressWarnings("deprecation")
      CodeStyleSettings currSettings = mySettingsManager.getCurrentSettings();
      long newTrackerSetting = currSettings.getModificationTracker().getModificationCount();
      if (myOldTrackerSetting < newTrackerSetting && !myInsideRestartedComputation) {
        myInsideRestartedComputation = true;
        try {
          start();
        } finally {
          myInsideRestartedComputation = false;
        }

        return;
      }

      for (Runnable runnable : myScheduledRunnables) {
        runnable.run();
      }
      if (!myProject.isDisposed()) {
        CodeStyleSettingsManager.getInstance(myProject).fireCodeStyleSettingsChanged(myViewProvider.getVirtualFile());
      }
      myComputation.reset();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AsyncComputation that = (AsyncComputation)o;
      return myProject.equals(that.myProject);
    }
  }

  private @Nullable PsiFile getPsiFile() {
    return myViewProvider.getPsi(myViewProvider.getBaseLanguage());
  }

  //
  // Check provider equivalence by file ref. Other fields make no sense since AsyncComputation is a stateful object
  // whose state (active=true->false) changes over time due to long computation.
  //
  @Override
  public boolean equals(Object obj) {
    return obj instanceof CodeStyleCachedValueProvider &&
           Objects.equals(this.myViewProvider, ((CodeStyleCachedValueProvider)obj).myViewProvider);
  }


}
