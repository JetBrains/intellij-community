/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Alarm;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Serves as a facade to the 'show quick doc on mouse over an element' functionality.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 7/2/12 9:09 AM
 */
public class QuickDocOnMouseOverManager {

  @NotNull private final EditorMouseMotionListener myMouseListener       = new MyEditorMouseListener();
  @NotNull private final VisibleAreaListener       myVisibleAreaListener = new MyVisibleAreaListener();
  @NotNull private final CaretListener             myCaretListener       = new MyCaretListener();
  @NotNull private final DocumentListener          myDocumentListener    = new MyDocumentListener();
  @NotNull private final Alarm                     myAlarm               = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull private final Runnable                  myRequest             = new MyShowQuickDocRequest();
  @NotNull private final Runnable                  myHintCloseCallback   = new MyCloseDocCallback();
  @NotNull private final Map<Document, Boolean>    myMonitoredDocuments  = new WeakHashMap<Document, Boolean>();

  private final Map<Editor, PsiElement /** PSI element which is located under the current mouse position */> myActiveElements
    = new WeakHashMap<Editor, PsiElement>();

  /** Holds a reference (if any) to the documentation manager used last time to show an 'auto quick doc' popup. */
  @Nullable private WeakReference<DocumentationManager> myDocumentationManager;

  @Nullable private DelayedQuickDocInfo myDelayedQuickDocInfo;
  private           boolean             myEnabled;
  private           boolean             myApplicationActive;

  public QuickDocOnMouseOverManager(@NotNull Application application) {
    EditorFactory factory = EditorFactory.getInstance();
    if (factory != null) {
      factory.addEditorFactoryListener(new MyEditorFactoryListener(), application);
    }

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(
      ApplicationActivationListener.TOPIC,
      new ApplicationActivationListener() {
        @Override
        public void applicationActivated(IdeFrame ideFrame) {
          myApplicationActive = true;
        }

        @Override
        public void applicationDeactivated(IdeFrame ideFrame) {
          myApplicationActive = false;
        }
      });
  }

  /**
   * Instructs the manager to enable or disable 'show quick doc automatically when the mouse goes over an editor element' mode.
   *
   * @param enabled  flag that identifies if quick doc should be automatically shown
   */
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    myApplicationActive = enabled;
    if (!enabled) {
      closeQuickDocIfPossible();
      myAlarm.cancelAllRequests();
    }
    EditorFactory factory = EditorFactory.getInstance();
    if (factory == null) {
      return;
    }
    for (Editor editor : factory.getAllEditors()) {
      if (enabled) {
        registerListeners(editor);
      }
      else {
        unRegisterListeners(editor);
      }
    }
  }

  private void registerListeners(@NotNull Editor editor) {
    editor.addEditorMouseMotionListener(myMouseListener);
    editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    editor.getCaretModel().addCaretListener(myCaretListener);

    Document document = editor.getDocument();
    if (myMonitoredDocuments.put(document, Boolean.TRUE) == null) {
      document.addDocumentListener(myDocumentListener);
    }
  }

  private void unRegisterListeners(@NotNull Editor editor) {
    editor.removeEditorMouseMotionListener(myMouseListener);
    editor.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    editor.getCaretModel().removeCaretListener(myCaretListener);

    Document document = editor.getDocument();
    if (myMonitoredDocuments.remove(document) != null) {
      document.removeDocumentListener(myDocumentListener);
    }
  }
  
  private void processMouseMove(@NotNull EditorMouseEvent e) {
    if (!myApplicationActive || e.getArea() != EditorMouseEventArea.EDITING_AREA) {
      // Skip if the mouse is not at the editing area.
      closeQuickDocIfPossible();
      return;
    }

    if (e.getMouseEvent().getModifiers() != 0) {
      // Don't show the control when any modifier is active (e.g. Ctrl or Alt is hold). There is a common situation that a user
      // wants to navigate via Ctrl+click or perform quick evaluate by Alt+click.
      return;
    }
    
    Editor editor = e.getEditor();
    if (editor.isOneLineMode()) {
      // Don't want auto quick doc to mess at, say, editor used for debugger condition.
      return;
    }
    
    Project project = editor.getProject();
    if (project == null) {
      return;
    }

    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    JBPopup hint = documentationManager.getDocInfoHint();
    if (hint != null) {

      // Skip the event if the control is shown because of explicit 'show quick doc' action call.
      WeakReference<DocumentationManager> ref = myDocumentationManager;
      if (ref == null) {
        return;
      }
      DocumentationManager manager = ref.get();
      if (manager == null || !manager.isCloseOnSneeze()) {
        return;
      }

      // Skip the event if the mouse is under the opened quick doc control.
      Point hintLocation = hint.getLocationOnScreen();
      Dimension hintSize = hint.getSize();
      int mouseX = e.getMouseEvent().getXOnScreen();
      int mouseY = e.getMouseEvent().getYOnScreen();
      if (mouseX >= hintLocation.x && mouseX <= hintLocation.x + hintSize.width && mouseY >= hintLocation.y
          && mouseY <= hintLocation.y + hintSize.height)
      {
        return;
      }
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      closeQuickDocIfPossible();
      return;
    }

    VisualPosition visualPosition = editor.xyToVisualPosition(e.getMouseEvent().getPoint());
    if (editor.getSoftWrapModel().isInsideOrBeforeSoftWrap(visualPosition)) {
      closeQuickDocIfPossible();
      return;
    }
    
    int mouseOffset = editor.logicalPositionToOffset(editor.visualToLogicalPosition(visualPosition));
    PsiElement elementUnderMouse = psiFile.findElementAt(mouseOffset);
    if (elementUnderMouse == null || elementUnderMouse instanceof PsiWhiteSpace) {
      closeQuickDocIfPossible();
      return;
    }
    
    PsiElement targetElementUnderMouse = documentationManager.findTargetElement(editor, mouseOffset, psiFile, elementUnderMouse);
    if (targetElementUnderMouse == null) {
      // No PSI element is located under the current mouse position - close quick doc if any.
      closeQuickDocIfPossible();
      return;
    }

    PsiElement activeElement = myActiveElements.get(editor);
    if (targetElementUnderMouse.equals(activeElement)
        && (!myAlarm.isEmpty() // Request to show documentation for the target component has been already queued.
            || hint != null)) // Documentation for the target component is being shown.
    { 
      return;
    }
    allowUpdateFromContext(false);
    closeQuickDocIfPossible();
    myActiveElements.put(editor, targetElementUnderMouse);
    myDelayedQuickDocInfo = new DelayedQuickDocInfo(documentationManager, editor, targetElementUnderMouse, elementUnderMouse);

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myRequest, EditorSettingsExternalizable.getInstance().getQuickDocOnMouseOverElementDelayMillis());
  }

  private void closeQuickDocIfPossible() {
    myAlarm.cancelAllRequests();
    DocumentationManager docManager = getDocManager();
    if (docManager == null) {
      return;
    }

    JBPopup hint = docManager.getDocInfoHint();
    if (hint == null) {
      return;
    }
    
    hint.cancel();
    myDocumentationManager = null;
  }

  private void allowUpdateFromContext(boolean allow) {
    DocumentationManager documentationManager = getDocManager();
    if (documentationManager != null) {
      documentationManager.setAllowContentUpdateFromContext(allow);
    }
  }

  @Nullable
  private DocumentationManager getDocManager() {
    WeakReference<DocumentationManager> ref = myDocumentationManager;
    if (ref == null) {
      return null;
    }

    DocumentationManager docManager = ref.get();
    if (docManager == null) {
      return null;
    }
    return docManager;
  }
  
  private static class DelayedQuickDocInfo {

    @NotNull public final DocumentationManager docManager;
    @NotNull public final Editor               editor;
    @NotNull public final PsiElement           targetElement;
    @NotNull public final PsiElement           originalElement;

    private DelayedQuickDocInfo(@NotNull DocumentationManager docManager,
                                @NotNull Editor editor, @NotNull PsiElement targetElement,
                                @NotNull PsiElement originalElement)
    {
      this.docManager = docManager;
      this.editor = editor;
      this.targetElement = targetElement;
      this.originalElement = originalElement;
    }
  }

  private class MyShowQuickDocRequest implements Runnable {
    
    private final HintManager myHintManager = HintManager.getInstance();
    
    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      
      // Skip the request if it's outdated (the mouse is moved other another element).
      DelayedQuickDocInfo info = myDelayedQuickDocInfo;
      if (info == null || !info.targetElement.equals(myActiveElements.get(info.editor))) {
        return;
      }

      // Skip the request if there is a control shown as a result of explicit 'show quick doc' (Ctrl + Q) invocation.
      if (info.docManager.getDocInfoHint() != null && !info.docManager.isCloseOnSneeze()) {
        return;
      }
      
      // We don't want to show a quick doc control if there is an active hint (e.g. the mouse is under an invalid element
      // and corresponding error info is shown).
      if (!info.docManager.hasActiveDockedDocWindow() && myHintManager.hasShownHintsThatWillHideByOtherHint(false)) {
        myAlarm.addRequest(this, EditorSettingsExternalizable.getInstance().getQuickDocOnMouseOverElementDelayMillis());
        return;
      }
      
      info.editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION,
                              info.editor.offsetToVisualPosition(info.originalElement.getTextRange().getStartOffset()));
      try {
        info.docManager.showJavaDocInfo(info.editor, info.targetElement, info.originalElement, myHintCloseCallback, true, true);
        myDocumentationManager = new WeakReference<DocumentationManager>(info.docManager);
        myDocumentationManager = new WeakReference<DocumentationManager>(info.docManager);
      }
      finally {
        info.editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
      }
    }
  }
  
  private class MyCloseDocCallback implements Runnable {
    @Override
    public void run() {
      myActiveElements.clear();
      myDocumentationManager = null;
    }
  }
  
  private class MyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      if (myEnabled) {
        registerListeners(event.getEditor());
      }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      if (myEnabled) {
        // We do this in the 'if' block because editor logs an error on attempt to remove already released listener. 
        unRegisterListeners(event.getEditor());
      }
    }
  }

  private class MyEditorMouseListener extends EditorMouseMotionAdapter {

    @Override
    public void mouseMoved(EditorMouseEvent e) {
      processMouseMove(e);
    }
  }
  
  private class MyVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      closeQuickDocIfPossible();
    }
  }
  
  private class MyCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      allowUpdateFromContext(true);
      closeQuickDocIfPossible(); 
    }
  }
  
  private class MyDocumentListener extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent e) {
      closeQuickDocIfPossible();
    }
  }
}
