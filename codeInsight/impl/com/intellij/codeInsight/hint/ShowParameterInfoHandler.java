package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.Language;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ShowParameterInfoHandler implements CodeInsightActionHandler {
  public void invoke(Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, -1, null);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file, int lbraceOffset,
                     PsiElement highlightedElement) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int offset = editor.getCaretModel().getOffset();
    final PsiElement psiElement = file.findElementAt(offset);
    if (psiElement == null) return;

    final MyShowParameterInfoContext context = new MyShowParameterInfoContext(
      editor,
      project,
      file,
      offset,
      lbraceOffset
    );

    context.setHighlightedElement(highlightedElement);

    final Language language = psiElement.getLanguage();
    ParameterInfoHandler[] handlers = language.getParameterInfoHandlers();
    if (handlers == null) handlers = new ParameterInfoHandler[0];

    Lookup lookup = LookupManager.getInstance(project).getActiveLookup();

    if (lookup != null) {
      LookupItem item = lookup.getCurrentItem();

      if (item != null) {
        for(ParameterInfoHandler handler:handlers) {
          if (handler.couldShowInLookup()) {
            final Object[] items = handler.getParametersForLookup(item, context);
            if (items != null && items.length > 0) showLookupEditorHint(items, editor, project,handler);
            return;
          }
        }
      }
      return;
    }

    for(ParameterInfoHandler handler:handlers) {
      Object element = handler.findElementForParameterInfo(context);
      if (element != null) {
        handler.showParameterInfo(element,context);
      }
    }
  }

  private static void showLookupEditorHint(Object[] descriptors, final Editor editor, final Project project, ParameterInfoHandler handler) {
    ParameterInfoComponent component = new ParameterInfoComponent(descriptors, editor, handler);
    component.update();

    final LightweightHint hint = new LightweightHint(component);
    hint.setSelectingHint(true);
    final HintManager hintManager = HintManager.getInstance();
    final Point p = chooseBestHintPosition(project, editor, -1, -1, hint);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!editor.getComponent().isShowing()) return;
        hintManager.showEditorHint(hint, editor, p,
                                   HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE | HintManager.UPDATE_BY_SCROLLING,
                                   0, false);
      }
    });
  }

  interface BestLocationPointProvider {
    Point getBestPointPosition(LightweightHint hint);
  }

  private static void showParameterHint(final PsiElement element, final Editor editor, final Object[] descriptors,
                                        final Project project, BestLocationPointProvider provider,
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
    final HintManager hintManager = HintManager.getInstance();
    final Point p = provider.getBestPointPosition(hint);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        hintManager.showEditorHint(hint, editor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false);
        new ParameterInfoController(project,
                                    editor,
                                    elementStart,
                                    hint,
                                    handler
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
    showParameterHint(list, editor, candidates, project, new MyBestLocationPointProvider(list, editor, project,offset),
                      candidates.length > 1 ? highlighted: null,offset, handler);
  }

  /**
   * @return Point in layered pane coordinate system
   */
  private static Point chooseBestHintPosition(Project project, Editor editor, int line, int col, LightweightHint hint) {
    HintManager hintManager = HintManager.getInstance();
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
      p1 = hintManager.getHintPosition(hint, editor, pos, HintManager.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, pos, HintManager.ABOVE);
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

  private static class MyShowParameterInfoContext implements CreateParameterInfoContext {
    private final Editor myEditor;
    private final PsiFile myFile;
    private final Project myProject;
    private final int myOffset;
    private final int myParameterListStart;
    private PsiElement myHighlightedElement;
    private Object[] myItems;

    public MyShowParameterInfoContext(final Editor editor, final Project project,
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
  }

  private static class MyBestLocationPointProvider implements BestLocationPointProvider {
    private final PsiElement myList;
    private final Editor myEditor;
    private final Project myProject;
    private final int myStartOffset;

    public MyBestLocationPointProvider(final PsiElement list, final Editor editor, final Project project, int offset) {
      myList = list;
      myEditor = editor;
      myProject = project;
      final TextRange textRange = myList.getTextRange();
      myStartOffset = textRange.contains(offset) ? offset:textRange.getStartOffset() + 1;
    }

    public Point getBestPointPosition(LightweightHint hint) {
      String listText = myList.getText();
      final boolean isMultiline = listText.indexOf('\n') >= 0 || listText.indexOf('\r') >= 0;
      final LogicalPosition pos = myEditor.offsetToLogicalPosition(myStartOffset);
      Point p;

      if (!isMultiline) {
        p = chooseBestHintPosition(myProject, myEditor, pos.line, pos.column, hint);
      }
      else {
        p = HintManager.getInstance().getHintPosition(hint, myEditor, pos, HintManager.ABOVE);
        Dimension hintSize = hint.getComponent().getPreferredSize();
        JComponent editorComponent = myEditor.getComponent();
        JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
        p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
        p.x = Math.max(p.x, 0);
      }
      return p;
    }
  }
}

