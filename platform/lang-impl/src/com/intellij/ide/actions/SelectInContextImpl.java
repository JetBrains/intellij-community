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
import java.awt.event.InputEvent;

public abstract class SelectInContextImpl implements SelectInContext {
  protected final PsiFile myPsiFile;

  protected SelectInContextImpl(PsiFile psiFile) {
    myPsiFile = psiFile;
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
      Navigatable descriptor = CommonDataKeys.NAVIGATABLE.getData(dataContext);
      if (descriptor instanceof OpenFileDescriptor) {
        final VirtualFile file = ((OpenFileDescriptor)descriptor).getFile();
        if (file.isValid()) {
          Project project = CommonDataKeys.PROJECT.getData(dataContext);
          selectInContext = OpenFileDescriptorContext.create(project, file);
        }
      }
    }

    if (selectInContext == null) {
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (virtualFile != null && project != null) {
        return new VirtualFileSelectInContext(project, virtualFile);
      }
    }

    return selectInContext;
  }

  @Nullable
  private static SelectInContext createEditorContext(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    return doCreateEditorContext(project, editor, dataContext);
  }

  public static SelectInContext createEditorContext(Project project, FileEditor editor) {
    return doCreateEditorContext(project, editor, null);
  }

  private static SelectInContext doCreateEditorContext(Project project, FileEditor editor, @Nullable DataContext dataContext) {
    if (project == null || editor == null) {
      return null;
    }
    VirtualFile file = FileEditorManagerEx.getInstanceEx(project).getFile(editor);
    if (file == null) {
      file = dataContext == null ? null : CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
      if (file == null) {
        return null;
      }
    }
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      return null;
    }
    if (editor instanceof TextEditor) {
      return new TextEditorContext((TextEditor)editor, psiFile);
    }
    else {
      StructureViewBuilder builder = editor.getStructureViewBuilder();
      StructureView structureView = builder != null ? builder.createStructureView(editor, project) : null;
      Object selectorInFile = structureView != null ? structureView.getTreeModel().getCurrentEditorElement() : null;
      if (structureView != null) Disposer.dispose(structureView);
      return new SimpleSelectInContext(psiFile, ObjectUtils.chooseNotNull(selectorInFile, psiFile));
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

    @Override
    public FileEditorProvider getFileEditorProvider() {
      return new FileEditorProvider() {
        @Override
        public FileEditor openFileEditor() {
          return myEditor;
        }
      };
    }

    @Override
    public Object getSelectorInFile() {
      if (myPsiFile.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
        return super.getSelectorInFile();
      }
      Editor editor = myEditor.getEditor();
      int offset = TargetElementUtil.adjustOffset(myPsiFile, editor.getDocument(), editor.getCaretModel().getOffset());
      PsiElement element = myPsiFile.findElementAt(offset);
      return element != null ? element : super.getSelectorInFile();
    }
  }


  private static class OpenFileDescriptorContext extends SelectInContextImpl {
    public OpenFileDescriptorContext(PsiFile psiFile) {
      super(psiFile);
    }

    @Override
    public FileEditorProvider getFileEditorProvider() {
      return new FileEditorProvider() {
        @Override
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
    private final Object mySelector;

    public SimpleSelectInContext(PsiFile psiFile, Object selector) {
      super(psiFile);
      mySelector = selector;
    }

    @Override
    public Object getSelectorInFile() {
      return mySelector;
    }

    @Override
    public FileEditorProvider getFileEditorProvider() {
      return new FileEditorProvider() {
        @Override
        public FileEditor openFileEditor() {
          final VirtualFile file = myPsiFile.getVirtualFile();
          if (file == null) {
            return null;
          }
          return ArrayUtil.getFirstElement(FileEditorManager.getInstance(getProject()).openFile(file, false));
        }
      };
    }
   }

  private static class VirtualFileSelectInContext implements SelectInContext {
    private final Project myProject;
    private final VirtualFile myVirtualFile;

    public VirtualFileSelectInContext(final Project project, final VirtualFile virtualFile) {
      myProject = project;
      myVirtualFile = virtualFile;
    }

    @Override
    @NotNull
    public Project getProject() {
      return myProject;
    }

    @Override
    @NotNull
    public VirtualFile getVirtualFile() {
      return myVirtualFile;
    }

    @Override
    @Nullable
    public Object getSelectorInFile() {
      return myVirtualFile;
    }

    @Override
    @Nullable
    public FileEditorProvider getFileEditorProvider() {
      return null;
    }
  }
}

