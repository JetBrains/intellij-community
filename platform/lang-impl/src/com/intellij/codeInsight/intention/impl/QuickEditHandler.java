/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author Gregory Shrago
*/
public class QuickEditHandler extends DocumentAdapter implements Disposable {
  private final Project myProject;
  private final PsiFile myInjectedFile;
  private final Editor myEditor;
  private final QuickEditAction myAction;
  private final Document myOrigDocument;
  private final PsiFile myNewFile;
  private final LightVirtualFile myNewVirtualFile;
  private final Document myNewDocument;
  private final List<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>> myMarkers = new LinkedList<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>>();
  private EditorWindow mySplittedWindow;
  private boolean myReleased;

  QuickEditHandler(Project project, PsiFile injectedFile, final PsiFile origFile, Editor editor, QuickEditAction action) {
    myProject = project;
    myInjectedFile = injectedFile;
    myEditor = editor;
    myAction = action;
    myOrigDocument = editor.getDocument();
    final Place shreds = InjectedLanguageUtil.getShreds(myInjectedFile);
    final FileType fileType = injectedFile.getFileType();
    final Language language = injectedFile.getLanguage();

    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    final String text = InjectedLanguageManager.getInstance(project).getUnescapedText(injectedFile);
    final String newFileName =
      StringUtil.notNullize(language.getDisplayName(), "Injected") + " Fragment " + "(" +
      origFile.getName() + ":" + shreds.get(0).getHost().getTextRange().getStartOffset() + ")" + "." + fileType.getDefaultExtension();
    myNewFile = factory.createFileFromText(newFileName, language, text, true, true);
    myNewVirtualFile = (LightVirtualFile)myNewFile.getVirtualFile();
    assert myNewVirtualFile != null;
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    myNewFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, smartPointerManager.createSmartPsiElementPointer(origFile));
    myNewDocument = PsiDocumentManager.getInstance(project).getDocument(myNewFile);
    assert myNewDocument != null;
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(myNewDocument, new ReadonlyFragmentModificationHandler() {
      @Override
      public void handle(final ReadOnlyFragmentModificationException e) {
        //nothing
      }
    });
    myOrigDocument.addDocumentListener(this);
    myNewDocument.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            commitToOriginal();
          }
        });
      }
    });
    EditorFactory editorFactory = EditorFactory.getInstance();
    assert editorFactory != null;
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {

      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        if (event.getEditor().getDocument() == myNewDocument) {
          final EditorActionHandler editorEscape = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE);
          new AnAction() {
            @Override
            public void update(AnActionEvent e) {
              Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
              e.getPresentation().setEnabled(
                editor != null && LookupManager.getActiveLookup(editor) == null &&
                TemplateManager.getInstance(myProject).getActiveTemplate(editor) == null &&
                (editorEscape == null || !editorEscape.isEnabled(editor, e.getDataContext())));
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
              closeEditor();
            }
          }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, event.getEditor().getContentComponent());
        }
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor().getDocument() == myNewDocument) {
          Disposer.dispose(QuickEditHandler.this);
          myReleased = true;
          myOrigDocument.removeDocumentListener(QuickEditHandler.this);
          myInjectedFile.putUserData(QuickEditAction.QUICK_EDIT_HANDLER, null);
        }
      }
    }, this);
    initMarkers(shreds);
  }

  public boolean isValid() {
    return myNewVirtualFile.isValid() && myInjectedFile.isValid();
  }

  public void navigate(int injectedOffset) {
    if (myAction.isShowInBalloon()) {
      Ref<Balloon> ref = Ref.create(null);
      final JComponent component = myAction.createBalloonComponent(myNewFile, ref);
      if (component != null) {
        final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(component)
          .setShadow(true)
          .setAnimationCycle(0)
          .setHideOnClickOutside(true)
          .setHideOnKeyOutside(true)
          .setHideOnAction(false)
          .setFillColor(UIUtil.getControlColor())
          .createBalloon();
        ref.set(balloon);
        Disposer.register(myNewFile.getProject(), balloon);
        final Balloon.Position position = QuickEditAction.getBalloonPosition(myEditor);
        RelativePoint point = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
        if (position == Balloon.Position.above) {
          final Point p = point.getPoint();
          point = new RelativePoint(point.getComponent(), new Point(p.x, p.y - myEditor.getLineHeight()));
        }
        balloon.show(point, position);
      }
    } else {
      final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(myProject);
      final FileEditor[] editors = fileEditorManager.getEditors(myNewVirtualFile);
      if (editors.length == 0) {
        final EditorWindow curWindow = fileEditorManager.getCurrentWindow();
        mySplittedWindow = curWindow.split(SwingConstants.HORIZONTAL, false, myNewVirtualFile, true);
      }
      fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myNewVirtualFile, injectedOffset), true);
    }
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    closeEditor();
  }

  private void closeEditor() {
    boolean unsplit = false;
    if (mySplittedWindow != null && !mySplittedWindow.isDisposed()) {
      final EditorWithProviderComposite[] editors = mySplittedWindow.getEditors();
      if (editors.length == 1 && Comparing.equal(editors[0].getFile(), myNewVirtualFile)) {
        unsplit = true;
      }
    }
    FileEditorManager.getInstance(myProject).closeFile(myNewVirtualFile);
    if (unsplit) {
      for (EditorWindow editorWindow : mySplittedWindow.findSiblings()) {
        editorWindow.unsplit(true);
      }
    }
  }

  public void initMarkers(final Place shreds) {
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      final RangeMarker rangeMarker = myNewDocument.createRangeMarker(
        shred.getRange().getStartOffset() + shred.getPrefix().length(),
        shred.getRange().getEndOffset() - shred.getSuffix().length());
      final TextRange rangeInsideHost = shred.getRangeInsideHost();
      final RangeMarker origMarker =
        myOrigDocument.createRangeMarker(rangeInsideHost.shiftRight(shred.getHost().getTextRange().getStartOffset()));
      SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer = smartPointerManager.createSmartPsiElementPointer(shred.getHost());
      myMarkers.add(Trinity.<RangeMarker, RangeMarker, SmartPsiElementPointer>create(origMarker, rangeMarker, elementPointer));
    }
    for (Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> markers : myMarkers) {
      markers.first.setGreedyToLeft(true);
      markers.second.setGreedyToLeft(true);
      markers.first.setGreedyToRight(true);
      markers.second.setGreedyToRight(true);
    }
    int curOffset = 0;
    for (Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> markerPair : myMarkers) {
      final RangeMarker marker = markerPair.second;
      final int start = marker.getStartOffset();
      final int end = marker.getEndOffset();
      if (curOffset < start) {
        final RangeMarker rangeMarker = myNewDocument.createGuardedBlock(curOffset, start);
        if (curOffset == 0) rangeMarker.setGreedyToLeft(true);
      }
      curOffset = end;
    }
    if (curOffset < myNewDocument.getTextLength()) {
      final RangeMarker rangeMarker = myNewDocument.createGuardedBlock(curOffset, myNewDocument.getTextLength());
      rangeMarker.setGreedyToRight(true);
    }
  }

  private void commitToOriginal() {
    if (!isValid()) return;
    final PsiFile origFile = (PsiFile)myNewFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT).getElement();
    if (!myReleased) myOrigDocument.removeDocumentListener(this);
    try {
      new WriteCommandAction.Simple(myProject, origFile) {
        @Override
        protected void run() throws Throwable {
          PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(new Runnable() {
            @Override
            public void run() {
              commitToOriginalInner();
            }
          });
        }
      }.execute();
    }
    finally {
      if (!myReleased) myOrigDocument.addDocumentListener(this);
    }
  }

  private void commitToOriginalInner() {
    final String text = myNewDocument.getText();
    final Map<PsiLanguageInjectionHost, Set<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>>> map = ContainerUtil
      .classify(myMarkers.iterator(),
                new Convertor<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer>, PsiLanguageInjectionHost>() {
                  @Override
                  public PsiLanguageInjectionHost convert(final Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> o) {
                    final PsiElement element = o.third.getElement();
                    return (PsiLanguageInjectionHost)element;
                  }
                });
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    documentManager.commitDocument(myOrigDocument); // commit here and after each manipulator update
    int localInsideFileCursor = 0;
    for (PsiLanguageInjectionHost host : map.keySet()) {
      if (host == null) continue;
      String hostText = host.getText();
      ProperTextRange insideHost = null;
      StringBuilder sb = new StringBuilder();
      for (Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer> entry : map.get(host)) {
        RangeMarker origMarker = entry.first;
        int hostOffset = host.getTextRange().getStartOffset();
        ProperTextRange localInsideHost = new ProperTextRange(origMarker.getStartOffset() - hostOffset, origMarker.getEndOffset() - hostOffset);
        RangeMarker rangeMarker = entry.second;
        ProperTextRange localInsideFile = new ProperTextRange(Math.max(localInsideFileCursor, rangeMarker.getStartOffset()), rangeMarker.getEndOffset());
        if (insideHost != null) {
          //append unchanged inter-markers fragment
          sb.append(hostText.substring(insideHost.getEndOffset(), localInsideHost.getStartOffset()));
        }
        sb.append(localInsideFile.getEndOffset() <= text.length() && !localInsideFile.isEmpty()? localInsideFile.substring(text) : "");
        localInsideFileCursor = localInsideFile.getEndOffset();
        insideHost = insideHost == null ? localInsideHost : insideHost.union(localInsideHost);
      }
      assert insideHost != null;
      ElementManipulators.getManipulator(host).handleContentChange(host, insideHost, sb.toString());
      documentManager.commitDocument(myOrigDocument);
    }
  }

  @Override
  public void dispose() {
    // noop
  }

  @TestOnly
  public PsiFile getNewFile() {
    return myNewFile;
  }
}
