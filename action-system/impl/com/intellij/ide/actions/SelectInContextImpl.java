package com.intellij.ide.actions;

import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.SelectInContext;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
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
import com.intellij.psi.jsp.JspFile;
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

    SelectInContext selectInContext = (SelectInContext)dataContext.getData(SelectInContext.DATA_CONTEXT_ID);
    if (selectInContext == null) {
      selectInContext = createPsiContext(event);
    }

    if (selectInContext == null) {
      Navigatable descriptor = DataKeys.NAVIGATABLE.getData(dataContext);
      if (descriptor instanceof OpenFileDescriptor) {
        final VirtualFile file = ((OpenFileDescriptor)descriptor).getFile();
        if (file.isValid()) {
          Project project = DataKeys.PROJECT.getData(dataContext);
          selectInContext = OpenFileDescriptorContext.create(project, file);
        }
      }
    }

    if (selectInContext == null) {
      VirtualFile virtualFile = DataKeys.VIRTUAL_FILE.getData(dataContext);
      Project project = DataKeys.PROJECT.getData(dataContext);
      if (virtualFile != null && project != null) {
        return new VirtualFileSelectInContext(project, virtualFile);
      }
    }

    return selectInContext;
  }

  @Nullable
  private static SelectInContext createEditorContext(DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    final FileEditor editor = DataKeys.FILE_EDITOR.getData(dataContext);
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
    PsiElement psiElement = DataKeys.PSI_ELEMENT.getData(dataContext);
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
      return safeCast(event.getDataContext().getData(DataConstants.CONTEXT_COMPONENT), JComponent.class);
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
      if (myPsiFile instanceof JspFile) return super.getSelectorInFile();
      final int offset = myEditor.getEditor().getCaretModel().getOffset();

      if (offset >= 0 && offset < myPsiFile.getTextLength()) {
        PsiElement javaPsi = myPsiFile.getViewProvider().findElementAt(offset, StdLanguages.JAVA);
        if (javaPsi != null) return javaPsi;
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
          return FileEditorManager.getInstance(getProject()).openFile(myElementToSelect.getContainingFile().getVirtualFile(), false)[0];
        }
      };
    }

    public SimpleSelectInContext(PsiFile psiFile, PsiElement elementToSelect) {
      super(psiFile);
      myElementToSelect = elementToSelect;
    }
   }

  private static class VirtualFileSelectInContext implements SelectInContext {
    private Project myProject;
    private VirtualFile myVirtualFile;

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

