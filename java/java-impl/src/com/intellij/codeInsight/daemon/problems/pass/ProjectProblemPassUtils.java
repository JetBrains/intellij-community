// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.JavaLensProvider;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.problems.Problem;
import com.intellij.codeInsight.hints.BlockConstrainedPresentation;
import com.intellij.codeInsight.hints.BlockConstraints;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.presentation.*;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.SmartHashMap;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;


public class ProjectProblemPassUtils {

  private static final Key<Map<SmartPsiElementPointer<PsiMember>, EditorInfo>> EDITOR_INFOS_KEY = Key.create("ProjectProblemEditorInfoKey");

  private static final Key<Long> PREV_MODIFICATION_COUNT = Key.create("ProjectProblemModificationCount");

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
    InlayPresentation problemsPresentation = factory.referenceOnHover(errorTextPresentation, (e, p) -> showProblems(member, relatedProblems));

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem item = new JMenuItem(JavaBundle.message("project.problems.settings"));
    item.addActionListener(e -> JavaLensProvider.openSettings(JavaLanguage.INSTANCE, project));
    popupMenu.add(item);

    InlayPresentation withSettings = factory.onClick(problemsPresentation, MouseButton.Right, (e, __) -> {
      popupMenu.show(e.getComponent(), e.getX(), e.getY());
      return Unit.INSTANCE;
    });

    return factory.seq(problemsOffset, withSettings);
  }

  private static void showProblems(@NotNull PsiMember member, @NotNull Set<Problem> relatedProblems) {
    FUCounterUsageLogger.getInstance().logEvent(member.getProject(), JavaLensProvider.FUS_GROUP_ID, RELATED_PROBLEMS_CLICKED_EVENT_ID);

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

  static @NotNull BlockInlayRenderer createBlockRenderer(@NotNull InlayPresentation presentation) {
    BlockConstraints constraints = new BlockConstraints(true, BlockInlayPriority.PROBLEMS);
    RecursivelyUpdatingRootPresentation rootPresentation = new RecursivelyUpdatingRootPresentation(presentation);
    BlockConstrainedPresentation<InlayPresentation> constrainedPresentation =
      new BlockConstrainedPresentation<>(rootPresentation, constraints);
    return new BlockInlayRenderer(Collections.singletonList(constrainedPresentation));
  }

  static void addListener(@NotNull BlockInlayRenderer renderer, @NotNull Inlay<?> inlay) {
    renderer.setListener(new PresentationListener() {
      @Override
      public void sizeChanged(@NotNull Dimension previous, @NotNull Dimension current) {
        inlay.repaint();
      }

      @Override
      public void contentChanged(@NotNull Rectangle area) {
        inlay.repaint();
      }
    });
  }

  static @NotNull HighlightInfo createHighlightInfo(@NotNull Editor editor,
                                                    @NotNull PsiMember member,
                                                    @NotNull PsiElement identifier,
                                                    @NotNull Set<Problem> relatedProblems) {
    ShowRelatedProblemsAction relatedProblemsAction = new ShowRelatedProblemsAction(relatedProblems);
    return createHighlightInfo(editor, member, identifier, relatedProblemsAction);
  }

  private static @NotNull HighlightInfo createHighlightInfo(@NotNull Editor editor,
                                                            @NotNull PsiMember member,
                                                            @NotNull PsiElement identifier,
                                                            @NotNull IntentionAction action) {
    Color textColor = editor.getColorsScheme().getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES).getEffectColor();
    TextAttributes attributes = new TextAttributes(null, null, textColor, null, Font.PLAIN);
    String memberName = UsageViewUtil.getLongName(member);

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
      .range(identifier.getTextRange())
      .textAttributes(attributes)
      .descriptionAndTooltip(JavaBundle.message("project.problems.fix.description", memberName))
      .createUnconditionally();

    QuickFixAction.registerQuickFixAction(info, action);
    return info;
  }

  static int getMemberOffset(@NotNull PsiMember psiMember) {
    return Arrays.stream(psiMember.getChildren())
      .filter(c -> !(c instanceof PsiComment) && !(c instanceof PsiWhiteSpace))
      .findFirst().orElse(psiMember)
      .getTextRange().getStartOffset();
  }

  static boolean hasOtherElementsOnSameLine(@NotNull PsiMember psiMember) {
    PsiElement prevSibling = psiMember.getPrevSibling();
    while (prevSibling != null && !(prevSibling instanceof PsiWhiteSpace && prevSibling.textContains('\n'))) {
      if (!(prevSibling instanceof PsiWhiteSpace) && !prevSibling.getText().isEmpty()) return true;
      prevSibling = prevSibling.getPrevSibling();
    }
    return false;
  }

  static boolean hintsEnabled() {
    return JavaLensProvider.getSettings().isShowRelatedProblems();
  }

  public static @NotNull Map<PsiMember, Inlay<?>> getInlays(@NotNull Editor editor) {
    return ContainerUtil.map2Map(getEditorInfos(editor).entrySet(), e -> Pair.create(e.getKey(), e.getValue().myInlay));
  }

  static @NotNull Map<PsiMember, EditorInfo> getEditorInfos(@NotNull Editor editor) {
    Map<SmartPsiElementPointer<PsiMember>, EditorInfo> oldInfos = editor.getUserData(EDITOR_INFOS_KEY);
    Map<PsiMember, EditorInfo> editorInfos = new SmartHashMap<>();
    if (oldInfos == null) return editorInfos;
    InlayModel inlayModel = editor.getInlayModel();
    oldInfos.forEach((pointer, info) -> {
      PsiMember member = pointer.getElement();
      Inlay<?> inlay = info.myInlay;
      if (member == null) {
        Disposer.dispose(inlay);
      }
      else {
        int curOffset = inlay.getOffset();
        int memberOffset = getMemberOffset(member);
        if (curOffset != memberOffset) {
          EditorCustomElementRenderer renderer = inlay.getRenderer();
          info.myInlay = inlayModel.addBlockElement(memberOffset, true, true, BlockInlayPriority.PROBLEMS, renderer);
          Disposer.dispose(inlay);
        }

        PsiElement identifier = getIdentifier(member);
        if (identifier != null) {
          HighlightInfo oldHighlightInfo = info.myHighlightInfo;
          if (!identifier.getTextRange().equalsToRange(oldHighlightInfo.getActualStartOffset(), oldHighlightInfo.getActualEndOffset())) {
            IntentionAction action = getRegisteredAction(oldHighlightInfo);
            if (action != null) {
              HighlightInfo newHighlightInfo = createHighlightInfo(editor, member, identifier, action);
              UpdateHighlightersUtil.setHighlightersToEditor(member.getProject(), editor.getDocument(), 0,
                                                             member.getContainingFile().getTextLength(),
                                                             Collections.singletonList(newHighlightInfo), editor.getColorsScheme(), -1);
              info.myHighlightInfo = newHighlightInfo;
            }
          }
        }

        editorInfos.put(member, info);
      }
    });
    return editorInfos;
  }

  private static @Nullable IntentionAction getRegisteredAction(@NotNull HighlightInfo highlightInfo) {
    List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> actionRanges = highlightInfo.quickFixActionRanges;
    if (actionRanges == null || actionRanges.size() != 1) return null;
    return actionRanges.get(0).first.getAction();
  }

  static void updateInfos(@NotNull Editor editor, @NotNull Map<PsiMember, EditorInfo> infos) {
    Map<SmartPsiElementPointer<PsiMember>, EditorInfo> newInfos =
      ContainerUtil.map2Map(infos.entrySet(), e -> Pair.create(SmartPointerManager.createPointer(e.getKey()), e.getValue()));
    editor.putUserData(EDITOR_INFOS_KEY, newInfos);
  }

  static void removeInfos(@NotNull Editor editor) {
    Map<SmartPsiElementPointer<PsiMember>, EditorInfo> infos = editor.getUserData(EDITOR_INFOS_KEY);
    if (infos == null) return;
    infos.values().forEach(info -> Disposer.dispose(info.myInlay));
    editor.putUserData(EDITOR_INFOS_KEY, null);
  }

  static boolean isDocumentUpdated(@NotNull Editor editor) {
    Document document = editor.getDocument();
    long stamp = document.getModificationStamp();
    Long prevStamp = document.getUserData(PREV_MODIFICATION_COUNT);
    return prevStamp == null || prevStamp != stamp;
  }

  static void updateTimestamp(@NotNull Editor editor) {
    Document document = editor.getDocument();
    long timestamp = document.getModificationStamp();
    document.putUserData(PREV_MODIFICATION_COUNT, timestamp);
  }

  @Nullable
  static PsiElement getIdentifier(@NotNull PsiMember psiMember) {
    PsiNameIdentifierOwner identifierOwner = tryCast(psiMember, PsiNameIdentifierOwner.class);
    if (identifierOwner == null) return null;
    return identifierOwner.getNameIdentifier();
  }

  static class EditorInfo {

    Inlay<?> myInlay;
    HighlightInfo myHighlightInfo;

    EditorInfo(@NotNull Inlay<?> inlay, @NotNull HighlightInfo info) {
      myInlay = inlay;
      myHighlightInfo = info;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      EditorInfo info = (EditorInfo)o;
      return myInlay.equals(info.myInlay) &&
             myHighlightInfo.equals(info.myHighlightInfo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myInlay, myHighlightInfo);
    }
  }

  private static final class ShowRelatedProblemsAction extends BaseElementAtCaretIntentionAction {

    private final Set<Problem> myRelatedProblems;

    private ShowRelatedProblemsAction(Set<Problem> relatedProblems) {
      myRelatedProblems = relatedProblems;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      return hintsEnabled();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
      PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
      if (member == null) return;
      showProblems(member, myRelatedProblems);
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
