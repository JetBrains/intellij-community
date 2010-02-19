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

package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * User: Maxim.Mossienko
 * Date: 16.09.2009
 * Time: 20:35:06
 */
public class GlobalInspectionUtil {
  private static final String LOC_MARKER = " #loc";

  static RefElement retrieveRefElement(PsiElement element, GlobalInspectionContext globalContext) {
    PsiFile elementFile = element.getContainingFile();
    RefElement refElement = globalContext.getRefManager().getReference(elementFile);
    if (refElement == null) {
      PsiElement context = elementFile.getContext();
      if (context != null) refElement = globalContext.getRefManager().getReference(context.getContainingFile());
    }
    return refElement;
  }

  public static String createInspectionMessage(String message) {
    return message + LOC_MARKER;
  }

  public static void createProblem(PsiElement elt, String message, ProblemHighlightType problemHighlightType, TextRange range,
                                    InspectionManager manager, ProblemDescriptionsProcessor problemDescriptionsProcessor,
                                    GlobalInspectionContext globalContext) {
    ProblemDescriptor descriptor = manager.createProblemDescriptor(
        elt,
        range,
        createInspectionMessage(message),
        problemHighlightType, false);
    problemDescriptionsProcessor.addProblemElement(
      retrieveRefElement(elt, globalContext),
      descriptor
    );
  }

  public static void createProblem(PsiElement elt, String message, ProblemHighlightType problemHighlightType, TextRange range,
                                    InspectionManager manager, ProblemDescriptionsProcessor problemDescriptionsProcessor,
                                    GlobalInspectionContext globalContext, LocalQuickFix fix) {
    ProblemDescriptor descriptor = manager.createProblemDescriptor(
        elt,
        range,
        createInspectionMessage(message),
        problemHighlightType, false, fix);
    problemDescriptionsProcessor.addProblemElement(
      retrieveRefElement(elt, globalContext),
      descriptor
    );
  }
}
