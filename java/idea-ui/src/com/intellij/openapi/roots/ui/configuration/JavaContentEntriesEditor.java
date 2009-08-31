package com.intellij.openapi.roots.ui.configuration;

import com.intellij.Patches;
import com.intellij.ide.util.JavaUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.util.concurrency.SwingWorker;

import javax.swing.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.awt.*;

public class JavaContentEntriesEditor extends CommonContentEntriesEditor {
  private JRadioButton myRbRelativePaths;

  public JavaContentEntriesEditor(Project project, String moduleName, ModifiableRootModel model, ModulesProvider modulesProvider) {
    super(project, moduleName, model, modulesProvider);
  }

  protected ContentEntryEditor createContentEntryEditor(ContentEntry contentEntry) {
    return new JavaContentEntryEditor(contentEntry, myModel);
  }

  protected ContentEntryTreeEditor createContentEntryTreeEditor(Project project) {
    return new JavaContentEntryTreeEditor(project);
  }

  @Override
  protected List<ContentEntry> addContentEntries(VirtualFile[] files) {
    List<ContentEntry> contentEntries = super.addContentEntries(files);
    if (contentEntries.size() > 0) {
      final ContentEntry[] contentEntriesArray = contentEntries.toArray(new ContentEntry[contentEntries.size()]);
      addSourceRoots(myProject, contentEntriesArray, new Runnable() {
        public void run() {
          addContentEntryPanels(contentEntriesArray);
        }
      });
    }
    return contentEntries;
  }

  private static void addSourceRoots(final Project project, final ContentEntry[] contentEntries, final Runnable finishRunnable) {
    final HashMap<ContentEntry, List<Pair<File, String>>> entryToRootMap = new HashMap<ContentEntry, List<Pair<File, String>>>();
    final Map<File, ContentEntry> fileToEntryMap = new HashMap<File, ContentEntry>();
    for (final ContentEntry contentEntry : contentEntries) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        entryToRootMap.put(contentEntry, null);
        fileToEntryMap.put(VfsUtil.virtualToIoFile(file), contentEntry);
      }
    }

    final ProgressWindow progressWindow = new ProgressWindow(true, project);
    final ProgressIndicator progressIndicator = Patches.MAC_HIDE_QUIT_HACK
                                                ? progressWindow
                                                : new SmoothProgressAdapter(progressWindow, project);

    final Runnable searchRunnable = new Runnable() {
      public void run() {
        final Runnable process = new Runnable() {
          public void run() {
            for (final File file : fileToEntryMap.keySet()) {
              progressIndicator.setText(ProjectBundle.message("module.paths.searching.source.roots.progress", file.getPath()));
              final List<Pair<File, String>> roots = JavaUtil.suggestRoots(file);
              entryToRootMap.put(fileToEntryMap.get(file), roots);
            }
          }
        };
        progressWindow.setTitle(ProjectBundle.message("module.paths.searching.source.roots.title"));
        ProgressManager.getInstance().runProcess(process, progressIndicator);
      }
    };

    final Runnable addSourcesRunnable = new Runnable() {
      public void run() {
        for (final ContentEntry contentEntry : contentEntries) {
          final List<Pair<File, String>> suggestedRoots = entryToRootMap.get(contentEntry);
          if (suggestedRoots != null) {
            for (final Pair<File, String> suggestedRoot : suggestedRoots) {
              final VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByIoFile(suggestedRoot.first);
              final VirtualFile fileContent = contentEntry.getFile();
              if (sourceRoot != null && fileContent != null && VfsUtil.isAncestor(fileContent, sourceRoot, false)) {
                contentEntry.addSourceFolder(sourceRoot, false, suggestedRoot.getSecond());
              }
            }
          }
        }
        if (finishRunnable != null) {
          finishRunnable.run();
        }
      }
    };

    new SwingWorker() {
      public Object construct() {
        searchRunnable.run();
        return null;
      }

      public void finished() {
        addSourcesRunnable.run();
      }
    }.start();
  }

  protected JPanel createBottomControl(Module module) {
    final JPanel innerPanel = new JPanel(new GridBagLayout());
    innerPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 6));
    myRbRelativePaths = new JRadioButton(ProjectBundle.message("module.paths.outside.module.dir.relative.radio"));
    final JRadioButton rbAbsolutePaths = new JRadioButton(ProjectBundle.message("module.paths.outside.module.dir.absolute.radio"));
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbRelativePaths);
    buttonGroup.add(rbAbsolutePaths);
    innerPanel.add(new JLabel(ProjectBundle.message("module.paths.outside.module.dir.label")),
                   new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                          0, 0));
    innerPanel.add(rbAbsolutePaths,
                   new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                          0, 0));
    innerPanel.add(myRbRelativePaths,
                   new GridBagConstraints(2, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0),
                                          0, 0));
    if (module.isSavePathsRelative()) {
      myRbRelativePaths.setSelected(true);
    }
    else {
      rbAbsolutePaths.setSelected(true);
    }
    return innerPanel;
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    final Module selfModule = getModule();
    return selfModule != null && myRbRelativePaths != null && selfModule.isSavePathsRelative() != myRbRelativePaths.isSelected();
  }

  public void apply() throws ConfigurationException {
    final Module module = getModule();
    module.setSavePathsRelative(myRbRelativePaths.isSelected());
  }
}
