// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.hints.BlockConstrainedPresentation;
import com.intellij.codeInsight.hints.BlockConstraints;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.presentation.*;
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable;
import com.intellij.codeInspection.SmartHashMap;
import com.intellij.find.FindUtil;
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
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class ProjectProblemPassUtils {

  private static final Key<Map<SmartPsiElementPointer<PsiMember>, Inlay<?>>> PROBLEM_INLAY_HINTS = Key.create("ProjectProblemInlayHintsKey");

  private static final Key<Long> PREV_MODIFICATION_COUNT = Key.create("ProjectProblemInlayPassModificationCount");

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
    InlayPresentation textPresentation = factory.smallText(JavaErrorBundle.message("project.problems.broken.usages", brokenUsages.size()));
    TextAttributes errorAttrs = editor.getColorsScheme().getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
    InlayPresentation errorTextPresentation = new AttributesTransformerPresentation(textPresentation, __ -> errorAttrs);
    InlayPresentation usagesPresentation = factory.referenceOnHover(errorTextPresentation, (e, p) -> {
      if (brokenUsages.size() == 1) {
        PsiElement usage = brokenUsages.iterator().next();
        if (usage instanceof Navigatable) ((Navigatable)usage).navigate(true);
      }
      else {
        String memberName = Objects.requireNonNull(member.getName());
        FindUtil.showInUsageView(member, brokenUsages.toArray(PsiElement.EMPTY_ARRAY),
                                 JavaErrorBundle.message("project.problems.title", memberName), project);
      }
    });

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem item = new JMenuItem(JavaErrorBundle.message("project.problems.settings"));
    item.addActionListener(e -> InlayHintsConfigurable.showSettingsDialogForLanguage(project, JavaLanguage.INSTANCE));
    popupMenu.add(item);

    InlayPresentation withSettings = factory.onClick(usagesPresentation, MouseButton.Right, (e, __) -> {
      popupMenu.show(e.getComponent(), e.getX(), e.getY());
      return Unit.INSTANCE;
    });

    return factory.seq(usagesOffset, withSettings);
  }

  static @NotNull BlockInlayRenderer createBlockRenderer(@NotNull InlayPresentation presentation) {
    BlockConstraints constraints = new BlockConstraints(true, BlockInlayPriority.PROBLEMS);
    RecursivelyUpdatingRootPresentation rootPresentation = new RecursivelyUpdatingRootPresentation(presentation);
    BlockConstrainedPresentation<InlayPresentation> constrainedPresentation =
      new BlockConstrainedPresentation<>(rootPresentation, constraints);
    return new BlockInlayRenderer(Collections.singletonList(constrainedPresentation));
  }

  static void addListener(BlockInlayRenderer renderer, Inlay<?> inlay) {
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

  public static @NotNull Map<PsiMember, Inlay<?>> getInlays(@NotNull Editor editor) {
    Map<SmartPsiElementPointer<PsiMember>, Inlay<?>> oldInlays = editor.getUserData(PROBLEM_INLAY_HINTS);
    Map<PsiMember, Inlay<?>> inlays = new SmartHashMap<>();
    if (oldInlays == null) return inlays;
    oldInlays.forEach((pointer, inlay) -> {
      PsiMember member = pointer.getElement();
      if (member == null) Disposer.dispose(inlay);
      else inlays.put(member, inlay);
    });
    return inlays;
  }

  static void updateInlays(@NotNull Editor editor, @NotNull Map<PsiMember, Inlay<?>> inlays) {
    Map<SmartPsiElementPointer<PsiMember>, Inlay<?>> newInlays =
      ContainerUtil.map2Map(inlays.entrySet(), e -> Pair.create(SmartPointerManager.createPointer(e.getKey()), e.getValue()));
    editor.putUserData(PROBLEM_INLAY_HINTS, newInlays);
  }

  static void removeInlays(@NotNull Editor editor) {
    Map<SmartPsiElementPointer<PsiMember>, Inlay<?>> inlays = editor.getUserData(PROBLEM_INLAY_HINTS);
    if (inlays == null) return;
    inlays.values().forEach(inlay -> Disposer.dispose(inlay));
    editor.putUserData(PROBLEM_INLAY_HINTS, null);
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
}
