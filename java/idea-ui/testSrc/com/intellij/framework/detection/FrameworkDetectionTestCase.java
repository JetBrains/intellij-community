package com.intellij.framework.detection;

import com.intellij.framework.detection.impl.FrameworkDetectionManager;
import com.intellij.framework.detection.impl.FrameworkDetectionUtil;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.testFramework.PlatformTestCase;

import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkDetectionTestCase extends PlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    FrameworkDetectionManager.getInstance(myProject).doInitialize();
  }

  @Override
  protected void tearDown() throws Exception {
    FrameworkDetectionManager.getInstance(myProject).doDispose();
    super.tearDown();
  }

  protected void setupFrameworks(List<? extends DetectedFrameworkDescription> descriptions) {
    FrameworkDetectionUtil.setupFrameworks(descriptions, new PlatformModifiableModelsProvider(), new DefaultModulesProvider(myProject));
  }
}
