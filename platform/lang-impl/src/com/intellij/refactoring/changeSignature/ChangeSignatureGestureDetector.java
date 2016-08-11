/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandProcessor;
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
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class ChangeSignatureGestureDetector extends PsiTreeChangeAdapter implements EditorFactoryListener, Disposable {
  private final Map<VirtualFile, MyDocumentChangeAdapter> myListenerMap = new HashMap<>();
  private static final Logger LOG = Logger.getInstance("#" + ChangeSignatureGestureDetector.class.getName());
  private boolean myDeaf = false;
  private final FileDocumentManager myDocumentManager;
  private final PsiManager myPsiManager;
  private final FileEditorManager myFileEditorManager;
  private final Project myProject;
  private final PsiDocumentManager myPsiDocumentManager;

  public ChangeSignatureGestureDetector(final PsiDocumentManager psiDocumentManager,
                                        final FileDocumentManager documentManager,
                                        final PsiManager psiManager,
                                        final FileEditorManager fileEditorManager,
                                        final Project project) {
    myDocumentManager = documentManager;
    myPsiDocumentManager = psiDocumentManager;
    myPsiManager = psiManager;
    myFileEditorManager = fileEditorManager;
    myProject = project;
    myPsiManager.addPsiTreeChangeListener(this, this);
    EditorFactory.getInstance().addEditorFactoryListener(this, this);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        LOG.assertTrue(myListenerMap.isEmpty(), myListenerMap);
      }
    });
  }

  public static ChangeSignatureGestureDetector getInstance(Project project){
    return project.getComponent(ChangeSignatureGestureDetector.class);
  }

  public boolean isChangeSignatureAvailable(@NotNull PsiElement element) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(PsiUtilCore.getVirtualFile(element));
    if (adapter != null) {
      final ChangeInfo currentInfo = adapter.getCurrentInfo();
      if (currentInfo != null && element.equals(adapter.getInitialChangeInfo().getMethod())) {
        return true;
      }
    }
    return false;
  }

  public void dismissForElement(PsiElement method) {
    final PsiFile psiFile = method.getContainingFile();
    final ChangeInfo initialChangeInfo = getInitialChangeInfo(psiFile);
    if (initialChangeInfo != null && initialChangeInfo.getMethod() == method) {
      clearSignatureChange(psiFile);
    }
  }

  public boolean containsChangeSignatureChange(@NotNull PsiFile file) {
    return getChangeInfo(file) != null;
  }

  @Nullable
  public ChangeInfo getChangeInfo(@NotNull PsiFile file) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(file.getVirtualFile());
    return adapter != null ? adapter.getCurrentInfo() : null;
  }

  @Nullable
   public ChangeInfo getInitialChangeInfo(@NotNull PsiFile file) {
     final MyDocumentChangeAdapter adapter = myListenerMap.get(file.getVirtualFile());
     return adapter != null ? adapter.getInitialChangeInfo() : null;
   }

  public void changeSignature(PsiFile file, final boolean silently) {
    try {
      myDeaf = true;
      final MyDocumentChangeAdapter changeBean = myListenerMap.get(file.getVirtualFile());
      final ChangeInfo currentInfo = changeBean.getCurrentInfo();
      if (currentInfo != null) {
        final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(currentInfo.getLanguage());
        if (detector.performChange(currentInfo, changeBean.getInitialChangeInfo(), changeBean.getInitialText(), silently)) {
          changeBean.reinit();
        }
      }
    }
    finally {
      myDeaf = false;
    }
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
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
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    change(event.getParent());
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    change(event.getChild());
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
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
        if (editor != null && TemplateManager.getInstance(myProject).getActiveTemplate(editor) != null) return;
        final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(child.getLanguage());
        if (detector == null) return;
        if (detector.ignoreChanges(child)) return;
        final String currentSignature = detector.extractSignature(child, changeBean.getInitialChangeInfo());
        if (currentSignature == null) {
          changeBean.reinit();
        } else {
          changeBean.addSignature(currentSignature);
        }
      }
    }
  }

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
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
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    final EditorEx editor = (EditorEx)event.getEditor();
    final Document document = editor.getDocument();

    VirtualFile file = myDocumentManager.getFile(document);
    if (file == null) {
      file = editor.getVirtualFile();
    }
    if (file != null && file.isValid()) {
      for (FileEditor fileEditor : myFileEditorManager.getAllEditors(file)) {
        if (fileEditor instanceof TextEditor && ((TextEditor)fileEditor).getEditor() != editor) {
          return;
        }
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

  @Nullable
  private static ChangeInfo createCurrentChangeInfo(String signature, @NotNull ChangeInfo currentInfo, String initialName) {
    final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(currentInfo.getLanguage());
    return detector != null ? detector.createNextChangeInfo(signature, currentInfo, initialName) : null;
  }

  @Nullable
  private static ChangeInfo createInitialChangeInfo(@NotNull PsiElement element) {
    final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
    return detector != null ? detector.createInitialChangeInfo(element) : null;
  }

  private class MyDocumentChangeAdapter extends DocumentAdapter {
    private final @NonNls String [] COMMANDS = {
      EditorBundle.message("paste.command.name"), 
      EditorBundle.message("typing.in.editor.command.name"),
      ActionsBundle.message("action.MoveElementLeft.text"),
      ActionsBundle.message("action.MoveElementRight.text"),
      "Cut",
      LanguageChangeSignatureDetector.MOVE_PARAMETER
    };

    private String myInitialText;
    private String myInitialName;
    private ChangeInfo myInitialChangeInfo;
    private ChangeInfo myCurrentInfo;

    private final List<String> mySignatures = new ArrayList<>();

    public MyDocumentChangeAdapter() {
    }

    public String getInitialText() {
      return myInitialText;
    }

    public ChangeInfo getCurrentInfo() {
      if (myInitialChangeInfo == null) return null;
      synchronized (mySignatures) {
        if (!mySignatures.isEmpty()) {
          if (myCurrentInfo == null) {
            myCurrentInfo = myInitialChangeInfo;
          }

          for (String signature : mySignatures) {
            if (myInitialText.equals(signature)) {
              reinit();
              break;
            }
            try {
              myCurrentInfo = createCurrentChangeInfo(signature, myCurrentInfo, myInitialName);
              if (myCurrentInfo == null) {
                reinit();
                break;
              }
            }
            catch (IncorrectOperationException ignore) {
            }
          }
          mySignatures.clear();
        }
      }
      if (myCurrentInfo instanceof RenameChangeInfo) return myCurrentInfo;
      return myInitialChangeInfo != null && myInitialChangeInfo.equals(myCurrentInfo) ? null : myCurrentInfo;
    }

    public void addSignature(String signature) {
      synchronized (mySignatures) {
        if (!mySignatures.contains(signature)) {
          mySignatures.add(signature);
        }
      }
    }

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      if (myDeaf) return;
      if (DumbService.isDumb(myProject)) return;
      if (myInitialText == null) {
        final Document document = e.getDocument();
        final PsiDocumentManager documentManager = myPsiDocumentManager;

        if (!documentManager.isUncommited(document)) {
          final CommandProcessor processor = CommandProcessor.getInstance();
          final String currentCommandName = processor.getCurrentCommandName();

          if (!isPredefinedCommand(processor, currentCommandName)) return;

          final PsiFile file = documentManager.getPsiFile(document);
          if (file != null) {
            final PsiElement element = file.findElementAt(e.getOffset());
            if (element != null) {
              final ChangeInfo info = createInitialChangeInfo(element);
              if (info != null) {
                final PsiElement method = info.getMethod();
                final TextRange textRange = method.getTextRange();
                if (document.getTextLength() <= textRange.getEndOffset()) return;
                if (method instanceof PsiNameIdentifierOwner) {
                  myInitialName = ((PsiNameIdentifierOwner)method).getName();
                }
                myInitialText =  document.getText(textRange);
                myInitialChangeInfo = info;
              }
            }
          }
        }
      }
    }

    private boolean isPredefinedCommand(CommandProcessor processor, String currentCommandName) {
      if (Comparing.equal(EditorActionUtil.DELETE_COMMAND_GROUP, processor.getCurrentCommandGroupId())) {
        return true;
      }

      for (String commandName : COMMANDS) {
        if (Comparing.strEqual(commandName, currentCommandName)){
          return true;
        }
      }
      return false;
    }

    public ChangeInfo getInitialChangeInfo() {
      return myInitialChangeInfo;
    }

    public void reinit() {
      synchronized (mySignatures) {
        mySignatures.clear();
      }
      myInitialText = null;
      myInitialName = null;
      myInitialChangeInfo = null;
      myCurrentInfo = null;
    }
  }

  @Override
  public void dispose() {

  }
}
