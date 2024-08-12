// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.IdentifierUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CopyReferenceUtil extends FqnUtil {
  static void highlight(Editor editor, Project project, List<? extends PsiElement> elements) {
    if (project == null) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    if (elements.size() == 1 && editor != null) {
      PsiElement element = elements.get(0);
      PsiElement nameIdentifier = IdentifierUtil.getNameIdentifier(element);
      if (nameIdentifier != null) {
        highlightManager
          .addOccurrenceHighlights(editor, new PsiElement[]{nameIdentifier}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      }
      else {
        PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
        if (reference != null) {
          highlightManager
            .addOccurrenceHighlights(editor, new PsiReference[]{reference}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        }
        else if (element != PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.getDocument())) {
          highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{element}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        }
      }
    }
  }

  static @NotNull List<PsiElement> getElementsToCopy(final @Nullable Editor editor, final DataContext dataContext) {
    List<PsiElement> elements = new ArrayList<>();
    if (editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor);
      if (reference != null) {
        ContainerUtil.addIfNotNull(elements, reference.getElement());
      }
    }

    if (elements.isEmpty()) {
      PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (psiElements != null) {
        Collections.addAll(elements, psiElements);
      }
    }

    if (elements.isEmpty()) {
      ContainerUtil.addIfNotNull(elements, CommonDataKeys.PSI_ELEMENT.getData(dataContext));
    }

    if (elements.isEmpty() && editor == null) {
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (project != null && files != null) {
        for (VirtualFile file : files) {
          ContainerUtil.addIfNotNull(elements, PsiManager.getInstance(project).findFile(file));
        }
      }
    }

    return ContainerUtil.mapNotNull(elements, element -> element instanceof PsiFile && !((PsiFile)element).getViewProvider().isPhysical()
                                                         ? null
                                                         : adjustElement(element));
  }

  static PsiElement adjustElement(PsiElement element) {
    PsiElement adjustedElement = QualifiedNameProviderUtil.adjustElementToCopy(element);
    return adjustedElement != null ? adjustedElement : element;
  }

  static void setStatusBarText(Project project, @NlsContexts.StatusBarText String message) {
    if (project != null) {
      final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        statusBar.setInfo(message);
      }
    }
  }

  static String doCopy(List<? extends PsiElement> elements, @Nullable Editor editor) {
    if (elements.isEmpty()) return null;

    List<String> fqns = new ArrayList<>();
    for (PsiElement element : elements) {
      String fqn = elementToFqn(element, editor);
      if (fqn == null) return null;

      fqns.add(fqn);
    }

    return StringUtil.join(fqns, "\n");
  }
}
