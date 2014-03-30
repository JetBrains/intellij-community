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

import com.intellij.BundleBase;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * @author max
 */
public class ProblemsHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ProblemsHolder");
  private final InspectionManager myManager;
  private final PsiFile myFile;
  private final boolean myOnTheFly;
  private MyList<ProblemDescriptor> myProblems = new MyList<ProblemDescriptor>();

  public ProblemsHolder(@NotNull InspectionManager manager, @NotNull PsiFile file, boolean onTheFly) {
    myManager = manager;
    myFile = file;
    myOnTheFly = onTheFly;
  }

  public void registerProblem(@NotNull PsiElement psiElement, @NotNull @Nls String descriptionTemplate, LocalQuickFix... fixes) {
    registerProblem(psiElement, descriptionTemplate, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  public void registerProblem(@NotNull PsiElement psiElement,
                              @NotNull String descriptionTemplate,
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
      PsiElement context = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
      PsiElement myContext = InjectedLanguageManager.getInstance(myFile.getProject()).getInjectionHost(myFile);
      LOG.error("Reported element " + element + " is not from the file '" + myFile + "' the inspection was invoked for. Message: '" + problemDescriptor.getDescriptionTemplate()+"'.\n" +
                "Element' containing file: "+ containingFile +"; context: "+(context == null ? null : context.getContainingFile())+"\n"
                +"Inspection invoked for file: "+ myFile +"; context: "+(myContext == null ? null : myContext.getContainingFile())+"\n"
                );
    }

    myProblems.add(problemDescriptor);
  }

  private boolean isInPsiFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return myFile.getViewProvider() == file.getViewProvider();
  }

  private void redirectProblem(@NotNull final ProblemDescriptor problem, @NotNull final PsiElement target) {
    final PsiElement original = problem.getPsiElement();
    final VirtualFile vFile = original.getContainingFile().getVirtualFile();
    assert vFile != null;
    final String path = FileUtil.toSystemIndependentName(vFile.getPath());

    String description = XmlStringUtil.stripHtml(problem.getDescriptionTemplate());

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
    registerProblemForReference(reference, highlightType, descriptionTemplate, fixes);
  }

  public void registerProblemForReference(@NotNull PsiReference reference,
                              ProblemHighlightType highlightType,
                              String descriptionTemplate,
                              LocalQuickFix... fixes) {
    registerProblem(myManager.createProblemDescriptor(reference.getElement(), reference.getRangeInElement(), descriptionTemplate, highlightType,
                                                      myOnTheFly, fixes));
  }

  public void registerProblem(@NotNull PsiReference reference) {
    registerProblem(reference, unresolvedReferenceMessage(reference), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  public static String unresolvedReferenceMessage(PsiReference reference) {
    String message;
    if (reference instanceof EmptyResolveMessageProvider) {
      String pattern = ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern();
      try {
        message = BundleBase.format(pattern, reference.getCanonicalText()); // avoid double formatting
      }
      catch (IllegalArgumentException ex) {
        // unresolvedMessage provided by third-party reference contains wrong format string (e.g. {}), tolerate it
        message = pattern;
        LOG.info(pattern);
      }
    }
    else {
      message = CodeInsightBundle.message("error.cannot.resolve.default.message", reference.getCanonicalText());
    }
    return message;
  }

  /**
   * Creates highlighter for the specified place in the file.
   * @param psiElement The highlighter will be created at the text range od this element. This psiElement must be in the current file.
   * @param message Message for this highlighter. Will also serve as a tooltip.
   * @param highlightType The level of highlighter.
   * @param rangeInElement The (sub)range (must be inside (0..psiElement.getTextRange().getLength()) to create highlighter in.
   *                       If you want to highlight only part of the supplied psiElement. Pass null otherwise.
   * @param fixes (Optional) fixes to appear for this highlighter.
   */
  public void registerProblem(@NotNull final PsiElement psiElement,
                              @NotNull final String message,
                              final ProblemHighlightType highlightType,
                              @Nullable TextRange rangeInElement,
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

  @NotNull
  public List<ProblemDescriptor> getResults() {
    MyList<ProblemDescriptor> problems = myProblems;
    problems.allowModifications(false);
    myProblems = new MyList<ProblemDescriptor>();
    return problems;
  }

  @NotNull
  public ProblemDescriptor[] getResultsArray() {
    final List<ProblemDescriptor> problems = getResults();
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @NotNull
  public final InspectionManager getManager() {
    return myManager;
  }

  public boolean hasResults() {
    return !myProblems.isEmpty();
  }

  public int getResultCount() {
    return myProblems.size();
  }

  public boolean isOnTheFly() {
    return myOnTheFly;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public final Project getProject() {
    return myManager.getProject();
  }

  private static class MyList<T> extends ArrayList<T> {
    private volatile boolean readOnly;
    @Override
    public boolean add(T o) {
      if (readOnly) throw new ConcurrentModificationException();
      return super.add(o);
    }

    private void allowModifications(boolean v) {
      readOnly = !v;
    }
  }
}
