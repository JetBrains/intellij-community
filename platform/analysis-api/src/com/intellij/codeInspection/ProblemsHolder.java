// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.BundleBase;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.modcommand.ModCommandAction;
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
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages collection of {@link ProblemDescriptor} with convenience factory methods.
 */
public class ProblemsHolder {
  private static final Logger LOG = Logger.getInstance(ProblemsHolder.class);

  private final InspectionManager myManager;
  private final PsiFile myPsiFile;
  private final boolean myOnTheFly;
  private final List<ProblemDescriptor> myProblems = new ArrayList<>();

  public ProblemsHolder(@NotNull InspectionManager manager, @NotNull PsiFile psiFile, boolean onTheFly) {
    myManager = manager;
    myPsiFile = psiFile;
    myOnTheFly = onTheFly;
  }

  public void registerProblem(@NotNull PsiElement psiElement,
                              @NotNull @InspectionMessage String descriptionTemplate,
                              @NotNull LocalQuickFix @Nullable ... fixes) {
    registerProblem(psiElement, descriptionTemplate, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  public void registerProblem(@NotNull PsiElement psiElement,
                              @NotNull @InspectionMessage String descriptionTemplate,
                              @NotNull ProblemHighlightType highlightType,
                              @NotNull LocalQuickFix @Nullable ... fixes) {
    registerProblem(myManager.createProblemDescriptor(psiElement, descriptionTemplate, myOnTheFly, fixes, highlightType));
  }

  public void registerProblem(@NotNull ProblemDescriptor problemDescriptor) {
    PsiElement psiElement = problemDescriptor.getPsiElement();
    if (psiElement != null && !isInPsiFile(psiElement)) {
      ExternallyDefinedPsiElement external = PsiTreeUtil.getParentOfType(psiElement, ExternallyDefinedPsiElement.class, false);
      if (external != null) {
        PsiElement newTarget = external.getProblemTarget();
        if (newTarget != null) {
          redirectProblem(problemDescriptor, newTarget);
          return;
        }
      }
      if (isOnTheFly()) {
        LOG.error("Inspection generated invalid ProblemDescriptor '" + problemDescriptor + "'." +
                  " It contains PsiElement with getContainingFile(): '" + psiElement.getContainingFile() + "' (" + psiElement.getContainingFile().getClass() + ")" +
                  "; but expected: '" + getFile() + "' (" + getFile().getClass() + ")");
      }
    }

    saveProblem(problemDescriptor);
  }

  protected void saveProblem(@NotNull ProblemDescriptor problemDescriptor) {
    myProblems.add(problemDescriptor);
  }

  @ApiStatus.Internal
  protected boolean isInPsiFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null && myPsiFile.getViewProvider() == file.getViewProvider();
  }

  @ApiStatus.Internal
  protected void redirectProblem(@NotNull ProblemDescriptor problem, @NotNull PsiElement target) {
    PsiElement original = problem.getPsiElement();
    VirtualFile vFile = original.getContainingFile().getVirtualFile();
    assert vFile != null;
    String path = FileUtil.toSystemIndependentName(vFile.getPath());

    String description = XmlStringUtil.stripHtml(problem.getDescriptionTemplate());

    String template = AnalysisBundle.message("inspection.redirect.template",
                                             description, path, original.getTextRange().getStartOffset(), vFile.getName());
    ProblemDescriptor newProblem =
      getManager().createProblemDescriptor(target, template, (LocalQuickFix)null, problem.getHighlightType(), isOnTheFly());
    registerProblem(newProblem);
  }

  public void registerProblem(@NotNull PsiReference reference,
                              @InspectionMessage String descriptionTemplate,
                              ProblemHighlightType highlightType) {
    LocalQuickFix[] fixes = null;
    if (reference instanceof LocalQuickFixProvider) {
      fixes = ((LocalQuickFixProvider)reference).getQuickFixes();
    }
    registerProblemForReference(reference, highlightType, descriptionTemplate, fixes);
  }

  public void registerProblemForReference(@NotNull PsiReference reference,
                                          @NotNull ProblemHighlightType highlightType,
                                          @NotNull @InspectionMessage String descriptionTemplate,
                                          @NotNull LocalQuickFix @Nullable ... fixes) {
    ProblemDescriptor descriptor = myManager.createProblemDescriptor(reference.getElement(), reference.getRangeInElement(),
                                                                     descriptionTemplate, highlightType, myOnTheFly, fixes);
    registerProblem(descriptor);
  }

  public void registerProblem(@NotNull PsiReference reference) {
    registerProblem(reference, unresolvedReferenceMessage(reference), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  public void registerProblem(@NotNull PsiReference reference, @NotNull ProblemHighlightType highlightType) {
    registerProblem(reference, unresolvedReferenceMessage(reference), highlightType);
  }

  /**
   * Use to register a place ({@code identifier}) which was skipped during local analysis e.g., due to too long search or similar.
   * <p/>
   * Such problems would be silently skipped in batch. During local analysis they would signal 'RedundantSuppression' inspection
   * that this part was not fully processed by initial inspection and that the suppression may be not redundant
   */
  @SuppressWarnings({"HardCodedStringLiteral", "DialogTitleCapitalization"})
  public void registerPossibleProblem(PsiElement identifier) {
    registerProblem(identifier, "possible problem", ProblemHighlightType.POSSIBLE_PROBLEM);
  }

  @ApiStatus.Internal
  public void clearResults() {
    myProblems.clear();
  }

  /**
   * Returns {@link EmptyResolveMessageProvider#getUnresolvedMessagePattern()} (if implemented),
   * otherwise, default message "Cannot resolve symbol '[reference.getCanonicalText()]'".
   */
  public static @NotNull @InspectionMessage String unresolvedReferenceMessage(@NotNull PsiReference reference) {
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
      message = AnalysisBundle.message("error.cannot.resolve.default.message", reference.getCanonicalText());
    }
    return message;
  }

  public void registerProblem(@NotNull PsiElement psiElement,
                              @Nullable TextRange rangeInElement,
                              @NotNull @InspectionMessage String descriptionTemplate,
                              @NotNull LocalQuickFix @Nullable ... fixes) {
    registerProblem(psiElement, descriptionTemplate, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInElement, fixes);
  }

  /**
   * Creates highlighter for the specified place in the file.
   *
   * @param psiElement          The highlighter will be created at the text range of this element. The element must be in the current file.
   * @param descriptionTemplate Message for this highlighter, also used for tooltip. See {@link CommonProblemDescriptor#getDescriptionTemplate()}.
   * @param highlightType       The level of highlighter.
   * @param rangeInElement      The (sub)range (must be inside (0..psiElement.getTextRange().getLength()) to create highlighter in,
   *                            {@code null} for highlighting full text range.
   * @param fixes               (Optional) fixes to appear for this highlighter.
   */
  public void registerProblem(@NotNull PsiElement psiElement,
                              @NotNull @InspectionMessage String descriptionTemplate,
                              @NotNull ProblemHighlightType highlightType,
                              @Nullable TextRange rangeInElement,
                              @NotNull LocalQuickFix @Nullable ... fixes) {
    registerProblem(myManager.createProblemDescriptor(psiElement, rangeInElement, descriptionTemplate, highlightType, myOnTheFly, fixes));
  }

  public @NotNull @Unmodifiable List<ProblemDescriptor> getResults() {
    return myProblems;
  }

  public ProblemDescriptor @NotNull [] getResultsArray() {
    List<ProblemDescriptor> problems = getResults();
    return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  public final @NotNull InspectionManager getManager() {
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

  public @NotNull PsiFile getFile() {
    return myPsiFile;
  }

  public final @NotNull Project getProject() {
    return myManager.getProject();
  }

  /**
   * Creates a builder to report a problem. Make sure to call {@link ProblemBuilder#register()} afterwards 
   * @param psiElement element to anchor the problem
   * @param descriptionTemplate problem description template
   * @return the builder that allows adding more information and eventually register the problem
   */
  @Contract(pure = true)
  public @NotNull ProblemBuilder problem(@NotNull PsiElement psiElement, @InspectionMessage @NotNull String descriptionTemplate) {
    return new ProblemBuilder(psiElement, descriptionTemplate);
  }

  /**
   * The builder to create a problem report
   */
  @SuppressWarnings("UnstableApiUsage")
  public final class ProblemBuilder {
    private final @InspectionMessage @NotNull String myDescriptionTemplate;
    private final @NotNull PsiElement myPsiElement;
    private @NotNull ProblemHighlightType myHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    private @Nullable TextRange myRange;
    private final @NotNull List<LocalQuickFix> myFixes = new ArrayList<>();

    private ProblemBuilder(@NotNull PsiElement element, @InspectionMessage @NotNull String template) {
      myPsiElement = element;
      myDescriptionTemplate = template;
    }

    /**
     * @param problemHighlightType desired highlighting type (default is GENERIC_ERROR_OR_WARNING)
     * @return this builder
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ProblemBuilder highlight(ProblemHighlightType problemHighlightType) {
      myHighlightType = problemHighlightType;
      return this;
    }

    /**
     * @param rangeInElement desired highlighting range within the element
     * @return this builder
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ProblemBuilder range(@NotNull TextRange rangeInElement) {
      myRange = rangeInElement;
      return this;
    }

    /**
     * @param fix a new fix to add to the problem
     * @return this builder
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ProblemBuilder fix(@NotNull LocalQuickFix fix) {
      myFixes.add(fix);
      return this;
    }

    /**
     * @param action a new fix to add to the problem
     * @return this builder
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ProblemBuilder fix(@NotNull ModCommandAction action) {
      myFixes.add(LocalQuickFix.from(action));
      return this;
    }

    /**
     * @param fix a new fix to add to the problem; does nothing if it's null
     * @return this builder
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ProblemBuilder maybeFix(@Nullable LocalQuickFix fix) {
      if (fix != null) {
        myFixes.add(fix);
      }
      return this;
    }

    /**
     * @param action a new fix to add to the problem; does nothing if it's null
     * @return this builder
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ProblemBuilder maybeFix(@Nullable ModCommandAction action) {
      if (action != null) {
        myFixes.add(LocalQuickFix.from(action));
      }
      return this;
    }

    public void register() {
      registerProblem(myPsiElement, myDescriptionTemplate, myHighlightType, myRange, myFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }
}