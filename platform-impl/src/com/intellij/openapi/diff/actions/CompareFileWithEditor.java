package com.intellij.openapi.diff.actions;

import com.intellij.ide.DataAccessor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.ex.DiffContentFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.ElementPresentation;

public class CompareFileWithEditor extends BaseDiffAction {
  private static final DataAccessor<Document> EDITING_DOCUMENT = new DataAccessor<Document>() {
    public Document getImpl(DataContext dataContext) throws NoDataException {
      VirtualFile[] selectedFiles = FILE_EDITOR_MANAGER.getNotNull(dataContext).getSelectedFiles();
      if (selectedFiles.length == 0) return null;
      if (!DiffContentUtil.isTextFile(selectedFiles[0])) return null;
      return FileDocumentManager.getInstance().getDocument(selectedFiles[0]);
    }
  };

  public void update(AnActionEvent e) {
    boolean enabled = true;
    Presentation presentation = e.getPresentation();
    try {
      FileEditorContents diffData = getDiffData(e.getDataContext());
      presentation.setText(
        DiffBundle.message("diff.compare.element.type.with.editor.action.name", diffData.getElementPresentation().getKind().getTypeNum()));
    }
    catch (DataAccessor.NoDataException e1) {
      presentation.setText(DiffBundle.message("compare.with.editor.action.name"));
      enabled = false;
    }
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }

  protected FileEditorContents getDiffData(DataContext dataContext) throws DataAccessor.NoDataException {
    if (!checkSelection(dataContext)) throw new DataAccessor.NoDataException(
      DiffBundle.message("diff.file.whith.editor.action.wrong.selection.error.message"));
    Project project = DataAccessor.PROJECT.getNotNull(dataContext);
    PsiElement element = PRIMARY_SOURCE.getNotNull(dataContext);
    PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) element = psiFile;
    Document document = EDITING_DOCUMENT.getNotNull(dataContext);
    if (isSameFile(document, element)) throw new DataAccessor.NoDataException(
      DiffBundle.message("diff.file.whith.editor.action.same.file.error.message"));
    DiffContent diffContent = DiffContentFactory.fromPsiElement(element);
    if (diffContent == null || diffContent.getDocument() == null)
      throw new DataAccessor.NoDataException(DiffBundle.message("diff.file.whith.editor.action.no.content.error.message"));
    return new FileEditorContents(document, element, project);
  }

  private boolean isSameFile(Document document, PsiElement element) {
    VirtualFile documentFile = FileDocumentManager.getInstance().getFile(document);
    PsiFile elementPsiFile = element.getContainingFile();
    VirtualFile elementFile = elementPsiFile != null ? elementPsiFile.getVirtualFile() : null;

    return documentFile != null && documentFile.isValid() &&
           documentFile.equals(elementFile);
  }

  private boolean checkSelection(DataContext dataContext) {
    VirtualFile[] virtualFiles = DataAccessor.VIRTUAL_FILE_ARRAY.from(dataContext);
    if (virtualFiles != null && virtualFiles.length != 1) return false;
    PsiElement[] psiElements = DataAccessor.PSI_ELEMENT_ARRAY.from(dataContext);
    return !(psiElements != null && psiElements.length != 1);
  }

  protected void disableAction(Presentation presentation) {
    presentation.setVisible(false);
  }

  private static class FileEditorContents extends DiffRequest {
    private final PsiElement myPsiElement;
    private final Document myDocument;

    public FileEditorContents(Document document, PsiElement psiElement, Project project) {
      super(project);
      myDocument = document;
      myPsiElement = psiElement;
    }

    public String[] getContentTitles() {
      VirtualFile documentFile = getDocumentFile(myDocument);
      String documentTitle = documentFile != null ? ElementPresentation.forVirtualFile(documentFile).getNameWithFQComment() : DiffBundle
        .message("diff.content.editor.content.title");
      return new String[]{getElementPresentation().getNameWithFQComment(), documentTitle};
    }

    protected ElementPresentation getElementPresentation() {
      return ElementPresentation.forElement(myPsiElement);
    }

    public DiffContent[] getContents() {
      return new DiffContent[]{DiffContentFactory.fromPsiElement(myPsiElement), DocumentContent.fromDocument(getProject(), myDocument)};
    }

    public String getWindowTitle() {
      if (isEditorContent(myDocument)) {
        return DiffBundle.message("diff.element.qualified.name.vs.editor.dialog.title", getElementPresentation().getQualifiedName());
      } else {
        return DiffBundle.message("diff.element.qualified.name.vs.file.dialog.title", getElementPresentation().getQualifiedName(),
                                  getDocumentFile(myDocument));
      }

    }
  }
}
