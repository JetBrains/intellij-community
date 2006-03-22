package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;

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
    final FilePath filePath = getFilePath(change);
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile root = VcsDirtyScope.getRootFor(fileIndex, filePath);
    if (root != null) {
      return vcsManager.getVcsFor(root);
    }

    return null;
  }

  public interface ChangesProcessor {
    void processChanges(AbstractVcs vcs, List<Change> changes);
  }

  public static void processChangesByVcs(Project project, Collection<Change> changes, ChangesProcessor processor) {
    Map<AbstractVcs, List<Change>> changesByVcs = new HashMap<AbstractVcs, List<Change>>();

    for (Change change : changes) {
      final AbstractVcs vcs = getVcsForChange(change, project);
      if (vcs != null) {
        List<Change> vcsChanges = changesByVcs.get(vcs);
        if (vcsChanges == null) {
          vcsChanges = new ArrayList<Change>();
          changesByVcs.put(vcs, vcsChanges);
        }
        vcsChanges.add(change);
      }
    }

    for (Map.Entry<AbstractVcs, List<Change>> entry : changesByVcs.entrySet()) {
      processor.processChanges(entry.getKey(), entry.getValue());
    }
  }
}
