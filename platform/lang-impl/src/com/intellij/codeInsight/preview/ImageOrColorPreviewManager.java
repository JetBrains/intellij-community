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
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.ref.WeakReference;

public class ImageOrColorPreviewManager implements Disposable, EditorMouseMotionListener {
  private static final Logger LOG = Logger.getInstance(ImageOrColorPreviewManager.class);

  private static final Key<KeyListener> EDITOR_LISTENER_ADDED = Key.create("previewManagerListenerAdded");

  private final Alarm alarm = new Alarm();

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
            alarm.addRequest(new PreviewRequest(location, editor, true), 100);
          }
        }
      }
    };
    editor.getContentComponent().addKeyListener(keyListener);

    editor.putUserData(EDITOR_LISTENER_ADDED, keyListener);
  }

  private static boolean isSupportedFile(PsiFile psiFile) {
    for (ElementPreviewProvider provider : Extensions.getExtensions(ElementPreviewProvider.EP_NAME)) {
      if (provider.isSupportedFile(psiFile)) {
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
    if (psiFile == null || psiFile instanceof PsiCompiledElement || !psiFile.isValid()) {
      return null;
    }

    return InjectedLanguageUtil.findElementAtNoCommit(psiFile, editor.logicalPositionToOffset(editor.xyToLogicalPosition(point)));
  }

  @Override
  public void dispose() {
    alarm.cancelAllRequests();
    elementRef = null;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent event) {
    Editor editor = event.getEditor();
    if (editor.isOneLineMode()) {
      return;
    }

    alarm.cancelAllRequests();
    Point point = event.getMouseEvent().getPoint();
    if (elementRef == null && event.getMouseEvent().isShiftDown()) {
      alarm.addRequest(new PreviewRequest(point, editor, false), 100);
    }
    else if (elementRef != null && elementRef.get() != getPsiElementAt(point, editor)) {
      PsiElement element = elementRef.get();
      elementRef = null;
      for (ElementPreviewProvider provider : Extensions.getExtensions(ElementPreviewProvider.EP_NAME)) {
        try {
          provider.hide(element, editor);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public void mouseDragged(EditorMouseEvent e) {
    // nothing
  }

  private final class PreviewRequest implements Runnable {
    private final Point point;
    private final Editor editor;
    private final boolean keyTriggered;

    public PreviewRequest(Point point, Editor editor, boolean keyTriggered) {
      this.point = point;
      this.editor = editor;
      this.keyTriggered = keyTriggered;
    }

    @Override
    public void run() {
      PsiElement element = getPsiElementAt(point, editor);
      if (element == null || !element.isValid() || (elementRef != null && elementRef.get() == element)) {
        return;
      }
      if (PsiDocumentManager.getInstance(element.getProject()).isUncommited(editor.getDocument()) || DumbService.getInstance(element.getProject()).isDumb()) {
        return;
      }

      elementRef = new WeakReference<PsiElement>(element);
      for (ElementPreviewProvider provider : Extensions.getExtensions(ElementPreviewProvider.EP_NAME)) {
        try {
          provider.show(element, editor, point, keyTriggered);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }
}