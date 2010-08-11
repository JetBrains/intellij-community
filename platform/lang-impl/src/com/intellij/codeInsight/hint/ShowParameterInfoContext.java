/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LightweightHint;
import com.intellij.codeInsight.lookup.LookupManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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

  public ShowParameterInfoContext(final Editor editor, final Project project,
                                    final PsiFile file, int offset, int parameterListStart) {
    myEditor = editor;
    myProject = project;
    myFile = file;
    myParameterListStart = parameterListStart;
    myOffset = offset;
  }

  public Project getProject() {
    return myProject;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public int getOffset() {
    return myOffset;
  }

  public int getParameterListStart() {
    return myParameterListStart;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public PsiElement getHighlightedElement() {
    return myHighlightedElement;
  }

  public void setHighlightedElement(PsiElement element) {
    myHighlightedElement = element;
  }

  public void setItemsToShow(Object[] items) {
    myItems = items;
  }

  public Object[] getItemsToShow() {
    return myItems;
  }

  public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) {
    final Object[] itemsToShow = getItemsToShow();
    if (itemsToShow == null || itemsToShow.length == 0) return;
    showMethodInfo(
      getProject(),
      getEditor(),
      element,
      getHighlightedElement(),
      itemsToShow,
      offset,
      handler
    );
  }

  private static void showParameterHint(final PsiElement element, final Editor editor, final Object[] descriptors,
                                        final Project project, final ShowParameterInfoHandler.BestLocationPointProvider provider,
                                        @Nullable PsiElement highlighted,
                                        final int elementStart, final ParameterInfoHandler handler
                                        ) {
    if (ParameterInfoController.isAlreadyShown(editor, elementStart)) return;

    if (editor.isDisposed()) return;
    final ParameterInfoComponent component = new ParameterInfoComponent(descriptors, editor,handler);
    component.setParameterOwner(element);
    if (highlighted != null) {
      component.setHighlightedParameter(highlighted);
    }

    component.update(); // to have correct preferred size

    final LightweightHint hint = new LightweightHint(component);
    hint.setSelectingHint(true);
    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final Point p = provider.getBestPointPosition(hint, element, elementStart);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        hintManager.showEditorHint(hint, editor, p, HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.UPDATE_BY_SCROLLING | HintManagerImpl.HIDE_BY_OTHER_HINT, 0, false);
        new ParameterInfoController(project,
                                    editor,
                                    elementStart,
                                    hint,
                                    handler,
                                    provider
                                    );
      }
    });
  }

  private static void showMethodInfo(final Project project, final Editor editor,
                                     final PsiElement list,
                                     PsiElement highlighted,
                                     Object[] candidates,
                                     int offset,
                                     ParameterInfoHandler handler
                                     ) {
    showParameterHint(list, editor, candidates, project, new MyBestLocationPointProvider(editor),
                      candidates.length > 1 ? highlighted: null,offset, handler);
  }

  /**
   * @return Point in layered pane coordinate system
   */
  static Point chooseBestHintPosition(Project project, Editor editor, int line, int col, LightweightHint hint) {
    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    Point p1;
    Point p2;
    boolean isLookupShown = LookupManager.getInstance(project).getActiveLookup() != null;
    if (isLookupShown) {
      p1 = hintManager.getHintPosition(hint, editor, HintManagerImpl.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, HintManagerImpl.ABOVE);
    }
    else {
      LogicalPosition pos = new LogicalPosition(line, col);
      p1 = hintManager.getHintPosition(hint, editor, pos, HintManagerImpl.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, pos, HintManagerImpl.ABOVE);
    }

    p1.x = Math.min(p1.x, layeredPane.getWidth() - hintSize.width);
    p1.x = Math.max(p1.x, 0);
    p2.x = Math.min(p2.x, layeredPane.getWidth() - hintSize.width);
    p2.x = Math.max(p2.x, 0);
    boolean p1Ok = p1.y + hintSize.height < layeredPane.getHeight();
    boolean p2Ok = p2.y >= 0;

    if (isLookupShown) {
      if (p2Ok) return p2;
      if (p1Ok) return p1;
    }
    else {
      if (p1Ok) return p1;
      if (p2Ok) return p2;
    }

    int underSpace = layeredPane.getHeight() - p1.y;
    int aboveSpace = p2.y;
    return aboveSpace > underSpace ? new Point(p2.x, 0) : p1;
  }

  static class MyBestLocationPointProvider implements ShowParameterInfoHandler.BestLocationPointProvider {
    private final Editor myEditor;
    private int previousOffset = -1;
    private Point previousBestPoint;

    public MyBestLocationPointProvider(final Editor editor) {
      myEditor = editor;
    }

    @NotNull
    public Point getBestPointPosition(LightweightHint hint, final PsiElement list, int offset) {
      final TextRange textRange = list.getTextRange();
      offset = textRange.contains(offset) ? offset:textRange.getStartOffset() + 1;
      if (previousOffset == offset) return previousBestPoint;

      String listText = list.getText();
      final boolean isMultiline = listText.indexOf('\n') >= 0 || listText.indexOf('\r') >= 0;
      final LogicalPosition pos = myEditor.offsetToLogicalPosition(offset);
      Point p;

      if (!isMultiline) {
        p = chooseBestHintPosition(myEditor.getProject(), myEditor, pos.line, pos.column, hint);
      }
      else {
        p = HintManagerImpl.getHintPosition(hint, myEditor, pos, HintManagerImpl.ABOVE);
        Dimension hintSize = hint.getComponent().getPreferredSize();
        JComponent editorComponent = myEditor.getComponent();
        JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
        p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
        p.x = Math.max(p.x, 0);
      }
      previousBestPoint = p;
      previousOffset = offset;
      return p;
    }
  }
}
