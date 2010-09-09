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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public class ChangeSignatureGestureDetector extends PsiTreeChangeAdapter implements ProjectComponent, EditorFactoryListener {
  private final Project myProject;
  private final Map<PsiFile, MyDocumentChangeAdapter> myListenerMap = new HashMap<PsiFile, MyDocumentChangeAdapter>();
  private static final Logger LOG = Logger.getInstance("#" + ChangeSignatureGestureDetector.class.getName());
  private boolean myDeaf = false;

  public ChangeSignatureGestureDetector(Project project) {
    myProject = project;
  }

  public static ChangeSignatureGestureDetector getInstance(Project project){
    return project.getComponent(ChangeSignatureGestureDetector.class);
  }

  public boolean isChangeSignatureAvailable(@NotNull PsiElement element) {
    final MyDocumentChangeAdapter adapter = myListenerMap.get(element.getContainingFile());
    if (adapter != null && adapter.getCurrentInfo() != null) {
      final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage());
      LOG.assertTrue(detector != null);
      return detector.isChangeSignatureAvailable(element, adapter.getCurrentInfo());
    }
    return false;
  }

  public void changeSignature(PsiFile file) {
    try {
      myDeaf = true;
      final MyDocumentChangeAdapter changeBean = myListenerMap.get(file);
      final ChangeInfo currentInfo = changeBean.getCurrentInfo();
      final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(currentInfo.getLanguage());
      if (detector.showDialog(currentInfo, changeBean.getInitialText())) {
        changeBean.setInitialText(null);
        changeBean.setCurrentInfo(null);
      }
    }
    finally {
      myDeaf = false;
    }
  }

  @Override
  public void projectOpened() {
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(this);
    EditorFactory.getInstance().addEditorFactoryListener(this);
  }

  @Override
  public void projectClosed() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(this);
    EditorFactory.getInstance().removeEditorFactoryListener(this);
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
  public void childReplaced(PsiTreeChangeEvent event) {
    if (myDeaf) return;
    final PsiFile file = event.getChild().getContainingFile();
    if (file != null) {
      final MyDocumentChangeAdapter changeBean = myListenerMap.get(file);
      if (changeBean != null && changeBean.getInitialText() != null) {
        final ChangeInfo info = LanguageChangeSignatureDetectors.createCurrentChangeInfo(event.getChild(), changeBean.getCurrentInfo());
        changeBean.setCurrentInfo(info);
        if (info == null) {
          changeBean.setInitialText(null);
        }
      }
    }
  }

  @Override
  public void editorCreated(EditorFactoryEvent event) {
    final Document document = event.getEditor().getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file != null) {
      final MyDocumentChangeAdapter adapter = new MyDocumentChangeAdapter();
      document.addDocumentListener(adapter);
      myListenerMap.put(file, adapter);
    }
  }

  @Override
  public void editorReleased(EditorFactoryEvent event) {
    final Document document = event.getEditor().getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    final MyDocumentChangeAdapter adapter = myListenerMap.remove(file);
    if (adapter != null) {
      document.removeDocumentListener(adapter);
    }
  }

  private class MyDocumentChangeAdapter extends DocumentAdapter {
    private String myInitialText;
    private ChangeInfo myCurrentInfo;

    public void setCurrentInfo(ChangeInfo currentInfo) {
      myCurrentInfo = currentInfo;
    }

    public void setInitialText(String initialText) {
      myInitialText = initialText;
    }

    public String getInitialText() {
      return myInitialText;
    }

    public ChangeInfo getCurrentInfo() {
      return myCurrentInfo;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      if (myDeaf) return;
      if (myInitialText == null) {
        final Document document = e.getDocument();
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        if (!documentManager.isUncommited(document)) {
          final PsiFile file = documentManager.getPsiFile(document);
          if (file != null) {
            final PsiElement element = file.findElementAt(e.getOffset());
            if (element != null) {
              final ChangeInfo info = LanguageChangeSignatureDetectors.createCurrentChangeInfo(element, myCurrentInfo);
              if (info != null) {
                myInitialText = document.getText();
                myCurrentInfo = info;
              }
            }
          }
        }
      }
    }
  }

}
