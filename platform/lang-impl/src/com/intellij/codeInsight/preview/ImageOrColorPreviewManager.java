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

package com.intellij.codeInsight.preview;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import static com.intellij.codeInsight.hint.HintManagerImpl.getHintPosition;

/**
 * @author spleaner
 */
public class ImageOrColorPreviewManager implements Disposable, EditorMouseMotionListener, KeyListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.html.preview.ImageOrColorPreviewManager");
  private static final String QUEUE_NAME = "ImageOrColorPreview";
  private static final int HINT_HIDE_FLAGS = HintManager.HIDE_BY_ANY_KEY |
                                             HintManager.HIDE_BY_OTHER_HINT |
                                             HintManager.HIDE_BY_SCROLLING |
                                             HintManager.HIDE_BY_TEXT_CHANGE |
                                             HintManager.HIDE_IF_OUT_OF_EDITOR;
  @Nullable private MergingUpdateQueue myQueue;
  @Nullable private Editor myEditor;
  @Nullable private PsiFile myFile;
  @Nullable private LightweightHint myHint;
  @Nullable private PsiElement myElement;

  public ImageOrColorPreviewManager(@NotNull final TextEditor editor, @NotNull Project project) {
    myEditor = editor.getEditor();

    myEditor.addEditorMouseMotionListener(this);
    myEditor.getContentComponent().addKeyListener(this);

    Document document = myEditor.getDocument();
    myFile = PsiDocumentManager.getInstance(project).getPsiFile(document);


    final JComponent component = editor.getEditor().getComponent();
    myQueue = new MergingUpdateQueue(QUEUE_NAME, 100, component.isShowing(), component);
    Disposer.register(this, new UiNotifyConnector(editor.getComponent(), myQueue));
  }

  @Override
  public void keyTyped(final KeyEvent e) {
  }

  @Override
  public void keyPressed(@NotNull final KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
      if (myEditor != null) {
        final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo != null) {
          final Point location = pointerInfo.getLocation();
          SwingUtilities.convertPointFromScreen(location, myEditor.getContentComponent());

          if (myQueue != null) {
            myQueue.cancelAllUpdates();
            myQueue.queue(new PreviewUpdate(this, location));
          }
        }
      }
    }
  }

  @Override
  public void keyReleased(final KeyEvent e) {
  }

  @Nullable
  public Editor getEditor() {
    return myEditor;
  }

  @Nullable
  private PsiElement getPsiElementAt(@NotNull final Point point) {
    final LogicalPosition position = getLogicalPosition(point);
    if (myEditor != null && myFile != null && !(myFile instanceof PsiCompiledElement)) {
      return InjectedLanguageUtil.findElementAtNoCommit(myFile, myEditor.logicalPositionToOffset(position));
    }

    return null;
  }

  @NotNull
  private LogicalPosition getLogicalPosition(@NotNull final Point point) {
    return myEditor != null ? myEditor.xyToLogicalPosition(point) : new LogicalPosition(0, 0);
  }

  private void setCurrentHint(@Nullable final LightweightHint hint, final PsiElement element) {
    if (hint != null) {
      myHint = hint;
      myElement = element;
    }
  }

  private void showHint(@NotNull final LightweightHint hint,
                        @NotNull final PsiElement element,
                        @NotNull Editor editor,
                        @NotNull Point point) {
    if (element != myElement && element.isValid()) {
      hideCurrentHintIfAny();
      setCurrentHint(hint, element);

      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor,
                                                       getHintPosition(hint, editor, getLogicalPosition(point), HintManager.RIGHT_UNDER),
                                                       HINT_HIDE_FLAGS, 0, false);
    }
  }

  private void hideCurrentHindIfOutOfElement(final PsiElement e) {
    if (myHint != null && e != myElement) {
      hideCurrentHintIfAny();
    }
  }

  private void hideCurrentHintIfAny() {
    if (myHint != null) {
      myHint.hide();
      myHint = null;
      myElement = null;
    }
  }

  @Override
  public void dispose() {
    if (myEditor != null) {
      myEditor.removeEditorMouseMotionListener(this);
      myEditor.getContentComponent().removeKeyListener(this);
    }

    if (myQueue != null) {
      myQueue.cancelAllUpdates();
      myQueue.hideNotify();
    }

    myQueue = null;
    myEditor = null;
    myFile = null;
    myHint = null;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    if (myQueue != null) {
      myQueue.cancelAllUpdates();
    }
    if (myHint == null && e.getMouseEvent().getModifiers() == InputEvent.SHIFT_MASK) {
      myQueue.queue(new PreviewUpdate(this, e.getMouseEvent().getPoint()));
    }
    else {
      hideCurrentHindIfOutOfElement(getPsiElementAt(e.getMouseEvent().getPoint()));
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

  private static final class PreviewUpdate extends Update {
    private final ImageOrColorPreviewManager myManager;
    @NotNull private final Point myPoint;

    public PreviewUpdate(@NonNls final ImageOrColorPreviewManager manager, @NotNull final Point point) {
      super(manager);

      myManager = manager;
      myPoint = point;
    }

    @Override
    public void run() {
      final PsiElement element = myManager.getPsiElementAt(myPoint);
      final Editor editor = myManager.getEditor();
      if (editor != null && element != null && element.isValid()) {
        if (PsiDocumentManager.getInstance(element.getProject()).isUncommited(editor.getDocument())) {
          return;
        }

        if (DumbService.getInstance(element.getProject()).isDumb()) {
          return;
        }

        final LightweightHint hint = getHint(element);
        if (hint != null) {
          myManager.showHint(hint, element, editor, myPoint);
        }
        else {
          myManager.hideCurrentHintIfAny();
        }
      }
    }

    @Override
    public boolean canEat(final Update update) {
      return true;
    }
  }
}
