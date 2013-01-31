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

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

public class ParameterInfoController {
  private final Project myProject;
  @NotNull private final Editor myEditor;

  private final String myParameterCloseChars;
  private final RangeMarker myLbraceMarker;
  private final LightweightHint myHint;
  private final ParameterInfoComponent myComponent;

  private final CaretListener myEditorCaretListener;
  private final DocumentListener myEditorDocumentListener;
  private final PropertyChangeListener myLookupListener;
  @NotNull private final ParameterInfoHandler<Object, Object> myHandler;
  private final ShowParameterInfoHandler.BestLocationPointProvider myProvider;

  private final Alarm myAlarm = new Alarm();
  private static final int DELAY = 200;

  private boolean myDisposed = false;

  /**
   * Keeps Vector of ParameterInfoController's in Editor
   */
  private static final Key<ArrayList<ParameterInfoController>> ALL_CONTROLLERS_KEY = Key.create("ParameterInfoController.ALL_CONTROLLERS_KEY");

  public static ParameterInfoController findControllerAtOffset(Editor editor, int offset) {
    ArrayList<ParameterInfoController> allControllers = getAllControllers(editor);
    for (int i = 0; i < allControllers.size(); ++i) {
      ParameterInfoController controller = allControllers.get(i);

      if (controller.myLbraceMarker.getStartOffset() == offset) {
        if (controller.myHint.isVisible()) return controller;
        controller.dispose();
        --i;
      }
    }

    return null;
  }

  public Object[] getSelectedElements() {
    ParameterInfoContext context = new ParameterInfoContext() {
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public PsiFile getFile() {
        return myComponent.getParameterOwner().getContainingFile();
      }

      @Override
      public int getOffset() {
        return myEditor.getCaretModel().getOffset();
      }

      @Override
      @NotNull
      public Editor getEditor() {
        return myEditor;
      }
    };

    if (!myHandler.tracksParameterIndex()) {
      return myHandler.getParametersForDocumentation(myComponent.getObjects()[0],context);
    }

    final Object[] objects = myComponent.getObjects();
    int selectedParameterIndex = myComponent.getCurrentParameterIndex();
    List<Object> params = new ArrayList<Object>(objects.length);

    final Object highlighted = myComponent.getHighlighted();
    for(Object o:objects) {
      if (highlighted != null && !o.equals(highlighted)) continue;
      collectParams(context, selectedParameterIndex, params, o);
    }

    //choose anything when highlighted is not applicable
    if (highlighted != null && params.isEmpty()) {
      for (Object o : objects) {
        collectParams(context, selectedParameterIndex, params, o);
      }
    }

    return ArrayUtil.toObjectArray(params);
  }

  private void collectParams(ParameterInfoContext context, int selectedParameterIndex, List<Object> params, Object o) {
    final Object[] availableParams = myHandler.getParametersForDocumentation(o, context);

    if (availableParams != null &&
        selectedParameterIndex < availableParams.length &&
        selectedParameterIndex >= 0
      ) {
      params.add(availableParams[selectedParameterIndex]);
    }
  }

  private static ArrayList<ParameterInfoController> getAllControllers(@NotNull Editor editor) {
    ArrayList<ParameterInfoController> array = editor.getUserData(ALL_CONTROLLERS_KEY);
    if (array == null){
      array = new ArrayList<ParameterInfoController>();
      editor.putUserData(ALL_CONTROLLERS_KEY, array);
    }
    return array;
  }

  public static boolean isAlreadyShown(Editor editor, int lbraceOffset) {
    return findControllerAtOffset(editor, lbraceOffset) != null;
  }

  public ParameterInfoController(@NotNull Project project,
                                 @NotNull Editor editor,
                                 int lbraceOffset,
                                 @NotNull LightweightHint hint,
                                 @NotNull ParameterInfoHandler handler,
                                 @NotNull ShowParameterInfoHandler.BestLocationPointProvider provider) {
    myProject = project;
    myEditor = editor;
    myHandler = handler;
    myProvider = provider;
    myParameterCloseChars = handler.getParameterCloseChars();
    myLbraceMarker = editor.getDocument().createRangeMarker(lbraceOffset, lbraceOffset);
    myHint = hint;
    myComponent = (ParameterInfoComponent)myHint.getComponent();

    ArrayList<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.add(this);

    myEditorCaretListener = new CaretListener(){
      @Override
      public void caretPositionChanged(CaretEvent e) {
        myAlarm.cancelAllRequests();
        addAlarmRequest();
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditorDocumentListener = new DocumentAdapter(){
      @Override
      public void documentChanged(DocumentEvent e) {
        myAlarm.cancelAllRequests();
        addAlarmRequest();
      }
    };
    myEditor.getDocument().addDocumentListener(myEditorDocumentListener);

    myLookupListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName())){
          final LookupImpl lookup = (LookupImpl)evt.getNewValue();
          if (lookup != null && lookup.isShown()){
            adjustPositionForLookup(lookup);
          }
        }
      }
    };
    LookupManager.getInstance(project).addPropertyChangeListener(myLookupListener);

    updateComponent();
    if (myEditor instanceof EditorImpl) {
      Disposer.register(((EditorImpl)myEditor).getDisposable(), new Disposable() {
        @Override
        public void dispose() {
          ParameterInfoController.this.dispose();
        }
      });
    }
  }

  private void dispose(){
    if (myDisposed) return;
    myDisposed = true;

    ArrayList<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.remove(this);
    myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
    myEditor.getDocument().removeDocumentListener(myEditorDocumentListener);
    LookupManager.getInstance(myProject).removePropertyChangeListener(myLookupListener);
  }

  private void adjustPositionForLookup(@NotNull Lookup lookup) {
    if (!myHint.isVisible() || myEditor.isDisposed()) {
      dispose();
      return;
    }

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    short constraint = lookup.isPositionedAboveCaret() ? HintManager.UNDER : HintManager.ABOVE;
    Point p = hintManager.getHintPosition(myHint, myEditor, constraint);
    //Dimension hintSize = myHint.getComponent().getPreferredSize();
    //JLayeredPane layeredPane = myEditor.getComponent().getRootPane().getLayeredPane();
    //p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
    //p.x = Math.max(p.x, 0);
    myHint.updateBounds(p.x, p.y);
  }

  private void addAlarmRequest(){
    Runnable request = new Runnable(){
      @Override
      public void run(){
        if (!myDisposed && !myProject.isDisposed()) updateComponent();
      }
    };
    myAlarm.addRequest(request, DELAY, ModalityState.stateForComponent(myEditor.getComponent()));
  }

  private void updateComponent(){
    if (!myHint.isVisible()){
      dispose();
      return;
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    final int offset = CharArrayUtil.shiftBackward(chars, myEditor.getCaretModel().getOffset() - 1, " \t") + 1;

    final UpdateParameterInfoContext context = new MyUpdateParameterInfoContext(offset, file);
    final Object elementForUpdating = myHandler.findElementForUpdatingParameterInfo(context);

    if (elementForUpdating != null) {
      myHandler.updateParameterInfo(elementForUpdating, context);
      if (myHint.isVisible() && myEditor.getComponent().getRootPane() != null) {
        myComponent.update();
        Pair<Point,Short> pos = myProvider.getBestPointPosition(myHint, (PsiElement)elementForUpdating, offset, true, HintManager.UNDER);
        HintManagerImpl.adjustEditorHintPosition(myHint, myEditor, pos.getFirst(), pos.getSecond());
      }
    }
    else {
      context.removeHint();
    }
  }

  public static void nextParameter (Editor editor, int lbraceOffset) {
    final ParameterInfoController controller = findControllerAtOffset(editor, lbraceOffset);
    if (controller != null) controller.prevOrNextParameter(true, (ParameterInfoHandlerWithTabActionSupport)controller.myHandler);
  }

  public static void prevParameter (Editor editor, int lbraceOffset) {
    final ParameterInfoController parameterInfoController = findControllerAtOffset(editor, lbraceOffset);
    if (parameterInfoController != null) parameterInfoController.prevOrNextParameter(false, (ParameterInfoHandlerWithTabActionSupport)parameterInfoController.myHandler);
  }

  private void prevOrNextParameter(boolean isNext, ParameterInfoHandlerWithTabActionSupport handler) {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    int offset = CharArrayUtil.shiftBackward(chars, myEditor.getCaretModel().getOffset() - 1, " \t") + 1;

    int lbraceOffset = myLbraceMarker.getStartOffset();
    if (lbraceOffset < offset) {
      PsiElement argList = findArgumentList(file, offset, lbraceOffset);

      if (argList != null) {
        int currentParameterIndex = ParameterInfoUtils.getCurrentParameterIndex(argList.getNode(), offset, handler.getActualParameterDelimiterType());
        PsiElement currentParameter = null;
        if (currentParameterIndex > 0 && !isNext) {
          currentParameter = handler.getActualParameters(argList)[currentParameterIndex - 1];
        }
        else if (currentParameterIndex < handler.getActualParameters(argList).length - 1 && isNext) {
          currentParameter = handler.getActualParameters(argList)[currentParameterIndex + 1];
        }

        if (currentParameter != null) {
          offset = currentParameter.getTextRange().getStartOffset();
          myEditor.getCaretModel().moveToOffset(offset);
          myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          myEditor.getSelectionModel().removeSelection();
          handler.updateParameterInfo(argList, new MyUpdateParameterInfoContext(offset, file));
        }
      }
    }
  }

  @Nullable
  public static <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset){
    if (file == null) return null;
    ParameterInfoHandler[] handlers = ShowParameterInfoHandler.getHandlers(file.getProject(), PsiUtilBase.getLanguageAtOffset(file, offset), file.getViewProvider().getBaseLanguage());

    if (handlers != null) {
      for(ParameterInfoHandler handler:handlers) {
        if (handler instanceof ParameterInfoHandlerWithTabActionSupport) {
          final ParameterInfoHandlerWithTabActionSupport parameterInfoHandler2 = (ParameterInfoHandlerWithTabActionSupport)handler;

          final E e = (E)ParameterInfoUtils.findArgumentList(file, offset, lbraceOffset, parameterInfoHandler2);
          if (e != null) return e;
        }
      }
    }

    return null;
  }

  private class MyUpdateParameterInfoContext implements UpdateParameterInfoContext {
    private final int myOffset;
    private final PsiFile myFile;

    public MyUpdateParameterInfoContext(final int offset, final PsiFile file) {
      myOffset = offset;
      myFile = file;
    }

    @Override
    public int getParameterListStart() {
      return myLbraceMarker.getStartOffset();
    }

    @Override
    public int getOffset() {
      return myOffset;
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
    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    @Override
    public void removeHint() {
      myHint.hide();
      dispose();
    }

    @Override
    public void setParameterOwner(final PsiElement o) {
      myComponent.setParameterOwner(o);
    }

    @Override
    public PsiElement getParameterOwner() {
      return myComponent.getParameterOwner();
    }

    @Override
    public void setHighlightedParameter(final Object method) {
      myComponent.setHighlightedParameter(method);
    }

    @Override
    public void setCurrentParameter(final int index) {
      myComponent.setCurrentParameterIndex(index);
    }

    @Override
    public boolean isUIComponentEnabled(int index) {
      return myComponent.isEnabled(index);
    }

    @Override
    public void setUIComponentEnabled(int index, boolean b) {
      myComponent.setEnabled(index, b);
    }

    @Override
    public Object[] getObjectsToView() {
      return myComponent.getObjects();
    }

  }
}
