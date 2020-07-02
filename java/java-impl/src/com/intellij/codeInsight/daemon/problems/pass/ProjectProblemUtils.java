// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.JavaLensProvider;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.problems.Problem;
import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ProjectProblemUtils {

  private static final Key<Map<PsiMember, Set<Problem>>> PROBLEMS_KEY = Key.create("project.problems.problem.key");
  private static final Key<Long> MODIFICATION_COUNT = Key.create("ProjectProblemModificationCount");
  private static final String RELATED_PROBLEMS_CLICKED_EVENT_ID = "related.problems.clicked";

  static @NotNull InlayPresentation getPresentation(@NotNull Project project,
                                                    @NotNull Editor editor,
                                                    @NotNull Document document,
                                                    @NotNull PresentationFactory factory,
                                                    int offset,
                                                    @NotNull PsiMember member,
                                                    @NotNull Set<Problem> relatedProblems) {
    int column = offset - document.getLineStartOffset(document.getLineNumber(offset));
    InlayPresentation problemsOffset = factory.textSpacePlaceholder(column, true);
    InlayPresentation textPresentation = factory.smallText(JavaBundle.message("project.problems.hint.text", relatedProblems.size()));
    InlayPresentation errorTextPresentation = new AttributesTransformerPresentation(textPresentation, __ ->
      editor.getColorsScheme().getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES));
    InlayPresentation problemsPresentation =
      factory.referenceOnHover(errorTextPresentation, (e, p) -> showProblems(editor, member));

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem item = new JMenuItem(JavaBundle.message("project.problems.settings"));
    item.addActionListener(e -> ProjectProblemHintProvider.openSettings(project));
    popupMenu.add(item);

    InlayPresentation withSettings = factory.onClick(problemsPresentation, MouseButton.Right, (e, __) -> {
      popupMenu.show(e.getComponent(), e.getX(), e.getY());
      return Unit.INSTANCE;
    });

    return factory.seq(problemsOffset, withSettings);
  }

  private static void showProblems(@NotNull Editor editor, @NotNull PsiMember member) {
    FUCounterUsageLogger.getInstance().logEvent(member.getProject(), JavaLensProvider.FUS_GROUP_ID, RELATED_PROBLEMS_CLICKED_EVENT_ID);

    Map<PsiMember, Set<Problem>> problems = getReportedProblems(editor);
    Set<Problem> relatedProblems = problems.get(member);
    if (relatedProblems == null || relatedProblems.isEmpty()) return;
    Project project = member.getProject();
    if (relatedProblems.size() == 1) {
      Problem problem = relatedProblems.iterator().next();
      PsiElement reportedElement = problem.getReportedElement();
      VirtualFile fileWithProblem = reportedElement.getContainingFile().getVirtualFile();
      TextRange elementRange = reportedElement.getTextRange();
      int offset = elementRange != null ? elementRange.getStartOffset() : -1;
      PsiNavigationSupport.getInstance().createNavigatable(project, fileWithProblem, offset).navigate(true);
    }
    else {
      String memberName = UsageViewUtil.getLongName(member);

      UsageViewPresentation presentation = new UsageViewPresentation();
      String title = JavaBundle.message("project.problems.window.title", memberName);
      presentation.setCodeUsagesString(JavaBundle.message("project.problems.title"));
      presentation.setTabName(title);
      presentation.setTabText(title);

      Usage[] usages = ContainerUtil.map2Array(relatedProblems, new Usage[relatedProblems.size()], e -> {
        PsiElement reportedElement = e.getReportedElement();
        UsageInfo usageInfo = new UsageInfo(e.getContext());
        return new BrokenUsage(usageInfo, reportedElement);
      });

      UsageTarget[] usageTargets = new UsageTarget[]{new RelatedProblemTargetAdapter(member)};
      UsageViewManager usageViewManager = UsageViewManager.getInstance(project);
      usageViewManager.showUsages(usageTargets, usages, presentation);
    }
  }

  public static @NotNull Map<PsiMember, Set<Problem>> getReportedProblems(@NotNull Editor editor) {
    Map<PsiMember, Set<Problem>> problems = editor.getUserData(PROBLEMS_KEY);
    if (problems == null) return new HashMap<>();
    problems.entrySet().removeIf(e -> !e.getKey().isValid());
    return problems;
  }

  static void reportProblems(@NotNull Editor editor, @NotNull Map<PsiMember, Set<Problem>> problems) {
    editor.putUserData(PROBLEMS_KEY, problems);
  }

  static int getMemberOffset(@NotNull PsiMember psiMember) {
    return Arrays.stream(psiMember.getChildren())
      .filter(c -> !(c instanceof PsiComment) && !(c instanceof PsiWhiteSpace))
      .findFirst().orElse(psiMember)
      .getTextRange().getStartOffset();
  }

  static @NotNull HighlightInfo createHighlightInfo(@NotNull Editor editor,
                                                    @NotNull PsiMember member,
                                                    @NotNull PsiElement identifier) {
    Color textColor = editor.getColorsScheme().getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES).getEffectColor();
    TextAttributes attributes = new TextAttributes(null, null, textColor, null, Font.PLAIN);
    String memberName = UsageViewUtil.getLongName(member);

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
      .range(identifier.getTextRange())
      .textAttributes(attributes)
      .descriptionAndTooltip(JavaBundle.message("project.problems.fix.description", memberName))
      .createUnconditionally();

    QuickFixAction.registerQuickFixAction(info, new ShowRelatedProblemsAction());
    return info;
  }

  static boolean isProjectUpdated(@NotNull PsiJavaFile psiJavaFile, @NotNull Editor editor) {
    Long prevTimestamp = editor.getUserData(MODIFICATION_COUNT);
    return prevTimestamp == null || prevTimestamp != psiJavaFile.getManager().getModificationTracker().getModificationCount();
  }

  static void updateTimestamp(@NotNull PsiJavaFile file, @NotNull Editor editor) {
    editor.putUserData(MODIFICATION_COUNT, file.getManager().getModificationTracker().getModificationCount());
  }

  private static class ShowRelatedProblemsAction extends BaseElementAtCaretIntentionAction {

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      return ProjectProblemHintProvider.hintsEnabled();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
      PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
      if (member == null) return;
      showProblems(editor, member);
    }

    @Override
    public @NotNull String getText() {
      return getFamilyName();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("project.problems.fix.text");
    }
  }
}
