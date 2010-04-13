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

package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.containers.WeakList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;

public class CodeFoldingManagerImpl extends CodeFoldingManager implements ProjectComponent {
  private final Project myProject;

  private final WeakList<Document> myDocumentsWithFoldingInfo = new WeakList<Document>();

  private final Key<DocumentFoldingInfo> FOLDING_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_INFO_IN_DOCUMENT_KEY");
  private static final Key<Boolean> FOLDING_STATE_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_STATE_IN_DOCUMENT");

  CodeFoldingManagerImpl(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      public void updateStarted(final Document doc) {
        resetFoldingInfo(doc); //TODO RangeMarkers tree convert
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "CodeFoldingManagerImpl";
  }

  public void initComponent() { }

  public void disposeComponent() {
    for (Document document : myDocumentsWithFoldingInfo) {
      if (document != null) {
        document.putUserData(FOLDING_INFO_IN_DOCUMENT_KEY, null);
      }
    }
  }

  public void projectOpened() {
    final EditorMouseMotionAdapter myMouseMotionListener = new EditorMouseMotionAdapter() {
      LightweightHint myCurrentHint = null;
      FoldRegion myCurrentFold = null;

      public void mouseMoved(EditorMouseEvent e) {
        if (myProject.isDisposed()) return;

        if (e.getArea() != EditorMouseEventArea.FOLDING_OUTLINE_AREA) return;
        LightweightHint hint = null;
        try {
          Editor editor = e.getEditor();
          if (PsiDocumentManager.getInstance(myProject).isUncommited(editor.getDocument())) return;

          MouseEvent mouseEvent = e.getMouseEvent();
          FoldRegion fold = ((EditorEx)editor).getGutterComponentEx().findFoldingAnchorAt(mouseEvent.getX(), mouseEvent.getY());

          if (fold == null) return;
          if (fold == myCurrentFold && myCurrentHint != null) {
            hint = myCurrentHint;
            return;
          }

          TextRange psiElementRange = EditorFoldingInfo.get(editor).getPsiElementRange(fold);
          if (psiElementRange == null) return;

          int textOffset = psiElementRange.getStartOffset();
          Point foldStartXY = editor.visualPositionToXY(editor.offsetToVisualPosition(textOffset));
          Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
          if (visibleArea.y > foldStartXY.y) {
            if (myCurrentHint != null) {
              myCurrentHint.hide();
              myCurrentHint = null;
            }
            TextRange textRange = new TextRange(textOffset, fold.getStartOffset());
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
      public void run() {
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(myMouseMotionListener, myProject);
      }
    });
  }

  @Override
  public void releaseFoldings(Editor editor) {
    final Project project = editor.getProject();
    if (project != null && !project.equals(myProject)) return;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null || !file.getViewProvider().isPhysical() || !file.isValid()) return;
    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    Editor[] otherEditors = EditorFactory.getInstance().getEditors(document, myProject);
    if (otherEditors.length == 0) {
      getDocumentFoldingInfo(document).loadFromEditor(editor);
    }
    EditorFoldingInfo.get(editor).dispose();
  }

  @Override
  public void buildInitialFoldings(final Editor editor) {
    final Project project = editor.getProject();
    if (project == null || !project.equals(myProject)) return;

    final Document document = editor.getDocument();
    //Do not save/restore folding for code fragments
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null || !file.getViewProvider().isPhysical() && !ApplicationManager.getApplication().isUnitTestMode()) return;

    if (!((FoldingModelEx)editor.getFoldingModel()).isFoldingEnabled()) return;
    if (project.isDisposed() || editor.isDisposed() || !file.isValid()) return;

    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    Runnable operation = new Runnable() {
      public void run() {
        Runnable runnable = updateFoldRegions(editor, true, true);
        if (runnable != null) {
          runnable.run();
        }

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
    };
    editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
  }

  public void projectClosed() {
  }

  public FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset) {
    return FoldingUtil.findFoldRegion(editor, startOffset, endOffset);
  }

  public FoldRegion[] getFoldRegionsAtOffset(@NotNull Editor editor, int offset) {
    return FoldingUtil.getFoldRegionsAtOffset(editor, offset);
  }

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
      public void run() {
        for (FoldRegion region : regions) {
          PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
          if (element != null) {
            region.setExpanded(!FoldingPolicy.isCollapseByDefault(element));
          }
        }
      }
    });
  }

  @Nullable
  public Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime) {
    return updateFoldRegions(editor, firstTime, false);
  }

  @Nullable
  private Runnable updateFoldRegions(Editor editor, boolean applyDefaultState, boolean quick) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (file != null) {
      editor.getDocument().putUserData(FOLDING_STATE_INFO_IN_DOCUMENT_KEY, Boolean.TRUE);
      return FoldingUpdate.updateFoldRegions(editor, file, applyDefaultState, quick);
    }
    else {
      return null;
    }
  }

  public CodeFoldingState saveFoldingState(@NotNull Editor editor) {
    DocumentFoldingInfo info = getDocumentFoldingInfo(editor.getDocument());
    info.loadFromEditor(editor);
    return info;
  }

  public void restoreFoldingState(@NotNull Editor editor, @NotNull CodeFoldingState state) {
    ((DocumentFoldingInfo)state).setToEditor(editor);
  }

  public void writeFoldingState(@NotNull CodeFoldingState state, @NotNull Element element) throws WriteExternalException {
    ((DocumentFoldingInfo)state).writeExternal(element);
  }

  public CodeFoldingState readFoldingState(@NotNull Element element, @NotNull Document document) {
    DocumentFoldingInfo info = getDocumentFoldingInfo(document);
    info.readExternal(element);
    return info;
  }

  private DocumentFoldingInfo getDocumentFoldingInfo(Document document) {
    DocumentFoldingInfo info = document.getUserData(FOLDING_INFO_IN_DOCUMENT_KEY);
    if (info == null) {
      info = new DocumentFoldingInfo(myProject, document);
      document.putUserData(FOLDING_INFO_IN_DOCUMENT_KEY, info);
      myDocumentsWithFoldingInfo.add(document);
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
