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

package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.containers.WeakList;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class CodeFoldingManagerImpl extends CodeFoldingManager implements ProjectComponent {
  private final Project myProject;

  private final List<Document> myDocumentsWithFoldingInfo = new WeakList<Document>();

  private final Key<DocumentFoldingInfo> myFoldingInfoInDocumentKey = Key.create("FOLDING_INFO_IN_DOCUMENT_KEY");
  private static final Key<Boolean> FOLDING_STATE_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_STATE_IN_DOCUMENT");

  CodeFoldingManagerImpl(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateStarted(@NotNull final Document doc) {
        resetFoldingInfo(doc);
      }
    });
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "CodeFoldingManagerImpl";
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() {
    for (Document document : myDocumentsWithFoldingInfo) {
      if (document != null) {
        document.putUserData(myFoldingInfoInDocumentKey, null);
      }
    }
  }

  @Override
  public void projectOpened() {
    final EditorMouseMotionAdapter myMouseMotionListener = new EditorMouseMotionAdapter() {
      LightweightHint myCurrentHint = null;
      FoldRegion myCurrentFold = null;

      @Override
      public void mouseMoved(EditorMouseEvent e) {
        if (myProject.isDisposed()) return;
        HintManager hintManager = HintManager.getInstance();
        if (hintManager != null && hintManager.hasShownHintsThatWillHideByOtherHint(false)) {
          return;
        } 

        if (e.getArea() != EditorMouseEventArea.FOLDING_OUTLINE_AREA) return;
        LightweightHint hint = null;
        try {
          Editor editor = e.getEditor();
          if (PsiDocumentManager.getInstance(myProject).isUncommited(editor.getDocument())) return;

          MouseEvent mouseEvent = e.getMouseEvent();
          FoldRegion fold = ((EditorEx)editor).getGutterComponentEx().findFoldingAnchorAt(mouseEvent.getX(), mouseEvent.getY());

          if (fold == null || !fold.isValid()) return;
          if (fold == myCurrentFold && myCurrentHint != null) {
            hint = myCurrentHint;
            return;
          }

          TextRange psiElementRange = EditorFoldingInfo.get(editor).getPsiElementRange(fold);
          if (psiElementRange == null) return;

          int textOffset = psiElementRange.getStartOffset();
          // There is a possible case that target PSI element's offset is less than fold region offset (e.g. complete method is
          // returned as PSI element for fold region that corresponds to java method code block). We don't want to show any hint
          // if start of the current fold region is displayed.
          Point foldStartXY = editor.visualPositionToXY(editor.offsetToVisualPosition(Math.max(textOffset, fold.getStartOffset())));
          Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
          if (visibleArea.y > foldStartXY.y) {
            if (myCurrentHint != null) {
              myCurrentHint.hide();
              myCurrentHint = null;
            }
            
            
            // We want to show a hint with the top fold region content that is above the current viewport position.
            // However, there is a possible case that complete region has a big height and only a little bottom part
            // is shown at the moment. We can't just show hint with the whole top content because it would hide actual
            // editor content, hence, we show max(2; available visual lines number) instead.
            // P.S. '2' is used here in assumption that many java methods have javadocs which first line is just '/**'.
            // So, it's not too useful to show only it even when available vertical space is not big enough.
            int availableVisualLines = 2;
            JComponent editorComponent = editor.getComponent();
            Container editorComponentParent = editorComponent.getParent();
            if (editorComponentParent != null) {
              Container contentPane = editorComponent.getRootPane().getContentPane();
              if (contentPane != null) {
                int y = SwingUtilities.convertPoint(editorComponentParent, editorComponent.getLocation(), contentPane).y;
                int visualLines = y / editor.getLineHeight();
                availableVisualLines = Math.max(availableVisualLines, visualLines);
              }
            }
            int startVisualLine = editor.offsetToVisualPosition(textOffset).line;
            int desiredEndVisualLine = Math.max(0, editor.xyToVisualPosition(new Point(0, visibleArea.y)).line - 1);
            int endVisualLine = startVisualLine + availableVisualLines;
            if (endVisualLine > desiredEndVisualLine) {
              endVisualLine = desiredEndVisualLine;
            }

            // Show only the non-displayed top part of the target fold region
            int endOffset = editor.logicalPositionToOffset(editor.visualToLogicalPosition(new VisualPosition(endVisualLine, 0)));
            TextRange textRange = new UnfairTextRange(textOffset, endOffset);
            hint = EditorFragmentComponent.showEditorFragmentHint(editor, textRange, true, true);
            myCurrentFold = fold;
            myCurrentHint = hint;
          }
        }
        finally {
          if (hint == null) {
            if (myCurrentHint != null) {
              myCurrentHint.hide();
              myCurrentHint = null;
            }
            myCurrentFold = null;
          }
        }
      }
    };

    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(myMouseMotionListener, myProject);
      }
    });
  }

  @Override
  public void releaseFoldings(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Project project = editor.getProject();
    if (project != null && (!project.equals(myProject) || !project.isOpen())) return;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null || !file.getViewProvider().isPhysical() || !file.isValid()) return;
    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    Editor[] otherEditors = EditorFactory.getInstance().getEditors(document, myProject);
    if (otherEditors.length == 0 && !editor.isDisposed()) {
      getDocumentFoldingInfo(document).loadFromEditor(editor);
    }
    EditorFoldingInfo.get(editor).dispose();
  }

  @Override
  public void buildInitialFoldings(@NotNull final Editor editor) {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(editor.getComponent());
    final Project project = editor.getProject();
    if (project == null || !project.equals(myProject)) return;

    final Document document = editor.getDocument();
    //Do not save/restore folding for code fragments
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null || !file.getViewProvider().isPhysical() && !ApplicationManager.getApplication().isUnitTestMode()) return;

    final FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
    if (!foldingModel.isFoldingEnabled()) return;
    if (project.isDisposed() || editor.isDisposed() || !file.isValid()) return;

    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    Runnable operation = new Runnable() {
      @Override
      public void run() {
        Runnable runnable = updateFoldRegions(editor, true, true);
        if (runnable != null) {
          runnable.run();
        }
        if (myProject.isDisposed() || editor.isDisposed()) return;
        foldingModel.runBatchFoldingOperation(new Runnable() {
          @Override
          public void run() {
            DocumentFoldingInfo documentFoldingInfo = getDocumentFoldingInfo(document);
            Editor[] editors = EditorFactory.getInstance().getEditors(document, myProject);
            for (Editor otherEditor : editors) {
              if (otherEditor == editor) continue;
              documentFoldingInfo.loadFromEditor(otherEditor);
              break;
            }
            documentFoldingInfo.setToEditor(editor);

            documentFoldingInfo.clear();
          }
        });
      }
    };
    UIUtil.invokeLaterIfNeeded(operation);
  }

  @Override
  public void projectClosed() {
  }
  
  @Override
  @Nullable
  public FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset) {
    return FoldingUtil.findFoldRegion(editor, startOffset, endOffset);
  }

  @Override
  public FoldRegion[] getFoldRegionsAtOffset(@NotNull Editor editor, int offset) {
    return FoldingUtil.getFoldRegionsAtOffset(editor, offset);
  }

  @Override
  public void updateFoldRegions(@NotNull Editor editor) {
    updateFoldRegions(editor, false);
  }

  public void updateFoldRegions(Editor editor, boolean quick) {
    PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
    Runnable runnable = updateFoldRegions(editor, false, quick);
    if (runnable != null) {
      runnable.run();
    }
  }

  @Override
  public void forceDefaultState(@NotNull final Editor editor) {
    PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
    Runnable runnable = updateFoldRegions(editor, true, false);
    if (runnable != null) {
      runnable.run();
    }

    final FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        EditorFoldingInfo foldingInfo = EditorFoldingInfo.get(editor);
        for (FoldRegion region : regions) {
          PsiElement element = foldingInfo.getPsiElement(region);
          if (element != null) {
            region.setExpanded(!FoldingPolicy.isCollapseByDefault(element));
          }
        }
      }
    });
  }

  @Override
  @Nullable
  public Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime) {
    return updateFoldRegions(editor, firstTime, false);
  }

  @Nullable
  private Runnable updateFoldRegions(@NotNull Editor editor, boolean applyDefaultState, boolean quick) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (file != null) {
      editor.getDocument().putUserData(FOLDING_STATE_INFO_IN_DOCUMENT_KEY, Boolean.TRUE);
      return FoldingUpdate.updateFoldRegions(editor, file, applyDefaultState, quick);
    }
    else {
      return null;
    }
  }

  @Override
  public CodeFoldingState saveFoldingState(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DocumentFoldingInfo info = getDocumentFoldingInfo(editor.getDocument());
    info.loadFromEditor(editor);
    return info;
  }

  @Override
  public void restoreFoldingState(@NotNull Editor editor, @NotNull CodeFoldingState state) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((DocumentFoldingInfo)state).setToEditor(editor);
  }

  @Override
  public void writeFoldingState(@NotNull CodeFoldingState state, @NotNull Element element) throws WriteExternalException {
    ((DocumentFoldingInfo)state).writeExternal(element);
  }

  @Override
  public CodeFoldingState readFoldingState(@NotNull Element element, @NotNull Document document) {
    DocumentFoldingInfo info = getDocumentFoldingInfo(document);
    info.readExternal(element);
    return info;
  }

  @NotNull
  private DocumentFoldingInfo getDocumentFoldingInfo(@NotNull Document document) {
    DocumentFoldingInfo info = document.getUserData(myFoldingInfoInDocumentKey);
    if (info == null) {
      info = new DocumentFoldingInfo(myProject, document);
      DocumentFoldingInfo written = ((UserDataHolderEx)document).putUserDataIfAbsent(myFoldingInfoInDocumentKey, info);
      if (written == info) {
        myDocumentsWithFoldingInfo.add(document);
      }
      else {
        info = written;
      }
    }
    return info;
  }

  private static void resetFoldingInfo(@NotNull final Document document) {
    final Boolean foldingInfoStatus = document.getUserData(FOLDING_STATE_INFO_IN_DOCUMENT_KEY);
    if (Boolean.TRUE.equals(foldingInfoStatus)) {
      final Editor[] editors = EditorFactory.getInstance().getEditors(document);
      for(Editor editor:editors) {
        EditorFoldingInfo.resetInfo(editor);
      }
      document.putUserData(FOLDING_STATE_INFO_IN_DOCUMENT_KEY, null);
    }
  }
}
