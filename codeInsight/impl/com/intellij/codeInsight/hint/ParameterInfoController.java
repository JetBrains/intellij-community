package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
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
  private ShowParameterInfoHandler.BestLocationPointProvider myProvider;

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
          selectedParameterIndex < availableParams.length &&
          selectedParameterIndex >= 0
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
    return findControllerAtOffset(editor, lbraceOffset) != null;
  }

  public ParameterInfoController(Project project, Editor editor, int lbraceOffset, LightweightHint hint, @NotNull ParameterInfoHandler handler,
                                 final ShowParameterInfoHandler.BestLocationPointProvider provider) {
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

    if (elementForUpdating != null) {
      myHandler.updateParameterInfo(elementForUpdating, context);
      if (myHint.isVisible()) {
        HintManager.getInstance().adjustEditorHintPosition(myHint, myEditor, myProvider.getBestPointPosition(myHint, (PsiElement)elementForUpdating,offset));
      }
    }
    else context.removeHint();

    myComponent.update();
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
    final ParameterInfoHandler[] handlers = ShowParameterInfoHandler.getHandlers(PsiUtil.getLanguageAtOffset(file, offset));
    
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

  private class MyUpdateParameterInfoContext implements com.intellij.codeInsight.hint.api.UpdateParameterInfoContext {
    private final int myOffset;
    private final PsiFile myFile;

    public MyUpdateParameterInfoContext(final int offset, final PsiFile file) {
      myOffset = offset;
      myFile = file;
    }

    public int getParameterListStart() {
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

    public void setHighlightedParameter(final Object method) {
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

    public void setHighlightedParameter(final PsiElement parameter) {
      setHighlightedParameter((Object)parameter);
    }

    public int getParameterStart() {
      return getParameterListStart();
    }
  }
}
