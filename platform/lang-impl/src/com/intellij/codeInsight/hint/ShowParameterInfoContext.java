/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
*/
public class ShowParameterInfoContext implements CreateParameterInfoContext {
  private final Editor myEditor;
  private final PsiFile myFile;
  private final Project myProject;
  private final int myOffset;
  private final int myParameterListStart;
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
    myEditor = editor;
    myProject = project;
    myFile = file;
    myParameterListStart = parameterListStart;
    myOffset = offset;
    myRequestFocus = requestFocus;
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
  @NotNull
  public Editor getEditor() {
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
    showMethodInfo(getProject(), getEditor(), element, getHighlightedElement(), itemsToShow, offset, handler, myRequestFocus);
  }

  private static void showParameterHint(final PsiElement element,
                                        final Editor editor,
                                        final Object[] descriptors,
                                        final Project project,
                                        @Nullable PsiElement highlighted,
                                        final int elementStart,
                                        final ParameterInfoHandler handler,
                                        final boolean requestFocus) {
    if (ParameterInfoController.isAlreadyShown(editor, elementStart)) return;

    if (editor.isDisposed() || !editor.getComponent().isVisible()) return;
    final ParameterInfoComponent component = new ParameterInfoComponent(descriptors, editor,handler,requestFocus);
    component.setParameterOwner(element);
    component.setRequestFocus(requestFocus);
    if (highlighted != null) {
      component.setHighlightedParameter(highlighted);
    }

    component.update(); // to have correct preferred size

    final LightweightHint hint = new LightweightHint(component);
    hint.setSelectingHint(true);
    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final ShowParameterInfoHandler.BestLocationPointProvider provider = new MyBestLocationPointProvider(editor);
    final Pair<Point, Short> pos = provider.getBestPointPosition(hint, element, elementStart, true, HintManager.UNDER);

    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(() -> {
      if (editor.isDisposed() || DumbService.isDumb(project)) return;

      final Document document = editor.getDocument();
      if (document.getTextLength() < elementStart) return;

      HintHint hintHint = HintManagerImpl.createHintHint(editor, pos.getFirst(), hint, pos.getSecond());
      hintHint.setExplicitClose(true);
      hintHint.setRequestFocus(requestFocus);

      Editor editorToShow = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
      // is case of injection we need to calculate position for EditorWindow
      // also we need to show the hint in the main editor because of intention bulb
      hintManager.showEditorHint(hint, editorToShow, pos.getFirst(), HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false, hintHint);
      new ParameterInfoController(project, editor, elementStart, hint, handler, provider);
    });
  }

  private static void showMethodInfo(final Project project, final Editor editor,
                                     final PsiElement list,
                                     PsiElement highlighted,
                                     Object[] candidates,
                                     int offset,
                                     ParameterInfoHandler handler,
                                     boolean requestFocus
                                     ) {
    showParameterHint(list, editor, candidates, project, candidates.length > 1 ? highlighted : null, offset, handler, requestFocus);
  }

  /**
   * @return Point in layered pane coordinate system
   */
  static Pair<Point, Short> chooseBestHintPosition(Project project,
                                                   Editor editor,
                                                   int line,
                                                   int col,
                                                   LightweightHint hint,
                                                   boolean awtTooltip, short preferredPosition) {
    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    Point p1;
    Point p2;
    boolean isLookupShown = LookupManager.getInstance(project).getActiveLookup() != null;
    if (isLookupShown) {
      p1 = hintManager.getHintPosition(hint, editor, HintManager.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, HintManager.ABOVE);
    }
    else {
      LogicalPosition pos = new LogicalPosition(line, col);
      p1 = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.UNDER);
      p2 = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.ABOVE);
    }

    if (!awtTooltip) {
      p1.x = Math.min(p1.x, layeredPane.getWidth() - hintSize.width);
      p1.x = Math.max(p1.x, 0);
      p2.x = Math.min(p2.x, layeredPane.getWidth() - hintSize.width);
      p2.x = Math.max(p2.x, 0);
    }

    boolean p1Ok = p1.y + hintSize.height < layeredPane.getHeight();
    boolean p2Ok = p2.y >= 0;

    if (isLookupShown) {
      if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
      if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
    }
    else {
      if (preferredPosition != HintManager.DEFAULT) {
        if (preferredPosition == HintManager.ABOVE) {
          if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
        } else if (preferredPosition == HintManager.UNDER) {
          if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
        }
      }

      if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
      if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
    }

    int underSpace = layeredPane.getHeight() - p1.y;
    int aboveSpace = p2.y;
    return aboveSpace > underSpace ? new Pair<>(new Point(p2.x, 0), HintManager.UNDER) : new Pair<>(p1,
                                                                                                    HintManager.ABOVE);
  }

  public void setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  public boolean isRequestFocus() {
    return myRequestFocus;
  }

  static class MyBestLocationPointProvider implements ShowParameterInfoHandler.BestLocationPointProvider {
    private final Editor myEditor;
    private int previousOffset = -1;
    private Point previousBestPoint;
    private Short previousBestPosition;

    public MyBestLocationPointProvider(final Editor editor) {
      myEditor = editor;
    }

    @Override
    @NotNull
    public Pair<Point, Short> getBestPointPosition(LightweightHint hint,
                                                   final PsiElement list,
                                                   int offset,
                                                   final boolean awtTooltip,
                                                   short preferredPosition) {
      if (list != null) {
        TextRange range = list.getTextRange();
        if (!range.contains(offset)) {
          offset = range.getStartOffset() + 1;
        }
      }
      if (previousOffset == offset) return Pair.create(previousBestPoint, previousBestPosition);

      final boolean isMultiline = list != null && StringUtil.containsAnyChar(list.getText(), "\n\r");
      final LogicalPosition pos = myEditor.offsetToLogicalPosition(offset);
      Pair<Point, Short> position;

      if (!isMultiline) {
        position = chooseBestHintPosition(myEditor.getProject(), myEditor, pos.line, pos.column, hint, awtTooltip, preferredPosition);
      }
      else {
        Point p = HintManagerImpl.getHintPosition(hint, myEditor, pos, HintManager.ABOVE);
        position = new Pair<>(p, HintManager.ABOVE);
      }
      previousBestPoint = position.getFirst();
      previousBestPosition = position.getSecond();
      previousOffset = offset;
      return position;
    }
  }
}
