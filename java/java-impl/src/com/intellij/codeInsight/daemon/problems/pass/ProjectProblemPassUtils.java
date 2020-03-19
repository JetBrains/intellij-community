// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.problems.ScopedMember;
import com.intellij.codeInsight.hints.BlockConstrainedPresentation;
import com.intellij.codeInsight.hints.BlockConstraints;
import com.intellij.codeInsight.hints.BlockInlayRenderer;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.RecursivelyUpdatingRootPresentation;
import com.intellij.codeInsight.hints.presentation.SpacePresentation;
import com.intellij.find.FindUtil;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProjectProblemPassUtils {

  private static final Key<Map<SmartPsiElementPointer<PsiMember>, ReportedChange>> REPORTED_CHANGES = Key.create("REPORTED_CHANGES");
  private static final Key<Boolean> FILE_OPENED_KEY = Key.create("FILE_OPENED");

  static @NotNull InlayPresentation getPresentation(@NotNull Project project,
                                                    @NotNull Editor editor,
                                                    @NotNull Document document,
                                                    @NotNull PresentationFactory factory,
                                                    int offset,
                                                    @NotNull PsiElement element,
                                                    @NotNull List<SmartPsiElementPointer<PsiElement>> problems) {
    int line = document.getLineNumber(offset);
    int column = offset - document.getLineStartOffset(line);
    int columnWidth = EditorUtil.getPlainSpaceWidth(editor);
    SpacePresentation spacePresentation = new SpacePresentation(column * columnWidth, 0);
    InlayPresentation textPresentation = factory.smallText(JavaErrorBundle.message("project.problems.broken.usages", problems.size()));
    InlayPresentation presentation = factory.seq(spacePresentation, textPresentation);
    return factory.referenceOnHover(presentation, (e, p) -> {
      List<PsiElement> elements = ContainerUtil.mapNotNull(problems, pointer -> pointer.getElement());
      if (elements.size() == 1) {
        PsiElement problem = elements.get(0);
        if (problem instanceof Navigatable) ((Navigatable)problem).navigate(true);
      }
      else {
        FindUtil.showInUsageView(element, elements.toArray(PsiElement.EMPTY_ARRAY),
                                 JavaErrorBundle.message("project.problems.title"), project);
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

  static int getMemberOffset(@NotNull PsiMember element) {
    return Arrays.stream(element.getChildren())
      .filter(c -> !(c instanceof PsiDocComment) && !(c instanceof PsiWhiteSpace))
      .findFirst().orElse(element)
      .getTextRange().getStartOffset();
  }

  static void removeOldInlays(@NotNull PsiFile psiFile) {
    Map<SmartPsiElementPointer<PsiMember>, ReportedChange> changes = getReportedChanges(psiFile);
    changes.values().forEach(c -> {
      Inlay<?> inlay = c.myInlay;
      if (inlay != null) Disposer.dispose(inlay);
    });
    psiFile.putUserData(FILE_OPENED_KEY, true);
  }

  static @NotNull Map<PsiMember, ScopedMember> mergeWithOldChanges(@NotNull PsiFile psiFile,
                                                                   @NotNull Map<PsiMember, ScopedMember> newChanges) {
    Map<SmartPsiElementPointer<PsiMember>, ReportedChange> oldChanges = Boolean.TRUE.equals(psiFile.getUserData(FILE_OPENED_KEY)) ?
                                                                        getReportedChanges(psiFile) : Collections.emptyMap();
    if (oldChanges.isEmpty()) return newChanges;
    Map<PsiMember, ScopedMember> changes = new HashMap<>(newChanges);
    oldChanges.forEach((memberPointer, reportedChange) -> {
      PsiMember psiMember = memberPointer.getElement();
      if (psiMember != null) changes.putIfAbsent(psiMember, reportedChange.getPrevMember());
    });
    return changes;
  }

  static void reportChanges(@NotNull PsiFile psiFile, @NotNull Map<SmartPsiElementPointer<PsiMember>, ReportedChange> newChanges) {
    Map<SmartPsiElementPointer<PsiMember>, ReportedChange> reportedChanges = new HashMap<>(newChanges);
    Map<SmartPsiElementPointer<PsiMember>, ReportedChange> oldChanges = getReportedChanges(psiFile);
    oldChanges.forEach((memberPointer, oldChange) -> {
      PsiMember member = memberPointer.getElement();
      if (member == null) {
        Inlay<?> inlay = oldChange.myInlay;
        if (inlay != null) Disposer.dispose(inlay);
        return;
      }
      ReportedChange newChange = reportedChanges.putIfAbsent(memberPointer, oldChange);
      // we have new change for this element
      if (newChange != null && newChange != oldChange) {
        Inlay<?> oldInlay = oldChange.myInlay;
        if (oldInlay != null) Disposer.dispose(oldInlay);
        if (newChange.myInlay == null) reportedChanges.remove(memberPointer);
      }
    });
    psiFile.putUserData(REPORTED_CHANGES, reportedChanges);
    psiFile.putUserData(FILE_OPENED_KEY, false);
  }

  public static @NotNull Map<SmartPsiElementPointer<PsiMember>, ReportedChange> getReportedChanges(@NotNull PsiFile psiFile) {
    Map<SmartPsiElementPointer<PsiMember>, ReportedChange> changes = psiFile.getUserData(REPORTED_CHANGES);
    return changes == null ? Collections.emptyMap() : changes;
  }

  public static class ReportedChange {

    private final ScopedMember myPrevMember;
    private final Inlay<?> myInlay;

    ReportedChange(@Nullable ScopedMember prevMember, @Nullable Inlay<?> inlay) {
      myPrevMember = prevMember;
      myInlay = inlay;
    }

    public Inlay<?> getInlay() {
      return myInlay;
    }

    public ScopedMember getPrevMember() {
      return myPrevMember;
    }

    @Override
    public String toString() {
      return "ReportedChange{" +
             "myPrevMember=" + myPrevMember +
             ", myInlay=" + myInlay +
             '}';
    }
  }
}
