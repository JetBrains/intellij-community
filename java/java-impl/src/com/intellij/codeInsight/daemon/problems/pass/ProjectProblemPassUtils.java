// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.hints.BlockConstrainedPresentation;
import com.intellij.codeInsight.hints.BlockConstraints;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.InlayPresentationFactory;
import com.intellij.codeInsight.hints.presentation.*;
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable;
import com.intellij.codeInspection.SmartHashMap;
import com.intellij.find.FindUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ProjectProblemPassUtils {

  private static final Key<Map<SmartPsiElementPointer<PsiMember>, Inlay<?>>> PROBLEM_INLAY_HINTS = Key.create("ProjectProblemInlayHintsKey");

  private static final Key<Long> PREV_MODIFICATION_COUNT = Key.create("ProjectProblemInlayPassModificationCount");

  static @NotNull InlayPresentation getPresentation(@NotNull Project project,
                                                    @NotNull Editor editor,
                                                    @NotNull Document document,
                                                    @NotNull PresentationFactory factory,
                                                    int offset,
                                                    @NotNull PsiElement element,
                                                    @NotNull Set<PsiElement> brokenUsages) {
    int column = offset - document.getLineStartOffset(document.getLineNumber(offset));
    int columnWidth = EditorUtil.getPlainSpaceWidth(editor);
    InlayPresentation usagesPresentation = getUsagesPresentation(project, factory, element, brokenUsages, column, columnWidth);
    InlayPresentation settingsPresentation = getSettingsPresentation(project, factory);
    InlayPresentation settingsPlaceholder = new SpacePresentation(columnWidth * 5, 0);
    return createTopLevelPresentation(factory, usagesPresentation, settingsPresentation, settingsPlaceholder);
  }

  private static InlayPresentation getUsagesPresentation(Project project,
                                                         PresentationFactory factory,
                                                         PsiElement element,
                                                         Set<PsiElement> brokenUsages,
                                                         int column,
                                                         int columnWidth) {
    SpacePresentation usagesOffset = new SpacePresentation(column * columnWidth, 0);
    InlayPresentation textPresentation = factory.smallText(JavaErrorBundle.message("project.problems.broken.usages", brokenUsages.size()));
    InlayPresentation usagesPresentation = factory.referenceOnHover(textPresentation, (e, p) -> {
      if (brokenUsages.size() == 1) {
        PsiElement usage = brokenUsages.iterator().next();
        if (usage instanceof Navigatable) ((Navigatable)usage).navigate(true);
      }
      else {
        FindUtil.showInUsageView(element, brokenUsages.toArray(PsiElement.EMPTY_ARRAY),
                                 JavaErrorBundle.message("project.problems.title"), project);
      }
    });
    SpacePresentation settingsOffset = new SpacePresentation(columnWidth, 0);
    return factory.seq(usagesOffset, usagesPresentation, settingsOffset);
  }

  private static InlayPresentation getSettingsPresentation(Project project, PresentationFactory factory) {
    InlayPresentation textPresentation = factory.smallText("Settings...");
    return factory.referenceOnHover(textPresentation, (event, translated) -> {
      InlayHintsConfigurable.showSettingsDialogForLanguage(project, JavaLanguage.INSTANCE);
    });
  }

  private static InlayPresentation createTopLevelPresentation(PresentationFactory factory,
                                                              InlayPresentation usages,
                                                              InlayPresentation settings,
                                                              InlayPresentation settingsPlaceholder) {
    BiStatePresentation settingsOrPlaceholder = new BiStatePresentation(() -> settings, () -> settingsPlaceholder, false) {
      @Override
      public int getWidth() {
        return Math.max(settings.getWidth(), settingsPlaceholder.getWidth());
      }

      @Override
      public int getHeight() {
        return Math.max(settings.getHeight(), settingsPlaceholder.getHeight());
      }
    };
    return factory.onHover(factory.seq(usages, settingsOrPlaceholder), new InlayPresentationFactory.HoverListener() {
      @Override
      public void onHover(@NotNull MouseEvent event, @NotNull Point translated) {
        settingsOrPlaceholder.setFirst();
      }

      @Override
      public void onHoverFinished() {
        settingsOrPlaceholder.setSecond();
      }
    });
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
