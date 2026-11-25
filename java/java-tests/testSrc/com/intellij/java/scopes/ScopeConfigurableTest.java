// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.scopes;

import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.ide.util.scopeChooser.ScopeConfigurable;
import com.intellij.ide.util.scopeChooser.ScopeEditorPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScopeConfigurableTest extends LightJavaCodeInsightTestCase {
  public void testModified() throws ConfigurationException {
    DependencyValidationManager manager = DependencyValidationManager.getInstance(getProject());
    final ScopeChooserConfigurable.ScopeChooserConfigurableState state = new ScopeChooserConfigurable.ScopeChooserConfigurableState();
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet( "java.util.*", PatternPackageSet.Scope.LIBRARY, null));
    manager.addScope(utilScope);
    state.myOrder.add(utilScope.getScopeId());
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("*..*", PatternPackageSet.Scope.ANY, null));
    manager.addScope(xScope);
    state.myOrder.add(xScope.getScopeId());
    try {
      ScopeChooserConfigurable configurable = new ScopeChooserConfigurable(getProject());
      MasterDetailsStateService.getInstance(getProject()).setComponentState(ScopeChooserConfigurable.SCOPE_CHOOSER_CONFIGURABLE_UI_KEY, state);
      configurable.reset();
      assertFalse(configurable.isModified());
      configurable.apply();
      assertFalse(configurable.isModified());
      configurable.disposeUIResources();
    }
    finally {
      manager.removeAllSets();
    }
  }

  public void testScopeConfigurablesModified() throws ConfigurationException {
    DependencyValidationManager manager = DependencyValidationManager.getInstance(getProject());
    NamedScope utilscope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.Scope.LIBRARY, null));
    manager.addScope(utilscope);
    try {
      for (NamedScope scope : manager.getScopes()) {
        LOG.debug("scope = " + scope);
        ScopeConfigurable configurable = new ScopeConfigurable(scope, true, getProject(), null);
        configurable.reset();
        assertFalse("Configurable " + configurable + " is modified immediately after creation", configurable.isModified());
        assertTrue(!configurable.isModified());
        configurable.apply();
        configurable.disposeUIResources();
      }
    }
    finally {
      manager.removeAllSets();
    }
  }

  public void testCancel() throws Exception {
    ScopeEditorPanel panel = new ScopeEditorPanel(getProject(), DependencyValidationManager.getInstance(getProject()));
    CompletableFuture<ProgressIndicator> resetStarted = new CompletableFuture<>();
    Semaphore releaseReset = new Semaphore(1);
    AtomicBoolean resetCompleted = new AtomicBoolean();
    try {
      // Keep the pattern field empty so the panel keeps this package set.
      panel.reset(new PatternPackageSet("", PatternPackageSet.Scope.ANY, null) {
        @Override
        public boolean contains(@NotNull VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
          if (!resetStarted.isDone()) {
            resetStarted.complete(ProgressManager.getInstance().getProgressIndicator());
            assertTrue("The rebuild request did not release the reset request", releaseReset.waitFor(10_000));
            ProgressManager.checkCanceled();
          }
          return super.contains(file, project, holder);
        }
      }, () -> resetCompleted.set(true));

      ProgressIndicator resetProgress = resetStarted.get(10, TimeUnit.SECONDS);
      assertNotNull("The reset request must run with a progress indicator", resetProgress);
      panel.rebuild(false);
      assertTrue("The rebuild request did not cancel the reset request", resetProgress.isCanceled());
      releaseReset.up();
      panel.waitForCompletion();
      assertFalse("The canceled reset request ran its callback", resetCompleted.get());
    }
    finally {
      releaseReset.up();
      panel.cancelCurrentProgress();
      Disposer.dispose(panel);
    }
  }
}
