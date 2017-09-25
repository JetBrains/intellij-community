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

package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.FileEditorSelectInContext;
import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

public class SelectInContextImpl implements SelectInContext {
  private final PsiFile myPsiFile;
  private final Object mySelector;

  private SelectInContextImpl(@NotNull PsiFile psiFile, @Nullable Object selector) {
    myPsiFile = psiFile;
    mySelector = selector;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myPsiFile.getProject();
  }


  @Override
  @NotNull
  public VirtualFile getVirtualFile() {
    return myPsiFile.getViewProvider().getVirtualFile();
  }

  @Override
  public Object getSelectorInFile() {
    return ObjectUtils.notNull(mySelector, myPsiFile);
  }

  @Nullable
  @Override
  public FileEditorProvider getFileEditorProvider() {
    return new FileEditorProvider() {
      @Override
      public FileEditor openFileEditor() {
        return ArrayUtil.getFirstElement(FileEditorManager.getInstance(getProject()).openFile(getVirtualFile(), false));
      }
    };
  }

  @Nullable
  public static SelectInContext createContext(AnActionEvent event) {
    Project project = event.getProject();
    FileEditor editor = event.getData(PlatformDataKeys.FILE_EDITOR);
    VirtualFile virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE);

    SelectInContext result = createEditorContext(project, editor, virtualFile);
    if (result != null) {
      return result;
    }

    JComponent sourceComponent = getEventComponent(event);
    if (sourceComponent == null) {
      return null;
    }

    result = event.getData(SelectInContext.DATA_KEY);
    if (result != null) {
      return result;
    }

    result = createPsiContext(event);
    if (result != null) {
      return result;
    }

    Navigatable descriptor = event.getData(CommonDataKeys.NAVIGATABLE);
    result = descriptor instanceof OpenFileDescriptor ? createDescriptorContext((OpenFileDescriptor)descriptor) : null;
    if (result != null) {
      return result;
    }

    if (virtualFile != null && project != null) {
      return new FileSelectInContext(project, virtualFile);
    }

    return null;
  }

  @Nullable
  private static SelectInContext createDescriptorContext(OpenFileDescriptor descriptor) {
    VirtualFile file = descriptor.getFile();
    Document document = !file.isValid() ? null : FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return null;
    PsiFile psiFile = PsiDocumentManager.getInstance(descriptor.getProject()).getPsiFile(document);
    if (psiFile == null) return null;
    return new SelectInContextImpl(psiFile, null) {
      @Override
      public FileEditorProvider getFileEditorProvider() {
        return new FileEditorProvider() {
          @Override
          public FileEditor openFileEditor() {
            descriptor.navigate(false);
            FileEditor[] allEditors = FileEditorManager.getInstance(descriptor.getProject()).getAllEditors(descriptor.getFile());
            return ArrayUtil.getFirstElement(allEditors);
          }
        };
      }
    };
  }

  private static SelectInContext createEditorContext(@Nullable Project project,
                                                     @Nullable FileEditor editor,
                                                     @Nullable VirtualFile contextFile) {
    if (project == null || editor == null) {
      return null;
    }

    VirtualFile file = FileEditorManagerEx.getInstanceEx(project).getFile(editor);
    if (file == null) {
      file = contextFile;
    }

    PsiFile psiFile = file == null ? null : PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      return null;
    }

    if (editor instanceof TextEditor) {
      return new FileEditorSelectInContext(editor, psiFile) {
        @Override
        public Object getSelectorInFile() {
          PsiFile file = getPsiFile();
          if (file.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
            return super.getSelectorInFile();
          }
          Editor editor = ((TextEditor)getFileEditor()).getEditor();
          int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
          PsiElement element = file.findElementAt(offset);
          return element != null ? element : super.getSelectorInFile();
        }
      };
    }
    else {
      StructureViewBuilder builder = editor.getStructureViewBuilder();
      StructureView structureView = builder != null ? builder.createStructureView(editor, project) : null;
      Object selectorInFile = structureView != null ? structureView.getTreeModel().getCurrentEditorElement() : null;
      if (structureView != null) Disposer.dispose(structureView);
      return new SelectInContextImpl(psiFile, ObjectUtils.chooseNotNull(selectorInFile, psiFile));
    }
  }

  @Nullable
  private static SelectInContext createPsiContext(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiElement == null || !psiElement.isValid()) {
      return null;
    }
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    return new SelectInContextImpl(psiFile, psiElement);
  }

  @Nullable
  private static JComponent getEventComponent(AnActionEvent event) {
    InputEvent inputEvent = event.getInputEvent();
    Object source;
    if (inputEvent != null && (source = inputEvent.getSource()) instanceof JComponent) {
      return (JComponent)source;
    }
    else {
      Component component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      return component instanceof JComponent ? (JComponent)component : null;
    }
  }
}

