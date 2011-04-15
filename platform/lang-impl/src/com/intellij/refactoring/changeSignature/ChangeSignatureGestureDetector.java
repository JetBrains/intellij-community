/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class ChangeSignatureGestureDetector extends PsiTreeChangeAdapter implements ProjectComponent, EditorFactoryListener {
  private final Map<VirtualFile, MyDocumentChangeAdapter> myListenerMap = new HashMap<VirtualFile, MyDocumentChangeAdapter>();
  private static final Logger LOG = Logger.getInstance("#" + ChangeSignatureGestureDetector.class.getName());
  private boolean myDeaf = false;
  private final FileDocumentManager myDocumentManager;
  private final PsiManager myPsiManager;
  private final FileEditorManager myFileEditorManager;
  private final Project myProject;
  private final TemplateManager myTemplateManager;
  private final PsiDocumentManager myPsiDocumentManager;

  public ChangeSignatureGestureDetector(final PsiDocumentManager psiDocumentManager,
                                        final FileDocumentManager documentManager,
                                        final PsiManager psiManager,
                                        final FileEditorManager fileEditorManager,
                                        final TemplateManager templateManager,
                                        final Project project) {
    myDocumentManager = documentManager;
    myPsiDocumentManager = psiDocumentManager;
    myPsiManager = psiManager;
    myFileEditorManager = fileEditorManager;
    myProject = project;
    myTemplateManager = templateManager;
  }

  public static ChangeSignatureGestureDetector getInstance(Project project){
    return project.getComponent(ChangeSignatureGestureDetector.class);
  }

  public boolean isChangeSignatureAvailable(@NotNull PsiElement element) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(PsiUtilBase.getVirtualFile(element));
    if (adapter != null && adapter.getCurrentInfo() != null) {
      final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
      return detector != null && detector.isChangeSignatureAvailable(element, adapter.getCurrentInfo());
    }
    return false;
  }

   @Nullable
   public String getChangeSignatureAcceptText(@NotNull PsiElement element) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(PsiUtilBase.getVirtualFile(element));
    if (adapter != null && adapter.getCurrentInfo() != null) {
      final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
      final ChangeInfo currentInfo = adapter.getCurrentInfo();
      if (detector != null && detector.isChangeSignatureAvailable(element, currentInfo)) {
        return currentInfo instanceof RenameChangeInfo ? ChangeSignatureDetectorAction.NEW_NAME
                                                       : ChangeSignatureDetectorAction.CHANGE_SIGNATURE;
      }
    }
    return null;
  }

  public boolean containsChangeSignatureChange(@NotNull PsiFile file) {
    return getChangeInfo(file) != null;
  }

  @Nullable
  public ChangeInfo getChangeInfo(@NotNull PsiFile file) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(file.getVirtualFile());
    return adapter != null ? adapter.getCurrentInfo() : null;
  }

  public void changeSignature(PsiFile file, final boolean silently) {
    try {
      myDeaf = true;
      final MyDocumentChangeAdapter changeBean = myListenerMap.get(file.getVirtualFile());
      final ChangeInfo currentInfo = changeBean.getCurrentInfo();
      if (currentInfo != null) {
        final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(currentInfo.getLanguage());
        if (detector.accept(currentInfo, changeBean.getInitialText(), silently)) {
          changeBean.reinit();
        }
      }
    }
    finally {
      myDeaf = false;
    }
  }

  @Override
  public void projectOpened() {
    myPsiManager.addPsiTreeChangeListener(this);
    EditorFactory.getInstance().addEditorFactoryListener(this, myProject);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        myPsiManager.removePsiTreeChangeListener(ChangeSignatureGestureDetector.this);
        LOG.assertTrue(myListenerMap.isEmpty(), myListenerMap);
      }
    });
  }

  @Override
  public void projectClosed() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ChangeSignatureGestureDetector";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void beforeChildRemoval(PsiTreeChangeEvent event) {
    final PsiElement child = event.getChild();
    if (child instanceof PsiFile) {
      final PsiFile psiFile = (PsiFile)child;
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && myListenerMap.containsKey(virtualFile)) {
        final Document document = myDocumentManager.getDocument(virtualFile);
        if (document != null) {
          removeDocListener(document, virtualFile);
        } else {
          myListenerMap.remove(virtualFile);
        }
      }
    }
  }

  @Override
  public void childRemoved(PsiTreeChangeEvent event) {
    change(event.getParent());
  }

  @Override
  public void childReplaced(PsiTreeChangeEvent event) {
    change(event.getChild());
  }

  @Override
  public void childAdded(PsiTreeChangeEvent event) {
    change(event.getChild());
  }

  private void change(PsiElement child) {
    if (myDeaf) return;
    if (child == null || !child.isValid()) return;
    final PsiFile file = child.getContainingFile();
    if (file != null) {
      final MyDocumentChangeAdapter changeBean = myListenerMap.get(file.getVirtualFile());
      if (changeBean != null && changeBean.getInitialText() != null) {
        final Editor editor = myFileEditorManager.getSelectedTextEditor();
        if (editor != null && myTemplateManager.getActiveTemplate(editor) != null) return;
        final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(child.getLanguage());
        if (detector == null) return;
        if (detector.ignoreChanges(child)) return;
        final ChangeInfo info = LanguageChangeSignatureDetectors.createCurrentChangeInfo(child, changeBean.getInitialChangeInfo());
        if (info == null) {
          changeBean.reinit();
        } else if (!info.equals(changeBean.getInitialChangeInfo())) {
          changeBean.setCurrentInfo(info);
        } else {
          changeBean.setCurrentInfo(null);
        }
      }
    }
  }

  @Override
  public void editorCreated(EditorFactoryEvent event) {
    final Editor editor = event.getEditor();
    if (editor.getProject() != myProject) return;
    addDocListener(editor.getDocument());
  }

  public void addDocListener(Document document) {
    if (document == null) return;
    final VirtualFile file = myDocumentManager.getFile(document);
    if (file != null && file.isValid() && !myListenerMap.containsKey(file)) {
      final PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile == null || !psiFile.isPhysical()) return;
      final MyDocumentChangeAdapter adapter = new MyDocumentChangeAdapter();
      document.addDocumentListener(adapter);
      myListenerMap.put(file, adapter);
    }
  }

  @Override
  public void editorReleased(EditorFactoryEvent event) {
    final EditorEx editor = (EditorEx)event.getEditor();
    final Document document = editor.getDocument();

    VirtualFile file = myDocumentManager.getFile(document);
    if (file == null) {
      file = editor.getVirtualFile();
    }
    if (file != null && file.isValid()) {
      if (myFileEditorManager.isFileOpen(file)) {
        return;
      }
    }
    removeDocListener(document, file);
  }

  public void removeDocListener(Document document, VirtualFile file) {
    final MyDocumentChangeAdapter adapter = myListenerMap.remove(file);
    if (adapter != null) {
      document.removeDocumentListener(adapter);
    }
  }

  public void clearSignatureChange(PsiFile file) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(file.getVirtualFile());
    if (adapter != null) {
      adapter.reinit();
    }
  }

  private class MyDocumentChangeAdapter extends DocumentAdapter {
    private String myInitialText;
    private ChangeInfo myInitialChangeInfo;
    private ChangeInfo myCurrentInfo;

    public void setCurrentInfo(ChangeInfo currentInfo) {
      myCurrentInfo = currentInfo;
    }

    public String getInitialText() {
      return myInitialText;
    }

    public ChangeInfo getCurrentInfo() {
      return myCurrentInfo;
    }

    private final @NonNls String PASTE_COMMAND_NAME = EditorBundle.message("paste.command.name");
    private final @NonNls String TYPING_COMMAND_NAME = EditorBundle.message("typing.in.editor.command.name");

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      if (myDeaf) return;
      if (myInitialText == null) {
        final Document document = e.getDocument();
        final PsiDocumentManager documentManager = myPsiDocumentManager;

        if (!documentManager.isUncommited(document)) {
          final CommandProcessor processor = CommandProcessor.getInstance();
          final String currentCommandName = processor.getCurrentCommandName();

          if (!Comparing.strEqual(TYPING_COMMAND_NAME, currentCommandName) &&
              !Comparing.strEqual(PASTE_COMMAND_NAME, currentCommandName) &&
              !Comparing.strEqual("Cut", currentCommandName) &&
              !Comparing.strEqual(LanguageChangeSignatureDetector.MOVE_PARAMETER, currentCommandName) &&
              !Comparing.equal(EditorActionUtil.DELETE_COMMAND_GROUP, processor.getCurrentCommandGroupId())) {
            return;
          }
          final PsiFile file = documentManager.getPsiFile(document);
          if (file != null) {
            final PsiElement element = file.findElementAt(e.getOffset());
            if (element != null) {
              final ChangeInfo info = LanguageChangeSignatureDetectors.createCurrentChangeInfo(element, myCurrentInfo);
              if (info != null) {
                final TextRange textRange = info.getMethod().getTextRange();
                if (document.getTextLength() <= textRange.getEndOffset()) return;
                myInitialText =  document.getText(textRange);
                myInitialChangeInfo = info;
              }
            }
          }
        }
      }
    }

    public ChangeInfo getInitialChangeInfo() {
      return myInitialChangeInfo;
    }

    public void reinit() {
      myInitialText = null;
      myInitialChangeInfo = null;
      myCurrentInfo = null;
    }
  }

}
