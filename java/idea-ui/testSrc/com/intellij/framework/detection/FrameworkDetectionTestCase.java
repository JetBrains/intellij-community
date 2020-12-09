// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.detection;

import com.intellij.facet.FacetTestCase;
import com.intellij.facet.mock.MockFacetDetector;
import com.intellij.facet.mock.MockSubFacetDetector;
import com.intellij.framework.detection.impl.FrameworkDetectionManager;
import com.intellij.framework.detection.impl.FrameworkDetectionUtil;
import com.intellij.ide.plugins.DynamicPluginsTestUtil;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.util.Disposer;

import java.util.List;

public abstract class FrameworkDetectionTestCase extends FacetTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (!"dynamicDetector".equals(getTestName(true))) {
      //todo we can get rid of this ugly check by converting facet tests to JUnit4 and using test rules to enable facet detection
      Disposer.register(getTestRootDisposable(), DynamicPluginsTestUtil.loadExtensionWithText(
        "<framework.detector implementation=\"" + MockFacetDetector.class.getName() + "\"/>",
        MockFacetDetector.class.getClassLoader()));

      Disposer.register(getTestRootDisposable(), DynamicPluginsTestUtil.loadExtensionWithText(
        "<framework.detector implementation=\"" + MockSubFacetDetector.class.getName() + "\"/>",
        MockSubFacetDetector.class.getClassLoader()));
    }
    FrameworkDetectionManager.getInstance(myProject).doInitialize();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FrameworkDetectionManager.getInstance(myProject).doDispose();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void setupFrameworks(List<? extends DetectedFrameworkDescription> descriptions) {
    FrameworkDetectionUtil.setupFrameworks(descriptions, new PlatformModifiableModelsProvider(), new DefaultModulesProvider(myProject));
  }
}
