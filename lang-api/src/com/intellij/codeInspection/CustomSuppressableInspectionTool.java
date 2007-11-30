/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 21, 2006
 * Time: 5:42:54 PM
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class CustomSuppressableInspectionTool extends LocalInspectionTool {

  @Nullable
  @Deprecated
  public IntentionAction[] getSuppressActions(ProblemDescriptor context){
    return null;
  }

  @Nullable
  public IntentionAction[] getSuppressActions(PsiElement element) {
    final InspectionManager inspectionManager = InspectionManager.getInstance(element.getProject());
    return getSuppressActions(inspectionManager.createProblemDescriptor(element, "", new LocalQuickFix[0], ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
  }

  public abstract boolean isSuppressedFor(PsiElement element);
}