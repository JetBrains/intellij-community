package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.FilePathSplittingPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;


public class ReplaceFileConfirmationDialog {
  private final FileStatusManager myFileStatusManager;
  ProgressIndicator myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
  private final String myActionName;

  public ReplaceFileConfirmationDialog(Project project, String actionName) {
    myFileStatusManager = FileStatusManager.getInstance(project);
    myActionName = actionName;
  }

  public boolean confirmFor(VirtualFile[] files) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    if (myProgressIndicator != null) myProgressIndicator.pushState();
    try {
      Collection modifiedFiles = collectModifiedFiles(files);
      if (modifiedFiles.isEmpty()) return true;
      return requestConfirmation(modifiedFiles);
    }
    finally {
      if (myProgressIndicator != null) myProgressIndicator.popState();
    }
  }

  public boolean requestConfirmation(final Collection modifiedFiles) {
    if (modifiedFiles.isEmpty()) return true;

    return Messages.showDialog(createMessage(modifiedFiles), myActionName,
                                    new String[]{createOwerriteButtonName(modifiedFiles), getCancelButtonText()},
                               0, Messages.getWarningIcon()) ==
                DialogWrapper.OK_EXIT_CODE;

  }

  protected String getCancelButtonText() {
    return "&Cancel";
  }

  private String createOwerriteButtonName(Collection modifiedFiles) {

    return modifiedFiles.size() > 1 ? getOkButtonTextForFiles() : getOkButtonTextForOneFile();
  }

  protected String getOkButtonTextForOneFile() {
    return "&Overwrite Modified File";
  }

  protected String getOkButtonTextForFiles() {
    return "&Overwrite Modified Files";
  }

  protected String createMessage(Collection modifiedFiles) {
    if (modifiedFiles.size() == 1) {
      VirtualFile virtualFile = ((VirtualFile)modifiedFiles.iterator().next());
      return "File " +
             FilePathSplittingPolicy.SPLIT_BY_LETTER.getPresentableName(new File(virtualFile.getPath()), 40) +
             " has been locally modified.";
    }
    else {
      return "Some files were locally modified.";
    }
  }

  public Collection<VirtualFile> collectModifiedFiles(VirtualFile[] files) {

    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    if (files == null) return result;

    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      if (myProgressIndicator != null) {
        myProgressIndicator.setText("Searching for modified files");
        myProgressIndicator.setText2(file.getPresentableUrl());
      }
      FileStatus status = myFileStatusManager.getStatus(file);
      if (status != FileStatus.NOT_CHANGED) {
        result.add(file);
        if (result.size() > 1) return result;
      }
      result.addAll(collectModifiedFiles(file.getChildren()));
    }
    return result;
  }
}
