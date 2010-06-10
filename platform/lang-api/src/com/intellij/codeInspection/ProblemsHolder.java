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

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternallyDefinedPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class ProblemsHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ProblemsHolder");
  private final InspectionManager myManager;
  private final PsiFile myFile;
  private final boolean myOnTheFly;
  private List<ProblemDescriptor> myProblems = null;

  public ProblemsHolder(@NotNull InspectionManager manager, @NotNull PsiFile file, boolean onTheFly) {
    myManager = manager;
    myFile = file;
    myOnTheFly = onTheFly;
  }

  public void registerProblem(@NotNull PsiElement psiElement, @Nls String descriptionTemplate, LocalQuickFix... fixes) {
    registerProblem(psiElement, descriptionTemplate, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  public void registerProblem(@NotNull PsiElement psiElement,
                              String descriptionTemplate,
                              ProblemHighlightType highlightType,
                              LocalQuickFix... fixes) {
    registerProblem(myManager.createProblemDescriptor(psiElement, descriptionTemplate, myOnTheFly, fixes, highlightType));
  }

  public void registerProblem(@NotNull ProblemDescriptor problemDescriptor) {
    PsiElement element = problemDescriptor.getPsiElement();
    if (element != null && !isInPsiFile(element)) {
      ExternallyDefinedPsiElement external = PsiTreeUtil.getParentOfType(element, ExternallyDefinedPsiElement.class, false);
      if (external != null) {
        PsiElement newTarget = external.getProblemTarget();
        if (newTarget != null) {
          redirectProblem(problemDescriptor, newTarget);
          return;
        }
      }

      PsiFile containingFile = element.getContainingFile();
      PsiElement context = containingFile.getContext();
      PsiElement myContext = myFile.getContext();
      LOG.error("Reported element " + element + " is not from the file '" + myFile + "' the inspection was invoked for. Message: '" + problemDescriptor.getDescriptionTemplate()+"'.\n" +
                "Element' containing file: "+ containingFile +"; context: "+(context == null ? null : context.getContainingFile())+"\n"
                +"Inspection invoked for file: "+ myFile +"; context: "+(myContext == null ? null : myContext.getContainingFile())+"\n"
                );
    }

    if (myProblems == null) {
      myProblems = new ArrayList<ProblemDescriptor>(1);
    }
    myProblems.add(problemDescriptor);
  }

  private boolean isInPsiFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return myFile.getViewProvider().getAllFiles().contains(file);
  }

  private void redirectProblem(@NotNull final ProblemDescriptor problem, @NotNull final PsiElement target) {
    final PsiElement original = problem.getPsiElement();
    final VirtualFile vFile = original.getContainingFile().getVirtualFile();
    assert vFile != null;
    final String path = FileUtil.toSystemIndependentName(vFile.getPath());

    String description = problem.getDescriptionTemplate();
    if (description.startsWith("<html>")) {
      description = description.replace("<html>", "").replace("</html>", "");
    }
    if (description.startsWith("<body>")) {
      description = description.replace("<body>", "").replace("</body>", "");
    }

    final String template =
      InspectionsBundle.message("inspection.redirect.template",
                                description, path, original.getTextRange().getStartOffset(), vFile.getName());


    final InspectionManager manager = InspectionManager.getInstance(original.getProject());
    final ProblemDescriptor newProblem =
      manager.createProblemDescriptor(target, template, (LocalQuickFix)null, problem.getHighlightType(), isOnTheFly());
    registerProblem(newProblem);
  }

  public void registerProblem(@NotNull PsiReference reference, String descriptionTemplate, ProblemHighlightType highlightType) {
    LocalQuickFix[] fixes = null;
    if (reference instanceof LocalQuickFixProvider) {
      fixes = ((LocalQuickFixProvider)reference).getQuickFixes();
    }
    registerProblem(reference, highlightType, descriptionTemplate, fixes);
  }

  public void registerProblem(@NotNull PsiReference reference,
                              ProblemHighlightType highlightType,
                              String descriptionTemplate,
                              LocalQuickFix... fixes) {
    registerProblem(myManager.createProblemDescriptor(reference.getElement(), reference.getRangeInElement(), descriptionTemplate, highlightType,
                                                      myOnTheFly, fixes));
  }

  public void registerProblem(@NotNull PsiReference reference) {
    assert reference instanceof EmptyResolveMessageProvider;
    registerProblem(reference, ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern(), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  public void registerProblem(@NotNull final PsiElement psiElement,
                              @NotNull final String message,
                              final ProblemHighlightType highlightType,
                              final TextRange rangeInElement,
                              final LocalQuickFix... fixes) {

    final ProblemDescriptor descriptor = myManager.createProblemDescriptor(psiElement, rangeInElement, message, highlightType, myOnTheFly,
                                                                           fixes);
    registerProblem(descriptor);
  }

  public void registerProblem(@NotNull final PsiElement psiElement,
                              final TextRange rangeInElement,
                              @NotNull final String message,
                              final LocalQuickFix... fixes) {

    final ProblemDescriptor descriptor = myManager.createProblemDescriptor(psiElement, rangeInElement, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, fixes);
    registerProblem(descriptor);
  }

  @Nullable
  public List<ProblemDescriptor> getResults() {
    final List<ProblemDescriptor> problems = myProblems;
    myProblems = null;
    return problems;
  }

  @Nullable
  public ProblemDescriptor[] getResultsArray() {
    final List<ProblemDescriptor> problems = myProblems;
    myProblems = null;
    return problems == null ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public final InspectionManager getManager() {
    return myManager;
  }
  public boolean hasResults() {
    return myProblems != null && !myProblems.isEmpty();
  }

  public boolean isOnTheFly() {
    return myOnTheFly;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public final Project getProject() {
    return myManager.getProject();
  }
}
