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

import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.SelectInContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;

abstract class SelectInContextImpl implements SelectInContext {
  protected final PsiFile myPsiFile;

  protected SelectInContextImpl(PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  @NotNull
  public Project getProject() {
    return myPsiFile.getProject();
  }


  @NotNull
  public VirtualFile getVirtualFile() {
    final VirtualFile vFile = myPsiFile.getVirtualFile();
    assert vFile != null;
    return vFile;
  }

  public Object getSelectorInFile() {
    return myPsiFile;
  }

  @Nullable
  public static SelectInContext createContext(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();

    SelectInContext result = createEditorContext(dataContext);
    if (result != null) {
      return result;
    }

    JComponent sourceComponent = getEventComponent(event);
    if (sourceComponent == null) {
      return null;
    }

    SelectInContext selectInContext = SelectInContext.DATA_KEY.getData(dataContext);
    if (selectInContext == null) {
      selectInContext = createPsiContext(event);
    }

    if (selectInContext == null) {
      Navigatable descriptor = PlatformDataKeys.NAVIGATABLE.getData(dataContext);
      if (descriptor instanceof OpenFileDescriptor) {
        final VirtualFile file = ((OpenFileDescriptor)descriptor).getFile();
        if (file.isValid()) {
          Project project = PlatformDataKeys.PROJECT.getData(dataContext);
          selectInContext = OpenFileDescriptorContext.create(project, file);
        }
      }
    }

    if (selectInContext == null) {
      VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (virtualFile != null && project != null) {
        return new VirtualFileSelectInContext(project, virtualFile);
      }
    }

    return selectInContext;
  }

  @Nullable
  private static SelectInContext createEditorContext(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    if (project == null || editor == null) {
      return null;
    }
    VirtualFile file = FileEditorManagerEx.getInstanceEx(project).getFile(editor);
    if (file == null) {
      return null;
    }
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      return null;
    }
    if (editor instanceof TextEditor) {
      return new TextEditorContext((TextEditor)editor, psiFile);
    }
    else {
      return new SimpleSelectInContext(psiFile);
    }
  }

  @Nullable
  private static SelectInContext createPsiContext(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    PsiElement psiElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiElement == null || !psiElement.isValid()) {
      return null;
    }
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    return new SimpleSelectInContext(psiFile, psiElement);
  }

  @Nullable
  private static JComponent getEventComponent(AnActionEvent event) {
    InputEvent inputEvent = event.getInputEvent();
    Object source;
    if (inputEvent != null && (source = inputEvent.getSource()) instanceof JComponent) {
      return (JComponent)source;
    }
    else {
      return safeCast(PlatformDataKeys.CONTEXT_COMPONENT.getData(event.getDataContext()), JComponent.class);
    }
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  private static <T> T safeCast(final Object obj, final Class<T> expectedClass) {
    if (expectedClass.isInstance(obj)) return (T)obj;
    return null;
  }

  private static class TextEditorContext extends SelectInContextImpl {
    private final TextEditor myEditor;

    public TextEditorContext(TextEditor editor, PsiFile psiFile) {
      super(psiFile);
      myEditor = editor;
    }

    public FileEditorProvider getFileEditorProvider() {
      return new FileEditorProvider() {
        public FileEditor openFileEditor() {
          return myEditor;
        }
      };
    }

    public Object getSelectorInFile() {
      if (myPsiFile.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
        return super.getSelectorInFile();
      }
      final int offset = myEditor.getEditor().getCaretModel().getOffset();

      if (offset >= 0 && offset < myPsiFile.getTextLength()) {
        return myPsiFile.findElementAt(offset);
      }
      return super.getSelectorInFile();
    }
  }


  private static class OpenFileDescriptorContext extends SelectInContextImpl {
    public OpenFileDescriptorContext(PsiFile psiFile) {
      super(psiFile);
    }

    public FileEditorProvider getFileEditorProvider() {
      return new FileEditorProvider() {
        public FileEditor openFileEditor() {
          return FileEditorManager.getInstance(getProject()).openFile(getVirtualFile(), false)[0];
        }
      };
    }

    @Nullable
    public static SelectInContext create(Project project, VirtualFile file) {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null) return null;
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile == null) return null;
      return new OpenFileDescriptorContext(psiFile);
    }
  }

  private static class SimpleSelectInContext extends SelectInContextImpl {
    private final PsiElement myElementToSelect;

    public SimpleSelectInContext(PsiFile psiFile) {
      this(psiFile, psiFile);
    }

    public FileEditorProvider getFileEditorProvider() {
      return new FileEditorProvider() {
        public FileEditor openFileEditor() {
          final VirtualFile file = myElementToSelect.getContainingFile().getVirtualFile();
          if (file == null) {
            return null;
          }
          final FileEditor[] fileEditors = FileEditorManager.getInstance(getProject()).openFile(file, false);
          return fileEditors.length > 0 ? fileEditors[0] : null;
        }
      };
    }

    public SimpleSelectInContext(PsiFile psiFile, PsiElement elementToSelect) {
      super(psiFile);
      myElementToSelect = elementToSelect;
    }
   }

  private static class VirtualFileSelectInContext implements SelectInContext {
    private final Project myProject;
    private final VirtualFile myVirtualFile;

    public VirtualFileSelectInContext(final Project project, final VirtualFile virtualFile) {
      myProject = project;
      myVirtualFile = virtualFile;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public VirtualFile getVirtualFile() {
      return myVirtualFile;
    }

    @Nullable
    public Object getSelectorInFile() {
      return myVirtualFile;
    }

    @Nullable
    public FileEditorProvider getFileEditorProvider() {
      return null;
    }
  }
}

