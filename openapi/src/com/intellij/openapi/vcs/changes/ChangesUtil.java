package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author max
 */
public class ChangesUtil {
  private ChangesUtil() {}

  @NotNull
  public static FilePath getFilePath(@NotNull final Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (revision == null) {
      revision = change.getBeforeRevision();
      assert revision != null;
    }

    return revision.getFile();
  }

  public static AbstractVcs getVcsForChange(Change change, final Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(getFilePath(change));
  }

  public static AbstractVcs getVcsForFile(VirtualFile file, Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
  }

  public static AbstractVcs getVcsForFile(File file, Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file));
  }

  public static Collection<FilePath> getPaths(final List<Change> changes) {
    Set<FilePath> paths = new HashSet<FilePath>();
    for (Change change : changes) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        paths.add(beforeRevision.getFile());
      }
      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        paths.add(afterRevision.getFile());
      }
    }
    return paths;
  }

  public static VirtualFile[] getFilesFromChanges(final Collection<Change> changes) {
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile file = afterRevision.getFile().getVirtualFile();
        if (file != null && file.isValid()) {
          files.add(file);
        }
      }
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public static Navigatable[] getNavigatableArray(final Project project, final VirtualFile[] selectedFiles) {
    List<Navigatable> result = new ArrayList<Navigatable>();
    for (VirtualFile selectedFile : selectedFiles) {
      if (!selectedFile.isDirectory()) {
        result.add(new OpenFileDescriptor(project, selectedFile));
      }
    }
    return result.toArray(new Navigatable[result.size()]);
  }

  @Nullable
  public static ChangeList getChangeListIfOnlyOne(final Project project, @Nullable Change[] changes) {
    if (changes == null || changes.length == 0) {
      return null;
    }

    ChangeList selectedList = null;
    for (Change change : changes) {
      final ChangeList list = ChangeListManager.getInstance(project).getChangeList(change);
      if (selectedList == null) {
        selectedList = list;
      }
      else if (selectedList != list) {
        return null;
      }
    }
    return selectedList;
  }

  public static FilePath getCommittedPath(final Project project, FilePath filePath) {
    // check if the file has just been renamed (IDEADEV-15494)
    Change change = ChangeListManager.getInstance(project).getChange(filePath);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          afterRevision.getFile().equals(filePath)) {
        filePath = beforeRevision.getFile();
      }
    }
    return filePath;
  }

  public static FilePath getLocalPath(final Project project, FilePath filePath) {
    // check if the file has just been renamed (IDEADEV-15494)
    Change change = ChangeListManager.getInstance(project).getChange(filePath);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          beforeRevision.getFile().equals(filePath)) {
        filePath = afterRevision.getFile();
      }
    }
    return filePath;
  }

  public interface PerVcsProcessor<T> {
    void process(AbstractVcs vcs, List<T> items);
  }

  public interface VcsSeparator<T> {
    AbstractVcs getVcsFor(T item);
  }

  public static <T> void processItemsByVcs(final Collection<T> items, final VcsSeparator<T> separator, PerVcsProcessor<T> processor) {
    final Map<AbstractVcs, List<T>> changesByVcs = new HashMap<AbstractVcs, List<T>>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (T item : items) {
          final AbstractVcs vcs = separator.getVcsFor(item);
          if (vcs != null) {
            List<T> vcsChanges = changesByVcs.get(vcs);
            if (vcsChanges == null) {
              vcsChanges = new ArrayList<T>();
              changesByVcs.put(vcs, vcsChanges);
            }
            vcsChanges.add(item);
          }
        }
      }
    });

    for (Map.Entry<AbstractVcs, List<T>> entry : changesByVcs.entrySet()) {
      processor.process(entry.getKey(), entry.getValue());
    }
  }

  public static void processChangesByVcs(final Project project, Collection<Change> changes, PerVcsProcessor<Change> processor) {
    processItemsByVcs(changes, new VcsSeparator<Change>() {
      public AbstractVcs getVcsFor(final Change item) {
        return getVcsForChange(item, project);
      }
    }, processor);
  }

  public static void processVirtualFilesByVcs(final Project project, Collection<VirtualFile> files, PerVcsProcessor<VirtualFile> processor) {
    processItemsByVcs(files, new VcsSeparator<VirtualFile>() {
      public AbstractVcs getVcsFor(final VirtualFile item) {
        return getVcsForFile(item, project);
      }
    }, processor);
  }

  public static void processFilePathsByVcs(final Project project, Collection<FilePath> files, PerVcsProcessor<FilePath> processor) {
    processItemsByVcs(files, new VcsSeparator<FilePath>() {
      public AbstractVcs getVcsFor(final FilePath item) {
        return getVcsForFile(item.getIOFile(), project);
      }
    }, processor);
  }

  public static List<File> filePathsToFiles(List<FilePath> filePaths) {
    List<File> ioFiles = new ArrayList<File>();
    for(FilePath filePath: filePaths) {
      ioFiles.add(filePath.getIOFile());
    }
    return ioFiles;
  }

  public static boolean hasFileChanges(final Collection<Change> changes) {
    for(Change change: changes) {
      FilePath path = ChangesUtil.getFilePath(change);
      if (!path.isDirectory()) {
        return true;
      }
    }
    return false;
  }
}
