package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.hint.api.ParameterInfoHandler;
import com.intellij.codeInsight.hint.api.UpdateParameterInfoContext;
import com.intellij.codeInsight.hint.api.ParameterInfoContext;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.ParameterInfoUtils;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

public class ParameterInfoController {
  private final Project myProject;
  private final Editor myEditor;

  private final String myParameterCloseChars;
  private final RangeMarker myLbraceMarker;
  private final LightweightHint myHint;
  private final ParameterInfoComponent myComponent;

  private final CaretListener myEditorCaretListener;
  private final DocumentListener myEditorDocumentListener;
  private final PropertyChangeListener myLookupListener;
  private final @NotNull ParameterInfoHandler myHandler;

  private final Alarm myAlarm = new Alarm();
  private static final int DELAY = 200;

  private boolean myDisposed = false;

  /**
   * Keeps Vector of ParameterInfoController's in Editor
   */
  private static final Key<ArrayList<ParameterInfoController>> ALL_CONTROLLERS_KEY = Key.create("ParameterInfoController.ALL_CONTROLLERS_KEY");

  public static ParameterInfoController getControllerAtOffset(Editor editor, int offset) {
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
      public Project getProject() {
        return myProject;
      }

      public PsiFile getFile() {
        return myComponent.getParameterOwner().getContainingFile();
      }

      public int getOffset() {
        return myEditor.getCaretModel().getOffset();
      }

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

    for(Object o:objects) {
      final Object[] availableParams = myHandler.getParametersForDocumentation(o, context);

      if (availableParams != null &&
          selectedParameterIndex < availableParams.length
        ) {
        params.add(availableParams[selectedParameterIndex]);
      }
    }

    return params.isEmpty() ? ArrayUtil.EMPTY_OBJECT_ARRAY : params.toArray(new Object[params.size()]);
  }

  private static ArrayList<ParameterInfoController> getAllControllers(Editor editor) {
    ArrayList<ParameterInfoController> array = editor.getUserData(ALL_CONTROLLERS_KEY);
    if (array == null){
      array = new ArrayList<ParameterInfoController>();
      editor.putUserData(ALL_CONTROLLERS_KEY, array);
    }
    return array;
  }

  public static boolean isAlreadyShown(Editor editor, int lbraceOffset) {
    ArrayList<ParameterInfoController> allControllers = getAllControllers(editor);
    for(int i = 0; i < allControllers.size(); i++) {
      ParameterInfoController controller = allControllers.get(i);
      if (!controller.myHint.isVisible()){
        controller.dispose();
        i--;
        continue;
      }
      if (controller.myLbraceMarker.getStartOffset() == lbraceOffset) return true;
    }
    return false;
  }

  public ParameterInfoController(
      Project project,
      Editor editor,
      int lbraceOffset,
      LightweightHint hint,
      @NotNull ParameterInfoHandler handler
      ) {
    myProject = project;
    myEditor = editor;
    myHandler = handler;
    myParameterCloseChars = handler.getParameterCloseChars();
    myLbraceMarker = editor.getDocument().createRangeMarker(lbraceOffset, lbraceOffset);
    myHint = hint;
    myComponent = (ParameterInfoComponent)myHint.getComponent();

    ArrayList<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.add(this);

    myEditorCaretListener = new CaretListener(){
      public void caretPositionChanged(CaretEvent e) {
        if (!myHandler.tracksParameterIndex()) {
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }

        int oldOffset = e.getEditor().logicalPositionToOffset(e.getOldPosition());
        int newOffset = e.getEditor().logicalPositionToOffset(e.getNewPosition());
        if (newOffset <= myLbraceMarker.getStartOffset()){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }
        int offset1 = Math.min(oldOffset, newOffset);
        int offset2 = Math.max(oldOffset, newOffset);
        CharSequence chars = e.getEditor().getDocument().getCharsSequence();
        int offset = CharArrayUtil.shiftForwardUntil(chars, offset1, myParameterCloseChars);
        if (offset < offset2){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
        }
        else{
          if (myAlarm.cancelAllRequests() > 0){
            addAlarmRequest();
          }
        }
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditorDocumentListener = new DocumentAdapter(){
      public void documentChanged(DocumentEvent e) {
        if (!myHandler.tracksParameterIndex()) {
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }

        CharSequence oldS = e.getOldFragment();
        if (CharArrayUtil.shiftForwardUntil(oldS, 0, myParameterCloseChars) < oldS.length()){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }
        CharSequence newS = e.getNewFragment();
        if (CharArrayUtil.shiftForwardUntil(newS, 0, myParameterCloseChars) < newS.length()){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }
        if (myAlarm.cancelAllRequests() > 0){
          addAlarmRequest();
        }
      }
    };
    myEditor.getDocument().addDocumentListener(myEditorDocumentListener);

    myLookupListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName())){
          final Lookup lookup = (Lookup)evt.getNewValue();
          if (lookup != null){
            adjustPositionForLookup(lookup);
          }
        }
      }
    };
    LookupManager.getInstance(project).addPropertyChangeListener(myLookupListener);

    updateComponent();
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

  private void adjustPositionForLookup(Lookup lookup) {
    if (!myHint.isVisible()){
      dispose();
      return;
    }

    HintManager hintManager = HintManager.getInstance();
    short constraint = lookup.isPositionedAboveCaret() ? HintManager.UNDER : HintManager.ABOVE;
    Point p = hintManager.getHintPosition(myHint, myEditor, constraint);
    Dimension hintSize = myHint.getComponent().getPreferredSize();
    JLayeredPane layeredPane = myEditor.getComponent().getRootPane().getLayeredPane();
    p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
    p.x = Math.max(p.x, 0);
    myHint.setBounds(p.x, p.y,hintSize.width,hintSize.height);
  }

  private void addAlarmRequest(){
    Runnable request = new Runnable(){
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

    if (elementForUpdating != null) myHandler.updateParameterInfo(elementForUpdating, context);
    else context.removeHint();

    myComponent.update();
  }

  public static void nextParameter (Editor editor, int lbraceOffset) {
    ArrayList<ParameterInfoController> controllers = getAllControllers(editor);
    for (final ParameterInfoController controller : controllers) {
      if (!controller.myHint.isVisible()) {
        controller.dispose();
        continue;
      }
      if (controller.myLbraceMarker.getStartOffset() == lbraceOffset) {
        controller.prevOrNextParameter(true);
        return;
      }
    }
  }

  public static void prevParameter (Editor editor, int lbraceOffset) {
    ArrayList<ParameterInfoController> controllers = getAllControllers(editor);
    for (ParameterInfoController controller : controllers) {
      if (!controller.myHint.isVisible()) {
        controller.dispose();
        continue;
      }
      if (controller.myLbraceMarker.getStartOffset() == lbraceOffset) {
        controller.prevOrNextParameter(false);
        return;
      }
    }
  }

  private void prevOrNextParameter(boolean isNext) {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    int offset = CharArrayUtil.shiftBackward(chars, myEditor.getCaretModel().getOffset() - 1, " \t") + 1;

    int lbraceOffset = myLbraceMarker.getStartOffset();
    if (lbraceOffset < offset) {
      PsiExpressionList argList = findArgumentList(file, offset, lbraceOffset);
      if (argList != null) {
        int currentParameterIndex = getCurrentParameterIndex(argList, offset, JavaTokenType.COMMA);
        PsiExpression currentParameter = null;
        if (currentParameterIndex > 0 && !isNext) {
          currentParameter = argList.getExpressions()[currentParameterIndex - 1];
        }
        else if (currentParameterIndex < argList.getExpressions().length - 1 && isNext) {
          currentParameter = argList.getExpressions()[currentParameterIndex + 1];
        }

        if (currentParameter != null) {
          offset = currentParameter.getTextRange().getStartOffset();
          myEditor.getCaretModel().moveToOffset(offset);
          myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          myEditor.getSelectionModel().removeSelection();
          MethodParameterInfoHandler.updateMethodInfo(argList, new MyUpdateParameterInfoContext(offset, file));
        }
      }
    }
  }

  public static int getCurrentParameterIndex(PsiElement argList, int offset, IElementType delimiterType) {
    return ParameterInfoUtils.getCurrentParameterIndex(argList.getNode(), offset, delimiterType);
  }

  @Nullable
  public static PsiExpressionList findArgumentList(PsiFile file, int offset, int lbraceOffset){
    return MethodParameterInfoHandler.findArgumentList(file, offset, lbraceOffset);
  }

  private class MyUpdateParameterInfoContext implements UpdateParameterInfoContext {
    private final int myOffset;
    private final PsiFile myFile;

    public MyUpdateParameterInfoContext(final int offset, final PsiFile file) {
      myOffset = offset;
      myFile = file;
    }

    public int getParameterStart() {
      return myLbraceMarker.getStartOffset();
    }

    public int getOffset() {
      return myOffset;
    }

    public PsiFile getFile() {
      return myFile;
    }

    public Editor getEditor() {
      return myEditor;
    }

    public void removeHint() {
      myHint.hide();
      dispose();
    }

    public void setParameterOwner(final PsiElement o) {
      myComponent.setParameterOwner(o);
    }

    public PsiElement getParameterOwner() {
      return myComponent.getParameterOwner();
    }

    public void setHighlightedParameter(final PsiElement method) {
      myComponent.setHighlightedParameter(method);
    }

    public void setCurrentParameter(final int index) {
      myComponent.setCurrentParameterIndex(index);
    }

    public boolean isUIComponentEnabled(int index) {
      return myComponent.isEnabled(index);
    }

    public void setUIComponentEnabled(int index, boolean b) {
      myComponent.setEnabled(index, b);
    }

    public Object[] getObjectsToView() {
      return myComponent.getObjects();
    }
  }
}
