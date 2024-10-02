// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.TripleFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public final class BatchModeDescriptorsUtil {
  private static final TripleFunction<LocalInspectionTool, PsiElement, GlobalInspectionContext,RefElement> CONVERT =
    (tool, element, context) -> {
      PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(context.getProject()).getInjectionHost(element);
      if (injectionHost != null) {
        element = injectionHost;
      }

      PsiNamedElement problemElement = getContainerElement(element, tool, context);

      RefElement refElement = context.getRefManager().getReference(problemElement);
      if (refElement == null && problemElement != null) {  // no need to lose collected results
        refElement = GlobalInspectionContextUtil.retrieveRefElement(element, context);
      }
      return refElement;
    };

  static void addProblemDescriptors(@NotNull Collection<? extends ProblemDescriptor> descriptors,
                                    boolean filterSuppressed,
                                    @NotNull GlobalInspectionContext context,
                                    @Nullable LocalInspectionTool tool,
                                    @NotNull InspectionToolResultExporter dpi,
                                    @NotNull TripleFunction<? super LocalInspectionTool, ? super PsiElement, ? super GlobalInspectionContext, ? extends RefElement> getProblemElementFunction) {
    if (descriptors.isEmpty()) return;

    Map<RefElement, List<ProblemDescriptor>> problems = new HashMap<>();
    RefManagerImpl refManager = (RefManagerImpl)context.getRefManager();
    for (ProblemDescriptor descriptor : descriptors) {
      PsiElement element = descriptor.getPsiElement();
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

      List<ProblemDescriptor> elementProblems = problems.computeIfAbsent(refElement, __ -> new ArrayList<>());
      elementProblems.add(descriptor);
    }

    for (Map.Entry<RefElement, List<ProblemDescriptor>> entry : problems.entrySet()) {
      List<ProblemDescriptor> problemDescriptors = entry.getValue();
      RefElement refElement = entry.getKey();
      CommonProblemDescriptor[] descriptions = problemDescriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
      dpi.addProblemElement(refElement, filterSuppressed, descriptions);
    }
  }

  public static void addProblemDescriptors(@NotNull Collection<? extends ProblemDescriptor> descriptors,
                                           @NotNull InspectionToolResultExporter dpi,
                                           boolean filterSuppressed,
                                           @NotNull GlobalInspectionContext inspectionContext,
                                           @NotNull LocalInspectionTool tool) {
    addProblemDescriptors(descriptors, filterSuppressed, inspectionContext, tool, dpi, CONVERT);
  }

  public static PsiNamedElement getContainerElement(@Nullable PsiElement element,
                                                    @NotNull LocalInspectionTool tool,
                                                    @NotNull GlobalInspectionContext context) {
    if (element == null) return null;
    PsiNamedElement containerFromTool = tool.getProblemElement(element);
    if (containerFromTool != null && !(containerFromTool instanceof PsiFile)) {
      return containerFromTool;
    }
    PsiNamedElement container = context.getRefManager().getContainerElement(element);
    return container != null ? container : containerFromTool;
  }

  public static CommonProblemDescriptor @NotNull [] flattenDescriptors(@NotNull List<CommonProblemDescriptor[]> descriptors) {
    return descriptors.stream().flatMap(ds -> Arrays.stream(ds)).toArray(CommonProblemDescriptor.ARRAY_FACTORY::create);
  }
}
