package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.UnknownFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Maxim.Mossienko
 */
public class FileReferenceQuickFixProvider {
  private FileReferenceQuickFixProvider() {}

  public static void registerQuickFix(final HighlightInfo info, final FileReference reference) {
    final FileReferenceSet fileReferenceSet = reference.getFileReferenceSet();
    int index = reference.getIndex();

    if (index < 0) return;
    final String newFileName = reference.getCanonicalText();

    // check if we could create file
    if (newFileName.length() == 0 ||
        newFileName.indexOf('\\') != -1 ||
        newFileName.indexOf('*') != -1 ||
        newFileName.indexOf('?') != -1 ||
        SystemInfo.isWindows && newFileName.indexOf(':') != -1) {
      return;
    }

    final PsiDirectory directory;

    if(index > 0) {
      PsiElement context = fileReferenceSet.getReference(index - 1).resolve();
      if (context == null) return;
      if (context instanceof PsiDirectory) directory = (PsiDirectory)context;
      else if (context instanceof WebDirectoryElement) {
        final VirtualFile originalFile = ((WebDirectoryElement)context).getOriginalVirtualFile();
        if (originalFile != null && originalFile.isDirectory()) {
          directory = reference.getElement().getManager().findDirectory(originalFile);
          if (directory == null) return;
        } else {
          return;
        }
      }
      else {
        return;
      }
    } else { // index == 0
      final Collection<PsiElement> defaultContexts = fileReferenceSet.getDefaultContexts(reference.getElement());
      final PsiElement psiElement = defaultContexts.isEmpty() ? null : defaultContexts.iterator().next();

      if (psiElement instanceof PsiDirectory) {
        directory = (PsiDirectory)psiElement;
      } else if (psiElement instanceof WebDirectoryElement) {
        final VirtualFile originalFile = ((WebDirectoryElement)psiElement).getOriginalVirtualFile();

        if (originalFile != null && originalFile.isDirectory()) {
          directory = reference.getElement().getManager().findDirectory(originalFile);
          if (directory == null) return;
        } else {
          return;
        }
      } else {
        return;
      }
    }

    boolean differentCase = false;

    if (fileReferenceSet.isCaseSensitive()) {
      boolean original = fileReferenceSet.isCaseSensitive();
      try {
        fileReferenceSet.setCaseSensitive(false);
        final PsiElement psiElement = reference.resolve();

        if (psiElement instanceof PsiNamedElement) {
          final String existingElementName = ((PsiNamedElement)psiElement).getName();

          differentCase = true;
          QuickFixAction.registerQuickFixAction(
            info,
            new RenameFileReferenceIntentionAction(existingElementName, reference)
          );

          QuickFixAction.registerQuickFixAction(
            info,
            new RenameFileFix(newFileName)
          );
        }
      } finally {
        fileReferenceSet.setCaseSensitive(original);
      }
    }

    if (differentCase && SystemInfo.isWindows) return;

    final boolean isdirectory;
    final ReferenceType type = reference.getType();

    if (type.isAssignableTo(ReferenceType.DIRECTORY)) {
      // directory
      try {
        directory.checkCreateSubdirectory(newFileName);
      } catch(IncorrectOperationException ex) {
        return;
      }
      isdirectory = true;
    } else {
      FileType ft = FileTypeManager.getInstance().getFileTypeByFileName(newFileName);
      if (ft instanceof UnknownFileType) return;

      try {
        directory.checkCreateFile(newFileName);
      } catch(IncorrectOperationException ex) {
        return;
      }

      isdirectory = false;
    }

    QuickFixAction.registerQuickFixAction(
      info,
      new CreateFileIntentionAction(isdirectory, newFileName, directory)
    );
  }

  private static class RenameFileReferenceIntentionAction implements IntentionAction {
    private final String myExistingElementName;
    private final FileReference myFileReference;

    public RenameFileReferenceIntentionAction(final String existingElementName, final FileReference fileReference) {
      myExistingElementName = existingElementName;
      myFileReference = fileReference;
    }

    @NotNull
    public String getText() {
      return QuickFixBundle.message("rename.file.reference.text", myExistingElementName);
    }

    @NotNull
    public String getFamilyName() {
      return QuickFixBundle.message("rename.file.reference.family");
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      myFileReference.handleElementRename(myExistingElementName);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  static class CreateFileIntentionAction implements IntentionAction {
    private final boolean myIsdirectory;
    private final String myNewFileName;
    private final PsiDirectory myDirectory;
    private final @Nullable String myText;
    private @NotNull String myKey;
    private boolean myIsAvailable;
    private long myIsAvailableTimeStamp;
    private static final int REFRESH_INTERVAL = 1000;

    public CreateFileIntentionAction(final boolean isdirectory,
                                     final String newFileName,
                                     final PsiDirectory directory,
                                     @Nullable String text,
                                     @NotNull String key) {
      myIsdirectory = isdirectory;
      myNewFileName = newFileName;
      myDirectory = directory;
      myText = text;
      myKey = key;
      myIsAvailable = true;
      myIsAvailableTimeStamp = System.currentTimeMillis();
    }

    public CreateFileIntentionAction(final boolean isdirectory,
                                     final String newFileName,
                                     final PsiDirectory directory) {
      this(isdirectory,newFileName,directory,null,isdirectory ? "create.directory.text":"create.file.text" );
    }

    @NotNull
    public String getText() {
      return QuickFixBundle.message(myKey, myNewFileName);
    }

    @NotNull
    public String getFamilyName() {
      return QuickFixBundle.message("create.file.family");
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      long current = System.currentTimeMillis();

      if (current - myIsAvailableTimeStamp > REFRESH_INTERVAL) {
        myIsAvailable = myDirectory.getVirtualFile().findChild(myNewFileName) == null;
        myIsAvailableTimeStamp = current;
      }

      return myIsAvailable;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myIsAvailableTimeStamp = 0; // to revalidate applicability

      try {
        if (myIsdirectory) {
          myDirectory.createSubdirectory(myNewFileName);
        }
        else {
          final PsiFile newfile = myDirectory.createFile(myNewFileName);
          String text = null;

          if (myText != null) {
            final PsiManager psiManager = file.getManager();
            final PsiFile psiFile = psiManager.getElementFactory().createFileFromText("_" + myNewFileName, myText);
            final PsiElement psiElement = CodeStyleManager.getInstance(file.getProject()).reformat(psiFile);

            text = psiElement.getText();
          }

          final FileEditorManager editorManager = FileEditorManager.getInstance(myDirectory.getProject());
          final FileEditor[] fileEditors = editorManager.openFile(newfile.getVirtualFile(), true);

          if (text != null) {
            for(FileEditor feditor:fileEditors) {
              if (feditor instanceof TextEditor) { // JSP is not safe to edit via Psi
                final Document document = ((TextEditor)feditor).getEditor().getDocument();
                document.setText(text);

                if (ApplicationManager.getApplication().isUnitTestMode()) {
                  FileDocumentManager.getInstance().saveDocument(document);
                }
                PsiDocumentManager.getInstance(project).commitDocument(document);
                break;
              }
            }
          }
        }
      }
      catch (IncorrectOperationException e) {
        myIsAvailable = false;
      }
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
