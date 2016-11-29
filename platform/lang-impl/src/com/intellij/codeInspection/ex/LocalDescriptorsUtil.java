/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.TripleFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalDescriptorsUtil {
  private static final TripleFunction<LocalInspectionTool, PsiElement, GlobalInspectionContext,RefElement> CONVERT =
    (tool, element, context) -> {
      final PsiNamedElement problemElement = tool.getProblemElement(element);

      RefElement refElement = context.getRefManager().getReference(problemElement);
      if (refElement == null && problemElement != null) {  // no need to lose collected results
        refElement = GlobalInspectionContextUtil.retrieveRefElement(element, context);
      }
      return refElement;
    };

  static void addProblemDescriptors(@NotNull List<ProblemDescriptor> descriptors,
                                    boolean filterSuppressed,
                                    @NotNull GlobalInspectionContext context,
                                    @Nullable LocalInspectionTool tool,
                                    @NotNull TripleFunction<LocalInspectionTool, PsiElement, GlobalInspectionContext, RefElement> getProblemElementFunction,
                                    @NotNull InspectionToolPresentation dpi) {
    if (descriptors.isEmpty()) return;

    Map<RefElement, List<ProblemDescriptor>> problems = new HashMap<>();
    final RefManagerImpl refManager = (RefManagerImpl)context.getRefManager();
    for (ProblemDescriptor descriptor : descriptors) {
      final PsiElement element = descriptor.getPsiElement();
      if (element == null) continue;
      if (filterSuppressed) {
        String alternativeId;
        String id;
        if (refManager.isDeclarationsFound() &&
            (context.isSuppressed(element, id = tool.getID()) ||
             (alternativeId = tool.getAlternativeID()) != null &&
             !alternativeId.equals(id) &&
             context.isSuppressed(element, alternativeId))) {
          continue;
        }
        if (SuppressionUtil.inspectionResultSuppressed(element, tool)) continue;
      }


      RefElement refElement = getProblemElementFunction.fun(tool, element, context);

      List<ProblemDescriptor> elementProblems = problems.get(refElement);
      if (elementProblems == null) {
        elementProblems = new ArrayList<>();
        problems.put(refElement, elementProblems);
      }
      elementProblems.add(descriptor);
    }

    for (Map.Entry<RefElement, List<ProblemDescriptor>> entry : problems.entrySet()) {
      final List<ProblemDescriptor> problemDescriptors = entry.getValue();
      RefElement refElement = entry.getKey();
      CommonProblemDescriptor[] descriptions = problemDescriptors.toArray(new CommonProblemDescriptor[problemDescriptors.size()]);
      dpi.addProblemElement(refElement, filterSuppressed, descriptions);
    }
  }

  public static void addProblemDescriptors(@NotNull List<ProblemDescriptor> descriptors,
                                           @NotNull InspectionToolPresentation dpi,
                                           boolean filterSuppressed,
                                           @NotNull GlobalInspectionContext inspectionContext,
                                           @NotNull LocalInspectionTool tool) {
    addProblemDescriptors(descriptors, filterSuppressed, inspectionContext, tool, CONVERT, dpi);
  }
}
