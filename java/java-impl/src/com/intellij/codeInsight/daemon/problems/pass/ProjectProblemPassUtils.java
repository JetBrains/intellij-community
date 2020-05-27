// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.JavaLensProvider;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hints.BlockConstrainedPresentation;
import com.intellij.codeInsight.hints.BlockConstraints;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.presentation.*;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInspection.SmartHashMap;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;


public class ProjectProblemPassUtils {

  private static final Key<Map<SmartPsiElementPointer<PsiMember>, EditorInfo>> EDITOR_INFOS_KEY = Key.create("ProjectProblemEditorInfoKey");

  private static final Key<Long> PREV_MODIFICATION_COUNT = Key.create("ProjectProblemModificationCount");

  static @NotNull InlayPresentation getPresentation(@NotNull Project project,
                                                    @NotNull Editor editor,
                                                    @NotNull Document document,
                                                    @NotNull PresentationFactory factory,
                                                    int offset,
                                                    @NotNull PsiMember member,
                                                    @NotNull Set<PsiElement> brokenUsages) {
    int column = offset - document.getLineStartOffset(document.getLineNumber(offset));
    int columnWidth = EditorUtil.getPlainSpaceWidth(editor);
    SpacePresentation usagesOffset = new SpacePresentation(column * columnWidth, 0);
    InlayPresentation textPresentation = factory.smallText(JavaBundle.message("project.problems.broken.usages", brokenUsages.size()));
    InlayPresentation errorTextPresentation = new AttributesTransformerPresentation(textPresentation, __ ->
      editor.getColorsScheme().getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES));
    InlayPresentation usagesPresentation = factory.referenceOnHover(errorTextPresentation, (e, p) -> showUsages(member, brokenUsages));

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem item = new JMenuItem(JavaBundle.message("project.problems.settings"));
    item.addActionListener(e -> JavaLensProvider.openSettings(JavaLanguage.INSTANCE, project));
    popupMenu.add(item);

    InlayPresentation withSettings = factory.onClick(usagesPresentation, MouseButton.Right, (e, __) -> {
      popupMenu.show(e.getComponent(), e.getX(), e.getY());
      return Unit.INSTANCE;
    });

    return factory.seq(usagesOffset, withSettings);
  }

  private static void showUsages(@NotNull PsiMember member, @NotNull Set<PsiElement> brokenUsages) {
    Project project = member.getProject();
    if (brokenUsages.size() == 1) {
      PsiElement usage = brokenUsages.iterator().next();
      if (usage instanceof Navigatable) ((Navigatable)usage).navigate(true);
    }
    else {
      String memberName = Objects.requireNonNull(member.getName());

      UsageViewPresentation presentation = new UsageViewPresentation();
      String title = JavaBundle.message("project.problems.window.title", memberName);
      presentation.setCodeUsagesString(title);
      presentation.setTabName(title);
      presentation.setTabText(title);

      PsiElement[] primary = new PsiElement[]{member};
      Usage[] usages = ContainerUtil.map2Array(brokenUsages, new Usage[brokenUsages.size()],
                                               e -> UsageInfoToUsageConverter.convert(primary, new UsageInfo(e)));

      UsageTarget[] usageTargets = new UsageTarget[]{new BrokenUsageTargetAdapter(member)};
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
                                                    @NotNull PsiElement identifier,
                                                    @NotNull Set<PsiElement> brokenUsages) {
    Color textColor = editor.getColorsScheme().getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES).getEffectColor();
    TextAttributes attributes = new TextAttributes(null, null, textColor, null, Font.PLAIN);

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
      .range(identifier.getTextRange())
      .textAttributes(attributes)
      .descriptionAndTooltip(JavaBundle.message("project.problems.fix.description", identifier.getText()))
      .createUnconditionally();

    QuickFixAction.registerQuickFixAction(info, new ShowBrokenUsagesAction(brokenUsages));
    return info;
  }

  static int getMemberOffset(@NotNull PsiMember psiMember) {
    return Arrays.stream(psiMember.getChildren())
      .filter(c -> !(c instanceof PsiDocComment) && !(c instanceof PsiWhiteSpace))
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
    return JavaLensProvider.getSettings().isShowBrokenUsages();
  }

  public static @NotNull Map<PsiMember, Inlay<?>> getInlays(@NotNull Editor editor) {
    return ContainerUtil.map2Map(getEditorInfos(editor).entrySet(), e -> Pair.create(e.getKey(), e.getValue().myInlay));
  }

  static @NotNull Map<PsiMember, EditorInfo> getEditorInfos(@NotNull Editor editor) {
    Map<SmartPsiElementPointer<PsiMember>, EditorInfo> oldInfos = editor.getUserData(EDITOR_INFOS_KEY);
    Map<PsiMember, EditorInfo> editorInfos = new SmartHashMap<>();
    if (oldInfos == null) return editorInfos;
    oldInfos.forEach((pointer, info) -> {
      PsiMember member = pointer.getElement();
      if (member == null) Disposer.dispose(info.myInlay);
      else editorInfos.put(member, info);
    });
    return editorInfos;
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

  static class EditorInfo {

    final Inlay<?> myInlay;
    final HighlightInfo myHighlightInfo;

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

  private static class ShowBrokenUsagesAction extends BaseElementAtCaretIntentionAction {

    private final Set<PsiElement> myBrokenUsages;

    private ShowBrokenUsagesAction(Set<PsiElement> usages) {
      myBrokenUsages = usages;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      return hintsEnabled();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
      PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
      if (member == null) return;
      showUsages(member, myBrokenUsages);
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
