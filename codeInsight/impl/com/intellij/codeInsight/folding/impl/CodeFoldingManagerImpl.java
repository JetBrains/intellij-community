package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.CodeFoldingState;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
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

  private EditorFactoryListener myEditorFactoryListener;
  private EditorMouseMotionAdapter myMouseMotionListener;

  private WeakList<Document> myDocumentsWithFoldingInfo = new WeakList<Document>();

  private final Key<DocumentFoldingInfo> FOLDING_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_INFO_IN_DOCUMENT_KEY");
  private static final Key<Boolean> FOLDING_STATE_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_STATE_IN_DOCUMENT");

  CodeFoldingManagerImpl(Project project) {
    myProject = project;
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
    myEditorFactoryListener = new EditorFactoryListener() {
      public void editorCreated(EditorFactoryEvent event) {
        final Editor editor = event.getEditor();
        final Project project = editor.getProject();
        if (project != null && !project.equals(myProject)) return;

        final Document document = editor.getDocument();
        //Do not save/restore folding for code fragments
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file == null || !file.getViewProvider().isPhysical()) return;

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (!((FoldingModelEx)editor.getFoldingModel()).isFoldingEnabled()) return;
            if (project.isDisposed() || editor.isDisposed()) return;

            PsiDocumentManager.getInstance(myProject).commitDocument(document);

            Runnable operation = new Runnable() {
              public void run() {
                Runnable runnable = updateFoldRegions(editor, true);
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
        });
      }

      public void editorReleased(EditorFactoryEvent event) {
        Editor editor = event.getEditor();

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
    };

    myMouseMotionListener = new EditorMouseMotionAdapter() {
      LightweightHint myCurrentHint = null;
      FoldRegion myCurrentFold = null;

      public void mouseMoved(EditorMouseEvent e) {
        if (e.getArea() != EditorMouseEventArea.FOLDING_OUTLINE_AREA) return;
        LightweightHint hint = null;
        FoldRegion fold;
        try {
          Editor editor = e.getEditor();
          if (PsiDocumentManager.getInstance(myProject).isUncommited(editor.getDocument())) return;

          MouseEvent mouseEvent = e.getMouseEvent();
          fold = ((EditorEx)editor).getGutterComponentEx().findFoldingAnchorAt(mouseEvent.getX(), mouseEvent.getY());

          if (fold == null) return;
          if (fold == myCurrentFold && myCurrentHint != null) {
            hint = myCurrentHint;
            return;
          }

          PsiElement psiElement = EditorFoldingInfo.get(editor).getPsiElement(fold);
          if (psiElement == null) return;

          int textOffset = psiElement.getTextOffset();
          Point foldStartXY = editor.visualPositionToXY(editor.offsetToVisualPosition(textOffset));
          Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
          if (visibleArea.y > foldStartXY.y) {
            if (myCurrentHint != null) {
              myCurrentHint.hide();
              myCurrentHint = null;
            }
            TextRange textRange = new TextRange(textOffset, fold.getStartOffset());
            hint = EditorFragmentComponent.showEditorFragmentHint(editor, textRange, true);
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

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(myMouseMotionListener);
      }
    });
  }

  public void projectClosed() {
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    EditorFactory.getInstance().getEventMulticaster().removeEditorMouseMotionListener(myMouseMotionListener);
  }

  public FoldRegion findFoldRegion(Editor editor, PsiElement element) {
    return FoldingUtil.findFoldRegion(editor, element);
  }

  public FoldRegion findFoldRegion(Editor editor, int startOffset, int endOffset) {
    return FoldingUtil.findFoldRegion(editor, startOffset, endOffset);
  }

  public FoldRegion[] getFoldRegionsAtOffset(Editor editor, int offset) {
    return FoldingUtil.getFoldRegionsAtOffset(editor, offset);
  }

  public void updateFoldRegions(Editor editor) {
    PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
    Runnable runnable = updateFoldRegions(editor, false);
    if (runnable != null) {
      runnable.run();
    }
  }

  @Nullable
  public Runnable updateFoldRegionsAsync(Editor editor) {
    return updateFoldRegions(editor, false);
  }

  @Nullable
  private Runnable updateFoldRegions(Editor editor, boolean applyDefaultState) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (file != null) {
      editor.getDocument().putUserData(FOLDING_STATE_INFO_IN_DOCUMENT_KEY, Boolean.TRUE);
      return FoldingUpdate.updateFoldRegions(editor, file, applyDefaultState);
    }
    else {
      return null;
    }
  }

  public CodeFoldingState saveFoldingState(Editor editor) {
    DocumentFoldingInfo info = getDocumentFoldingInfo(editor.getDocument());
    info.loadFromEditor(editor);
    return info;
  }

  public void restoreFoldingState(Editor editor, CodeFoldingState state) {
    ((DocumentFoldingInfo)state).setToEditor(editor);
  }

  public void writeFoldingState(CodeFoldingState state, Element element) throws WriteExternalException {
    ((DocumentFoldingInfo)state).writeExternal(element);
  }

  public CodeFoldingState readFoldingState(Element element, Document document) {
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

  public static void resetFoldingInfo(final @NotNull Document document) {
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
