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

package com.intellij.codeInsight.preview;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.ref.WeakReference;

import static com.intellij.codeInsight.hint.HintManagerImpl.getHintPosition;

public class ImageOrColorPreviewManager implements Disposable, EditorMouseMotionListener {
  private static final Key<KeyListener> EDITOR_LISTENER_ADDED = Key.create("previewManagerListenerAdded");

  private static final Logger LOG = Logger.getInstance("#com.intellij.html.preview.ImageOrColorPreviewManager");
  private static final int HINT_HIDE_FLAGS = HintManager.HIDE_BY_ANY_KEY |
                                             HintManager.HIDE_BY_OTHER_HINT |
                                             HintManager.HIDE_BY_SCROLLING |
                                             HintManager.HIDE_BY_TEXT_CHANGE |
                                             HintManager.HIDE_IF_OUT_OF_EDITOR;
  private final Alarm alarm = new Alarm();

  @Nullable
  private LightweightHint hint;
  @Nullable
  private WeakReference<PsiElement> elementRef;

  public ImageOrColorPreviewManager(EditorFactory editorFactory) {
    // we don't use multicaster because we don't want to serve all editors - only supported
    editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        registerListeners(event.getEditor());
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.isOneLineMode()) {
          return;
        }

        KeyListener keyListener = editor.getUserData(EDITOR_LISTENER_ADDED);
        if (keyListener != null) {
          editor.getContentComponent().removeKeyListener(keyListener);
          editor.removeEditorMouseMotionListener(ImageOrColorPreviewManager.this);
        }
      }
    }, this);
  }

  private void registerListeners(final Editor editor) {
    if (editor.isOneLineMode()) {
      return;
    }

    Project project = editor.getProject();
    if (project == null || project.isDisposed()) {
      return;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null || psiFile instanceof PsiCompiledElement || !isSupportedFile(psiFile)) {
      return;
    }

    editor.addEditorMouseMotionListener(this);

    KeyListener keyListener = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT && !editor.isOneLineMode()) {
          PointerInfo pointerInfo = MouseInfo.getPointerInfo();
          if (pointerInfo != null) {
            Point location = pointerInfo.getLocation();
            SwingUtilities.convertPointFromScreen(location, editor.getContentComponent());
            alarm.cancelAllRequests();
            alarm.addRequest(new PreviewRequest(location, editor), 100);
          }
        }
      }
    };
    editor.getContentComponent().addKeyListener(keyListener);

    editor.putUserData(EDITOR_LISTENER_ADDED, keyListener);
  }

  private static boolean isSupportedFile(PsiFile psiFile) {
    for (PreviewHintProvider hintProvider : Extensions.getExtensions(PreviewHintProvider.EP_NAME)) {
      if (hintProvider.isSupportedFile(psiFile)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement getPsiElementAt(Point point, Editor editor) {
    if (editor.isDisposed()) {
      return null;
    }

    Project project = editor.getProject();
    if (project == null || project.isDisposed()) {
      return null;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null || psiFile instanceof PsiCompiledElement) {
      return null;
    }
    return InjectedLanguageUtil.findElementAtNoCommit(psiFile, editor.logicalPositionToOffset(editor.xyToLogicalPosition(point)));
  }

  private void hideCurrentHintIfAny() {
    if (hint != null) {
      hint.hide();
      hint = null;
      elementRef = null;
    }
  }

  @Override
  public void dispose() {
    alarm.cancelAllRequests();
    hint = null;
    elementRef = null;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    Editor editor = e.getEditor();
    if (editor.isOneLineMode()) {
      return;
    }

    alarm.cancelAllRequests();
    Point point = e.getMouseEvent().getPoint();
    if (hint == null && e.getMouseEvent().isShiftDown()) {
      alarm.addRequest(new PreviewRequest(point, editor), 100);
    }
    else if (hint != null && elementRef != null && elementRef.get() != getPsiElementAt(point, editor)) {
      hideCurrentHintIfAny();
    }
  }

  @Override
  public void mouseDragged(EditorMouseEvent e) {
    // nothing
  }

  @Nullable
  private static LightweightHint getHint(@NotNull PsiElement element) {
    for (PreviewHintProvider hintProvider : Extensions.getExtensions(PreviewHintProvider.EP_NAME)) {
      JComponent preview;
      try {
        preview = hintProvider.getPreviewComponent(element);
      }
      catch (Exception e) {
        LOG.error(e);
        continue;
      }
      if (preview != null) {
        return new LightweightHint(preview);
      }
    }
    return null;
  }

  private final class PreviewRequest implements Runnable {
    private final Point point;
    private final Editor editor;

    public PreviewRequest(Point point, Editor editor) {
      this.point = point;
      this.editor = editor;
    }

    @Override
    public void run() {
      PsiElement element = getPsiElementAt(point, editor);
      if (element == null || !element.isValid()) {
        return;
      }

      if (PsiDocumentManager.getInstance(element.getProject()).isUncommited(editor.getDocument())) {
        return;
      }

      if (DumbService.getInstance(element.getProject()).isDumb()) {
        return;
      }

      LightweightHint newHint = getHint(element);
      if (newHint == null) {
        hideCurrentHintIfAny();
      }
      else if ((elementRef == null || elementRef.get() != element) && element.isValid()) {
        hideCurrentHintIfAny();
        hint = newHint;
        elementRef = new WeakReference<PsiElement>(element);

        HintManagerImpl.getInstanceImpl().showEditorHint(newHint, editor,
                                                         getHintPosition(newHint, editor, editor.xyToLogicalPosition(point), HintManager.RIGHT_UNDER),
                                                         HINT_HIDE_FLAGS, 0, false);
      }
    }
  }
}