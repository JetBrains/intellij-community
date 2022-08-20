// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class MalformedSetUpTearDownInspectionMerger extends InspectionElementsMerger {

  @NotNull
  @Override
  public String getMergedToolName() {
    return "MalformedSetUpTearDown";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] { "TeardownIsPublicVoidNoArg", "SetupIsPublicVoidNoArg" };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] { "TearDownWithIncorrectSignature", "SetUpWithIncorrectSignature" };
  }
}
