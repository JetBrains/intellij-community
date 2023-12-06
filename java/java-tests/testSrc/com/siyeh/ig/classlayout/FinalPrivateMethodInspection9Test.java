// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class FinalPrivateMethodInspection9Test extends FinalPrivateMethodInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + '9';
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}
