package com.intellij.historyIntegrTests.ui;

import com.intellij.historyIntegrTests.IntegrationTestCase;

import java.awt.*;

public abstract class LocalHistoryUITestCase extends IntegrationTestCase {
  @Override
  protected void runTest() throws Throwable {
    if (GraphicsEnvironment.isHeadless()) {
      System.out.println("Test '" + getClass().getName() + "." + getName() + "' is skipped because it requires working UI environment");
      return;
    }
    super.runTest();
  }
}
