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

import com.intellij.codeInsight.daemon.impl.Divider;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
    if (tools.isEmpty()) return Collections.emptyList();
    final List<ProblemDescriptor> resultDescriptors = Collections.synchronizedList(new ArrayList<ProblemDescriptor>());
    final List<PsiElement> elements = new ArrayList<PsiElement>();

    TextRange range = file.getTextRange();
    final LocalInspectionToolSession session = new LocalInspectionToolSession(file, range.getStartOffset(), range.getEndOffset());
    Divider.divideInsideAndOutside(file, range.getStartOffset(), range.getEndOffset(), range, elements, Collections.<PsiElement>emptyList(), true, Condition.TRUE);

    boolean result = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tools, indicator, failFastOnAcquireReadAction, new Processor<LocalInspectionTool>() {
      @Override
      public boolean process(LocalInspectionTool tool) {
        ProblemsHolder holder = new ProblemsHolder(iManager, file, isOnTheFly);
        createVisitorAndAcceptElements(tool, holder, isOnTheFly, session, elements, null);

        tool.inspectionFinished(session, holder);

        if (holder.hasResults()) {
          resultDescriptors.addAll(holder.getResults());
        }

        return true;
      }
    });

    return resultDescriptors;
  }
}
