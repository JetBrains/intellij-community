// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowParameterInfoContext implements CreateParameterInfoContext {
  private final Editor myEditor;
  private final PsiFile myFile;
  private final Project myProject;
  private final int myOffset;
  private final int myParameterListStart;
  private final boolean mySingleParameterInfo;
  private PsiElement myHighlightedElement;
  private Object[] myItems;
  private boolean myRequestFocus;

  public ShowParameterInfoContext(final Editor editor, final Project project,
                                  final PsiFile file, int offset, int parameterListStart) {
    this(editor, project, file, offset, parameterListStart, false);
  }

  public ShowParameterInfoContext(final Editor editor, final Project project,
                                  final PsiFile file, int offset, int parameterListStart,
                                  boolean requestFocus) {
    this(editor, project, file, offset, parameterListStart, requestFocus, false);
  }

  public ShowParameterInfoContext(final Editor editor, final Project project,
                                  final PsiFile file, int offset, int parameterListStart,
                                  boolean requestFocus, boolean singleParameterInfo) {
    myEditor = editor;
    myProject = project;
    myFile = file;
    myParameterListStart = parameterListStart;
    myOffset = offset;
    myRequestFocus = requestFocus;
    mySingleParameterInfo = singleParameterInfo;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public PsiFile getFile() {
    return myFile;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getParameterListStart() {
    return myParameterListStart;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Override
  public PsiElement getHighlightedElement() {
    return myHighlightedElement;
  }

  @Override
  public void setHighlightedElement(PsiElement element) {
    myHighlightedElement = element;
  }

  @Override
  public void setItemsToShow(Object[] items) {
    myItems = items;
  }

  @Override
  public Object[] getItemsToShow() {
    return myItems;
  }

  @Override
  public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) {
    final Object[] itemsToShow = getItemsToShow();
    if (itemsToShow == null || itemsToShow.length == 0) return;
    showParameterHint(element, getEditor(), itemsToShow, getProject(), itemsToShow.length > 1 ? getHighlightedElement() : null, offset,
                      handler, myRequestFocus, mySingleParameterInfo);
  }

  private static void showParameterHint(final PsiElement element,
                                        final Editor editor,
                                        final Object[] descriptors,
                                        final Project project,
                                        @Nullable PsiElement highlighted,
                                        final int elementStart,
                                        final ParameterInfoHandler<?, ?> handler,
                                        final boolean requestFocus,
                                        boolean singleParameterInfo) {
    if (editor.isDisposed() || !editor.getComponent().isVisible()) return;

    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(() -> {
      if (editor.isDisposed() || !element.isValid() ||
          (!ApplicationManager.getApplication().isUnitTestMode() &&
           !UIUtil.isShowing(editor.getContentComponent()))) return;

      final Document document = editor.getDocument();
      if (document.getTextLength() < elementStart) return;

      ParameterInfoControllerBase controller = ParameterInfoControllerBase.findControllerAtOffset(editor, elementStart);
      if (controller == null) {
        ParameterInfoControllerBase.createParameterInfoController(
          project, editor, elementStart, descriptors, highlighted, element, handler, true, requestFocus);
      }
      else {
        controller.setDescriptors(descriptors);
        controller.showHint(requestFocus, singleParameterInfo);
      }
    });
  }

  public void setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  public boolean isRequestFocus() {
    return myRequestFocus;
  }
}
