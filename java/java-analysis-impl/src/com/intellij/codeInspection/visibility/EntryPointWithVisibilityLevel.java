/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.visibility;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * Register entry points which visibility can be decreased,
 * e.g. package private test methods in junit 5
 */
public abstract class EntryPointWithVisibilityLevel extends EntryPoint {
  protected static final int ACCESS_LEVEL_INVALID = -1;
  @MagicConstant(intValues = {PsiUtil.ACCESS_LEVEL_PUBLIC, PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PRIVATE, ACCESS_LEVEL_INVALID})
  @interface VisibilityLevelResult {}

  /**
   * @return minimum accepted modifier (see {@link PsiUtil.AccessLevel}) or -1 when not applicable
   */
  @VisibilityLevelResult
  public abstract int getMinVisibilityLevel(PsiMember member);

  /**
   * Title for checkbox in visibility inspection settings
   */
  public abstract String getTitle();

  /**
   * Id to serialize checkbox state in visibility inspection settings
   */
  public abstract String getId();

  /**
   * Don't suggest decreasing visibility for the element, sometimes even if the entry point is disabled.
   */
  public boolean keepVisibilityLevel(boolean entryPointEnabled, @NotNull RefJavaElement refJavaElement) {
    return false;
  }
}
