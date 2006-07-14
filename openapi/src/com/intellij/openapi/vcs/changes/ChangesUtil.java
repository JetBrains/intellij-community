package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author max
 */
public class ChangesUtil {
  private ChangesUtil() {}

  public static FilePath getFilePath(final Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (revision == null) revision = change.getBeforeRevision();

    return revision.getFile();
  }

  public static AbstractVcs getVcsForChange(Change change, final Project project) {
    return getVcsForPath(getFilePath(change), project);
  }

  public static AbstractVcs getVcsForFile(VirtualFile file, Project project) {
    return getVcsForPath(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file), project);
  }

  public static AbstractVcs getVcsForFile(File file, Project project) {
    return getVcsForPath(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file), project);
  }

  private static AbstractVcs getVcsForPath(final FilePath filePath, final Project project) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile root = VcsDirtyScope.getRootFor(fileIndex, filePath);
    if (root != null) {
      return vcsManager.getVcsFor(root);
    }

    return null;
  }

  public static List<FilePath> getPaths(final List<Change> changes) {
    List<FilePath> paths = new ArrayList<FilePath>();
    for (Change change : changes) {
      paths.add(getFilePath(change));
    }
    return paths;
  }

  public static Navigatable[] getNavigatableArray(final Project project, final VirtualFile[] selectedFiles) {
    Navigatable[] navigatables = new Navigatable[selectedFiles.length];
    for (int i = 0; i < selectedFiles.length; i++) {
    navigatables[i] = new OpenFileDescriptor(project, selectedFiles[i], 0);
  }
    return navigatables;
  }

  @Nullable
  public static ChangeList getChangeListIfOnlyOne(final Project project, Change[] changes) {
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

  public interface PerVcsProcessor<T> {
    void process(AbstractVcs vcs, List<T> items);
  }

  public interface VcsSeparator<T> {
    AbstractVcs getVcsFor(T item);
  }

  public static <T> void processItemsByVcs(Collection<T> items, VcsSeparator<T> separator, PerVcsProcessor<T> processor) {
    Map<AbstractVcs, List<T>> changesByVcs = new HashMap<AbstractVcs, List<T>>();

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

  public static void processIOFilesByVcs(final Project project, Collection<File> files, PerVcsProcessor<File> processor) {
    processItemsByVcs(files, new VcsSeparator<File>() {
      public AbstractVcs getVcsFor(final File item) {
        return getVcsForFile(item, project);
      }
    }, processor);
  }
}
