// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scopes;

import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.ide.util.scopeChooser.ScopeConfigurable;
import com.intellij.ide.util.scopeChooser.ScopeEditorPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.util.Disposer;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.junit.Assert;

import java.util.concurrent.atomic.AtomicBoolean;

public class ScopeConfigurableTest extends LightJavaCodeInsightTestCase {
  public void testModified() throws ConfigurationException {
    DependencyValidationManager manager = DependencyValidationManager.getInstance(getProject());
    final ScopeChooserConfigurable.ScopeChooserConfigurableState state = new ScopeChooserConfigurable.ScopeChooserConfigurableState();
    NamedScope utilScope = new NamedScope("util", new PatternPackageSet( "java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
    manager.addScope(utilScope);
    state.myOrder.add(utilScope.getScopeId());
    NamedScope xScope = new NamedScope("xxx", new PatternPackageSet("*..*", PatternPackageSet.SCOPE_ANY, null));
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
    NamedScope utilscope = new NamedScope("util", new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null));
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

  public void testCancel() throws InterruptedException {
    ScopeEditorPanel panel = new ScopeEditorPanel(getProject(), DependencyValidationManager.getInstance(getProject()));
    try {
      AtomicBoolean wasNotCanceled = new AtomicBoolean();
      panel.reset(new PatternPackageSet("java.util.*", PatternPackageSet.SCOPE_LIBRARY, null), () -> wasNotCanceled.set(true));
      Thread.sleep(2); //ensure that Future was not canceled non started
      panel.rebuild(false);
      //Wait for the 'rebuild' task to complete. 
      //If 'reset' was not canceled by rebuild, 'rebuild' would wait on sync TreeModelBuilder.createTreeModel
      panel.waitForCompletion();   
      if (wasNotCanceled.get()) {
        Assert.fail("Model was not canceled by the consequent request");
      }
    }
    finally {
      panel.cancelCurrentProgress();
      Disposer.dispose(panel);
    }
  }
}