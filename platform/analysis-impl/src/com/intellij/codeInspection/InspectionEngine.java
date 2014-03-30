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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.Divider;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InspectionEngine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionEngine");

  @NotNull
  public static PsiElementVisitor createVisitorAndAcceptElements(@NotNull LocalInspectionTool tool,
                                                                 @NotNull ProblemsHolder holder,
                                                                 boolean isOnTheFly,
                                                                 @NotNull LocalInspectionToolSession session,
                                                                 @NotNull List<PsiElement> elements,
                                                                 @Nullable Collection<String> languages) {
    PsiElementVisitor visitor = tool.buildVisitor(holder, isOnTheFly, session);
    //noinspection ConstantConditions
    if(visitor == null) {
      LOG.error("Tool " + tool + " must not return null from the buildVisitor() method");
    }
    assert !(visitor instanceof PsiRecursiveElementVisitor || visitor instanceof PsiRecursiveElementWalkingVisitor)
      : "The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive. "+tool;

    tool.inspectionStarted(session, isOnTheFly);
    acceptElements(elements, visitor, languages);
    return visitor;
  }

  public static void acceptElements(@NotNull List<PsiElement> elements,
                                    @NotNull PsiElementVisitor elementVisitor,
                                    @Nullable Collection<String> languages) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, elementsSize = elements.size(); i < elementsSize; i++) {
      PsiElement element = elements.get(i);
      if (languages == null || languages.contains(element.getLanguage().getID())) {
        element.accept(elementVisitor);
      }
      ProgressManager.checkCanceled();
    }
  }

  @NotNull
  public static List<ProblemDescriptor> inspect(@NotNull final List<LocalInspectionTool> tools,
                                                @NotNull final PsiFile file,
                                                @NotNull final InspectionManager iManager,
                                                final boolean isOnTheFly,
                                                boolean failFastOnAcquireReadAction,
                                                @NotNull final ProgressIndicator indicator) {
    final Map<String, List<ProblemDescriptor>> problemDescriptors = inspectEx(tools, file, iManager, isOnTheFly, failFastOnAcquireReadAction, indicator);

    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (List<ProblemDescriptor> group : problemDescriptors.values())
      result.addAll(group);
    return result;
  }

  // public accessibility for Upsource
  @NotNull
  public static Map<String, List<ProblemDescriptor>> inspectEx(@NotNull final List<LocalInspectionTool> tools,
                                                               @NotNull final PsiFile file,
                                                               @NotNull final InspectionManager iManager,
                                                               final boolean isOnTheFly,
                                                               boolean failFastOnAcquireReadAction,
                                                               @NotNull final ProgressIndicator indicator) {
    if (tools.isEmpty()) return Collections.emptyMap();
    final Map<String, List<ProblemDescriptor>> resultDescriptors = new ConcurrentHashMap<String, List<ProblemDescriptor>>();
    final List<PsiElement> elements = new ArrayList<PsiElement>();

    TextRange range = file.getTextRange();
    final LocalInspectionToolSession session = new LocalInspectionToolSession(file, range.getStartOffset(), range.getEndOffset());
    Divider.divideInsideAndOutside(file, range.getStartOffset(), range.getEndOffset(), range, elements, new ArrayList<ProperTextRange>(),
                                   Collections.<PsiElement>emptyList(), Collections.<ProperTextRange>emptyList(), true, Condition.TRUE);

    boolean result = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
      tools, indicator, failFastOnAcquireReadAction,
      new Processor<LocalInspectionTool>() {
        @Override
        public boolean process(final LocalInspectionTool tool) {
          ProblemsHolder holder = new ProblemsHolder(iManager, file, isOnTheFly);
          createVisitorAndAcceptElements(tool, holder, isOnTheFly, session, elements, null);

          tool.inspectionFinished(session, holder);

          if (holder.hasResults()) {
            resultDescriptors.put(tool.getShortName(), ContainerUtil.filter(holder.getResults(), new Condition<ProblemDescriptor>() {
              @Override
              public boolean value(ProblemDescriptor descriptor) {
                PsiElement element = descriptor.getPsiElement();
                if (element != null) {
                  return !SuppressionUtil.inspectionResultSuppressed(element, tool);
                }
                return true;
              }
            }));
          }

          return true;
        }
      });

    return resultDescriptors;
  }

  @NotNull
  public static List<ProblemDescriptor> runInspectionOnFile(@NotNull final PsiFile file,
                                                            @NotNull InspectionToolWrapper toolWrapper,
                                                            @NotNull final GlobalInspectionContext inspectionContext) {
    final InspectionManager inspectionManager = InspectionManager.getInstance(file.getProject());
    toolWrapper.initialize(inspectionContext);
    RefManagerImpl refManager = (RefManagerImpl)inspectionContext.getRefManager();
    refManager.inspectionReadActionStarted();
    try {
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        LocalInspectionTool localTool = ((LocalInspectionToolWrapper)toolWrapper).getTool();
        return inspect(Collections.singletonList(localTool), file, inspectionManager, false, false, new EmptyProgressIndicator());
      }
      if (toolWrapper instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionTool globalTool = ((GlobalInspectionToolWrapper)toolWrapper).getTool();
        final List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
        if (globalTool instanceof GlobalSimpleInspectionTool) {
          GlobalSimpleInspectionTool simpleTool = (GlobalSimpleInspectionTool)globalTool;
          ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, file, false);
          ProblemDescriptionsProcessor collectProcessor = new ProblemDescriptionsProcessor() {
            @Nullable
            @Override
            public CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
              return descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
            }

            @Override
            public void ignoreElement(@NotNull RefEntity refEntity) {
              throw new RuntimeException();
            }

            @Override
            public void addProblemElement(@Nullable RefEntity refEntity, @NotNull CommonProblemDescriptor... commonProblemDescriptors) {
              if (!(refEntity instanceof RefElement)) return;
              PsiElement element = ((RefElement)refEntity).getElement();
              convertToProblemDescriptors(element, commonProblemDescriptors, descriptors);
            }

            @Override
            public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
              throw new RuntimeException();
            }
          };
          simpleTool.checkFile(file, inspectionManager, problemsHolder, inspectionContext, collectProcessor);
          return descriptors;
        }
        RefElement fileRef = refManager.getReference(file);
        final AnalysisScope scope = new AnalysisScope(file);
        fileRef.accept(new RefVisitor(){
          @Override
          public void visitElement(@NotNull RefEntity elem) {
            CommonProblemDescriptor[] elemDescriptors = globalTool.checkElement(elem, scope, inspectionManager, inspectionContext);
            if (descriptors != null) {
              convertToProblemDescriptors(file, elemDescriptors, descriptors);
            }

            for (RefEntity child : elem.getChildren()) {
              child.accept(this);
            }
          }
        });
        return descriptors;
      }
    }
    finally {
      refManager.inspectionReadActionFinished();
      toolWrapper.cleanup(file.getProject());
      inspectionContext.cleanup();
    }
    return Collections.emptyList();
  }

  private static void convertToProblemDescriptors(PsiElement element,
                                                  CommonProblemDescriptor[] commonProblemDescriptors,
                                                  List<ProblemDescriptor> descriptors) {
    for (CommonProblemDescriptor common : commonProblemDescriptors) {
      if (common instanceof ProblemDescriptor) {
        descriptors.add((ProblemDescriptor)common);
      }
      else {
        ProblemDescriptorBase base =
          new ProblemDescriptorBase(element, element, common.getDescriptionTemplate(), (LocalQuickFix[])common.getFixes(),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false, false);
        descriptors.add(base);
      }
    }
  }
}
