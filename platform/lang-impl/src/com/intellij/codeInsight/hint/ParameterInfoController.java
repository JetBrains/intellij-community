/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
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

public class ParameterInfoController implements Disposable {
  private final Project myProject;
  @NotNull private final Editor myEditor;

  private final RangeMarker myLbraceMarker;
  private final LightweightHint myHint;
  private final ParameterInfoComponent myComponent;

  private final CaretListener myEditorCaretListener;
  @NotNull private final ParameterInfoHandler<Object, Object> myHandler;
  private final ShowParameterInfoHandler.BestLocationPointProvider myProvider;

  private final Alarm myAlarm = new Alarm();
  private static final int DELAY = 200;

  private boolean myDisposed = false;

  /**
   * Keeps Vector of ParameterInfoController's in Editor
   */
  private static final Key<List<ParameterInfoController>> ALL_CONTROLLERS_KEY = Key.create("ParameterInfoController.ALL_CONTROLLERS_KEY");

  public static ParameterInfoController findControllerAtOffset(Editor editor, int offset) {
    List<ParameterInfoController> allControllers = getAllControllers(editor);
    for (int i = 0; i < allControllers.size(); ++i) {
      ParameterInfoController controller = allControllers.get(i);

      if (controller.myLbraceMarker.getStartOffset() == offset) {
        if (controller.myHint.isVisible()) return controller;
        Disposer.dispose(controller);
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

  private static List<ParameterInfoController> getAllControllers(@NotNull Editor editor) {
    List<ParameterInfoController> array = editor.getUserData(ALL_CONTROLLERS_KEY);
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
    myLbraceMarker = editor.getDocument().createRangeMarker(lbraceOffset, lbraceOffset);
    myHint = hint;
    myComponent = (ParameterInfoComponent)myHint.getComponent();

    List<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.add(this);

    myEditorCaretListener = new CaretAdapter(){
      @Override
      public void caretPositionChanged(CaretEvent e) {
        myAlarm.cancelAllRequests();
        addAlarmRequest();
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        myAlarm.cancelAllRequests();
        addAlarmRequest();
      }
    }, this);

    PropertyChangeListener lookupListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName())) {
          final LookupImpl lookup = (LookupImpl)evt.getNewValue();
          if (lookup != null && lookup.isShown()) {
            adjustPositionForLookup(lookup);
          }
        }
      }
    };
    LookupManager.getInstance(project).addPropertyChangeListener(lookupListener, this);

    updateComponent();
    if (myEditor instanceof EditorImpl) {
      Disposer.register(((EditorImpl)myEditor).getDisposable(), this);
    }
  }

  @Override
  public void dispose(){
    if (myDisposed) return;
    myDisposed = true;

    List<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.remove(this);
    myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
  }

  private void adjustPositionForLookup(@NotNull Lookup lookup) {
    if (!myHint.isVisible() || myEditor.isDisposed()) {
      Disposer.dispose(this);
      return;
    }

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    short constraint = lookup.isPositionedAboveCaret() ? HintManager.UNDER : HintManager.ABOVE;
    Point p = hintManager.getHintPosition(myHint, myEditor, constraint);
    //Dimension hintSize = myHint.getComponent().getPreferredSize();
    //JLayeredPane layeredPane = myEditor.getComponent().getRootPane().getLayeredPane();
    //p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
    //p.x = Math.max(p.x, 0);
    myHint.pack();
    myHint.updateLocation(p.x, p.y);
  }

  private void addAlarmRequest(){
    Runnable request = new Runnable(){
      @Override
      public void run(){
        if (!myDisposed && !myProject.isDisposed()) {
          DumbService.getInstance(myProject).withAlternativeResolveEnabled(new Runnable() {
            @Override
            public void run() {
              updateComponent();
            }
          });
        }
      }
    };
    myAlarm.addRequest(request, DELAY, ModalityState.stateForComponent(myEditor.getComponent()));
  }

  private void updateComponent(){
    if (!myHint.isVisible()){
      Disposer.dispose(this);
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
      if (!myDisposed && myHint.isVisible() && myEditor.getComponent().getRootPane() != null) {
        myComponent.update();
        Pair<Point,Short> pos = myProvider.getBestPointPosition(myHint, (PsiElement)elementForUpdating, offset, true, HintManager.UNDER);
        HintManagerImpl.adjustEditorHintPosition(myHint, myEditor, pos.getFirst(), pos.getSecond());
      }
    }
    else {
      context.removeHint();
    }
  }

  public static boolean hasPrevOrNextParameter(Editor editor, int lbraceOffset, boolean isNext) {
    ParameterInfoController controller = findControllerAtOffset(editor, lbraceOffset);
    return controller != null && controller.getPrevOrNextParameterOffset(isNext) != -1;
  }

  public static void prevOrNextParameter(Editor editor, int lbraceOffset, boolean isNext) {
    ParameterInfoController controller = findControllerAtOffset(editor, lbraceOffset);
    int newOffset = controller != null ? controller.getPrevOrNextParameterOffset(isNext) : -1;
    if (newOffset != -1) {
      controller.moveToParameterAtOffset(newOffset);
    }
  }

  private void moveToParameterAtOffset(int offset) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    PsiElement argsList = findArgumentList(file, offset, -1);
    if (argsList == null) return;

    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
    myHandler.updateParameterInfo(argsList, new MyUpdateParameterInfoContext(offset, file));
  }

  private int getPrevOrNextParameterOffset(boolean isNext) {
    if (!(myHandler instanceof ParameterInfoHandlerWithTabActionSupport)) return -1;

    int offset = CharArrayUtil.shiftBackward(myEditor.getDocument().getCharsSequence(), myEditor.getCaretModel().getOffset() - 1, " \t") + 1;
    int lbraceOffset = myLbraceMarker.getStartOffset();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    PsiElement argList = lbraceOffset < offset ? findArgumentList(file, offset, lbraceOffset) : null;
    if (argList == null) return -1;

    ParameterInfoHandlerWithTabActionSupport handler = (ParameterInfoHandlerWithTabActionSupport)myHandler;
    int currentParameterIndex = ParameterInfoUtils.getCurrentParameterIndex(argList.getNode(), offset, handler.getActualParameterDelimiterType());
    if (currentParameterIndex == -1) return -1;

    @SuppressWarnings("unchecked") PsiElement[] parameters = handler.getActualParameters(argList);
    int prevOrNextParameterIndex = isNext && currentParameterIndex < parameters.length - 1 ? currentParameterIndex + 1 :
                                   !isNext && currentParameterIndex > 0 ? currentParameterIndex - 1 : -1;
    return prevOrNextParameterIndex != -1 ? parameters[prevOrNextParameterIndex].getTextRange().getStartOffset() : -1;
  }

  @Nullable
  public static <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset){
    if (file == null) return null;
    ParameterInfoHandler[] handlers = ShowParameterInfoHandler.getHandlers(file.getProject(), PsiUtilCore.getLanguageAtOffset(file, offset), file.getViewProvider().getBaseLanguage());

    if (handlers != null) {
      for(ParameterInfoHandler handler:handlers) {
        if (handler instanceof ParameterInfoHandlerWithTabActionSupport) {
          final ParameterInfoHandlerWithTabActionSupport parameterInfoHandler2 = (ParameterInfoHandlerWithTabActionSupport)handler;

          // please don't remove typecast in the following line; it's required to compile the code under old JDK 6 versions
          final E e = (E) ParameterInfoUtils.findArgumentList(file, offset, lbraceOffset, parameterInfoHandler2);
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
      Disposer.dispose(ParameterInfoController.this);
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
    public void setUIComponentEnabled(int index, boolean enabled) {
      myComponent.setEnabled(index, enabled);
    }

    @Override
    public Object[] getObjectsToView() {
      return myComponent.getObjects();
    }

  }
}
