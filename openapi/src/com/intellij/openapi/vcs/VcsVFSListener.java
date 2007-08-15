package com.intellij.openapi.vcs;

import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.*;
import com.intellij.peer.PeerFactory;

import java.util.*;

/**
 * @author yole
 */
public abstract class VcsVFSListener {
  protected static class MovedFileInfo {
    public String myOldPath;
    public String myNewPath;
    private VirtualFile myFile;

    public MovedFileInfo(VirtualFile file, final String newPath) {
      myOldPath = file.getPath();
      myNewPath = newPath;
      myFile = file;
    }
  }

  protected final Project myProject;
  private final AbstractVcs myVcs;
  private final ChangeListManager myChangeListManager;
  private final MyVirtualFileAdapter myVFSListener;
  private final MyCommandAdapter myCommandListener;
  private VcsShowConfirmationOption myAddOption;
  private VcsShowConfirmationOption myRemoveOption;
  private final List<VirtualFile> myAddedFiles = new ArrayList<VirtualFile>();
  private final Map<VirtualFile, VirtualFile> myCopyFromMap = new HashMap<VirtualFile, VirtualFile>();
  private final List<FilePath> myDeletedFiles = new ArrayList<FilePath>();
  private final List<FilePath> myDeletedWithoutConfirmFiles = new ArrayList<FilePath>();
  private final List<MovedFileInfo> myMovedFiles = new ArrayList<MovedFileInfo>();

  protected enum VcsDeleteType { SILENT, CONFIRM, IGNORE }

  public VcsVFSListener(final Project project, final AbstractVcs vcs) {
    myProject = project;
    myVcs = vcs;
    myChangeListManager = ChangeListManager.getInstance(project);
    myVFSListener = new MyVirtualFileAdapter();
    myCommandListener = new MyCommandAdapter();

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    myAddOption = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    myRemoveOption = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    VirtualFileManager.getInstance().addVirtualFileListener(myVFSListener);
    CommandProcessor.getInstance().addCommandListener(myCommandListener);
  }

  public void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVFSListener);
    CommandProcessor.getInstance().removeCommandListener(myCommandListener);
  }

  protected boolean isEventIgnored(final VirtualFileEvent event) {
    if (event.isFromRefresh()) return true;
    if (ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getFile()) != myVcs) {
      return true;
    }

    return false;
  }

  protected void executeAdd() {
    final List<VirtualFile> addedFiles = new ArrayList<VirtualFile>(myAddedFiles);
    final Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<VirtualFile, VirtualFile>(myCopyFromMap);
    myAddedFiles.clear();
    myCopyFromMap.clear();

    if (myAddOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;
    if (myAddOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      performAdding(addedFiles, copyFromMap);
    }
    else {
      final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
      // TODO[yole]: nice and clean description label
      Collection<VirtualFile> filesToProcess = helper.selectFilesToProcess(addedFiles, getAddTitle(), null,
                                                                           getSingleFileAddTitle(), getSingleFileAddPromptTemplate(),
                                                                           myAddOption);
      if (filesToProcess != null) {
        performAdding(new ArrayList<VirtualFile>(filesToProcess), copyFromMap);
      }
    }
  }

  private void addFileToDelete(VirtualFile file) {
    if (file.isDirectory() && !isDirectoryVersioningSupported()){
      VirtualFile[] children = file.getChildren();
      if (children != null){
        for (VirtualFile child : children) {
          addFileToDelete(child);
        }
      }
    } else {
      final VcsDeleteType type = needConfirmDeletion(file);
      if (type == VcsDeleteType.CONFIRM) {
        myDeletedFiles.add(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file));
      }
      else if (type == VcsDeleteType.SILENT) {
        myDeletedWithoutConfirmFiles.add(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file));
      }
    }
  }

  private void executeDelete() {
    final List<FilePath> filesToDelete = new ArrayList<FilePath>(myDeletedWithoutConfirmFiles);
    final List<FilePath> deletedFiles = new ArrayList<FilePath>(myDeletedFiles);
    myDeletedWithoutConfirmFiles.clear();
    myDeletedFiles.clear();

    if (myRemoveOption.getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      if (myRemoveOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY || deletedFiles.isEmpty()) {
        filesToDelete.addAll(deletedFiles);
      }
      else {
        AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
        Collection<FilePath> filePaths = helper.selectFilePathsToProcess(deletedFiles, getDeleteTitle(), null, getSingleFileDeleteTitle(),
                                                                         getSingleFileDeletePromptTemplate(),
                                                                         myRemoveOption);
        if (filePaths != null) {
          filesToDelete.addAll(filePaths);
        }
      }
    }
    performDeletion(filesToDelete);
  }

  private void addFileToMove(final VirtualFile file, final String newParentPath, final String newName) {
    if (file.isDirectory() && !isDirectoryVersioningSupported()) {
      VirtualFile[] children = file.getChildren();
      if (children != null) {
        for (VirtualFile child : children) {
          addFileToMove(child, newParentPath + "/" + newName, child.getName());
        }
      }
    }
    else {
      processMovedFile(file, newParentPath, newName);
    }
  }


  private void processMovedFile(VirtualFile file, String newParentPath, String newName) {
    if (FileStatusManager.getInstance(myProject).getStatus(file) != FileStatus.UNKNOWN) {
      final String newPath = newParentPath + "/" + newName;
      boolean foundExistingInfo = false;
      for(MovedFileInfo info: myMovedFiles) {
        if (info.myFile == file) {
          info.myNewPath = newPath;
          foundExistingInfo = true;
          break;
        }
      }
      if (!foundExistingInfo) {
        myMovedFiles.add(new MovedFileInfo(file, newPath));
      }
    }
  }

  private void executeMoveRename() {
    final List<MovedFileInfo> movedFiles = new ArrayList<MovedFileInfo>(myMovedFiles);
    myMovedFiles.clear();
    performMoveRename(movedFiles);
  }

  protected VcsDeleteType needConfirmDeletion(final VirtualFile file) {
    return VcsDeleteType.CONFIRM;
  }

  protected abstract String getAddTitle();
  protected abstract String getSingleFileAddTitle();
  protected abstract String getSingleFileAddPromptTemplate();
  protected abstract void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap);

  protected abstract String getDeleteTitle();
  protected abstract String getSingleFileDeleteTitle();
  protected abstract String getSingleFileDeletePromptTemplate();
  protected abstract void performDeletion(List<FilePath> filesToDelete);
  protected abstract void performMoveRename(List<MovedFileInfo> movedFiles);
  protected abstract boolean isDirectoryVersioningSupported();

  private class MyVirtualFileAdapter extends VirtualFileAdapter {
    public void fileCreated(final VirtualFileEvent event) {
      if (!isEventIgnored(event) && !myChangeListManager.isIgnoredFile(event.getFile())) {
        myAddedFiles.add(event.getFile());              
      }
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      if (isEventIgnored(event) || myChangeListManager.isIgnoredFile(event.getFile())) return;
      final AbstractVcs oldVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getOriginalFile());
      if (oldVcs == myVcs) {
        final VirtualFile parent = event.getFile().getParent();
        if (parent != null) {
          myAddedFiles.add(event.getFile());
          myCopyFromMap.put(event.getFile(), event.getOriginalFile());
        }
      }
      else {
        myAddedFiles.add(event.getFile());
      }
    }

    public void fileDeleted(final VirtualFileEvent event) {
      if (!isEventIgnored(event)) {
        addFileToDelete(event.getFile());
      }
    }

    public void beforeFileMovement(final VirtualFileMoveEvent event) {
      if (isEventIgnored(event)) return;
      final VirtualFile file = event.getFile();
      final AbstractVcs newVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getNewParent());
      if (newVcs == myVcs) {
        addFileToMove(file, event.getNewParent().getPath(), file.getName());
      }
      else {
        addFileToDelete(event.getFile());
      }
    }

    public void fileMoved(final VirtualFileMoveEvent event) {
      if (isEventIgnored(event)) return;
      final AbstractVcs oldVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getOldParent());
      if (oldVcs != myVcs) {
        myAddedFiles.add(event.getFile());
      }
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (!isEventIgnored(event) && event.getPropertyName().equalsIgnoreCase(VirtualFile.PROP_NAME)) {
        final VirtualFile file = event.getFile();
        final VirtualFile parent = file.getParent();
        if (parent != null) {
          addFileToMove(file, parent.getPath(), (String)event.getNewValue());
        }
      }
    }
  }

  private class MyCommandAdapter extends CommandAdapter {
    private int myCommandLevel;

    public void commandStarted(final CommandEvent event) {
      if (myProject != event.getProject()) return;
      myCommandLevel++;
    }

    public void commandFinished(final CommandEvent event) {
      if (myProject != event.getProject()) return;
      myCommandLevel--;
      if (myCommandLevel == 0) {
        if (!myAddedFiles.isEmpty() || !myDeletedFiles.isEmpty() || !myDeletedWithoutConfirmFiles.isEmpty() || !myMovedFiles.isEmpty()) {
          // avoid reentering commandFinished handler - saving the documents may cause a "before file deletion" event firing,
          // which will cause closing the text editor, which will itself run a command that will be caught by this listener
          myCommandLevel++;
          try {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
          finally {
            myCommandLevel--;
          }
          if (!myAddedFiles.isEmpty()) {
            executeAdd();
          }
          if (!myDeletedFiles.isEmpty() || !myDeletedWithoutConfirmFiles.isEmpty()) {
            executeDelete();
          }
          if (!myMovedFiles.isEmpty()) {
            executeMoveRename();
          }
        }
      }
    }
  }
}