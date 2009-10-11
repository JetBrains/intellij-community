/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectViewDescriptor implements UsageViewDescriptor {
  private final PsiMethod myMethod;

  public ExtractMethodObjectViewDescriptor(final PsiMethod method) {
    myMethod = method;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{myMethod};
  }

  public String getProcessedElementsHeader() {
    return "Method to be converted";
  }

  public String getCodeReferencesText(final int usagesCount, final int filesCount) {
    return "References to be changed";
  }

  public String getCommentReferencesText(final int usagesCount, final int filesCount) {
    return null;
  }
}