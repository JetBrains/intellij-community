package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class CompareFileWithEditor extends BaseDiffAction {
  @Nullable
  private static Document getEditingDocument(final DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length == 0) return null;
    if (!DiffContentUtil.isTextFile(selectedFiles[0])) return null;
    return FileDocumentManager.getInstance().getDocument(selectedFiles[0]);
  }

  public void update(AnActionEvent e) {
    boolean enabled = true;
    Presentation presentation = e.getPresentation();
    presentation.setText(DiffBundle.message("diff.compare.element.type.with.editor.action.name"));
    if (getDiffData(e.getDataContext()) == null) {
      enabled = false;
    }
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }

  protected FileEditorContents getDiffData(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    VirtualFile[] array = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    Document document = getEditingDocument(dataContext);
    if (array == null || array.length != 1 || document == null) {
      return null;
    }
    if (isSameFile(document, array [0])) {
      return null;
    }
    return new FileEditorContents(document, array [0], project);
  }

  private static boolean isSameFile(Document document, VirtualFile elementFile) {
    VirtualFile documentFile = FileDocumentManager.getInstance().getFile(document);

    return documentFile != null && documentFile.isValid() &&
           documentFile.equals(elementFile);
  }

  protected void disableAction(Presentation presentation) {
    presentation.setVisible(false);
  }


  private static class FileEditorContents extends DiffRequest {
    private final VirtualFile myFile;
    private final Document myDocument;

    public FileEditorContents(Document document, VirtualFile file, Project project) {
      super(project);
      myDocument = document;
      myFile = file;
    }

    public String[] getContentTitles() {
      VirtualFile documentFile = getDocumentFile(myDocument);
      String documentTitle = documentFile != null
                             ? getVirtualFileContentTitle(documentFile)
                             : DiffBundle.message("diff.content.editor.content.title");
      return new String[]{getVirtualFileContentTitle(myFile), documentTitle};
    }

    public DiffContent[] getContents() {
      return new DiffContent[]{
        DocumentContent.fromFile(getProject(), myFile),
        DocumentContent.fromDocument(getProject(), myDocument)
      };
    }

    public String getWindowTitle() {
      if (isEditorContent(myDocument)) {
        return DiffBundle.message("diff.element.qualified.name.vs.editor.dialog.title", getVirtualFileContentTitle(myFile));
      } else {
        return DiffBundle.message("diff.element.qualified.name.vs.file.dialog.title", getVirtualFileContentTitle(myFile),
                                  getVirtualFileContentTitle(getDocumentFile(myDocument)));
      }

    }
  }
}
