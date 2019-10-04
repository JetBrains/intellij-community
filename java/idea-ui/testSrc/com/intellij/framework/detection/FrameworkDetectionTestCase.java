// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.detection;

import com.intellij.framework.detection.impl.FrameworkDetectionManager;
import com.intellij.framework.detection.impl.FrameworkDetectionUtil;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.testFramework.HeavyPlatformTestCase;

import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkDetectionTestCase extends HeavyPlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
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
