package com.intellij.history.deprecated;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.localVcs.*;
import com.intellij.history.deprecated.DeprecatedUpToDateLineNumberProviderImpl;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeprecatedLVCS extends LocalVcs implements ProjectComponent {
  private final Project myProject;
  private LocalHistoryConfiguration myConfiguration;

  public DeprecatedLVCS(Project project, LocalHistoryConfiguration configuration) {
    myProject = project;
    myConfiguration = configuration;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "DeprecatedLocalVcs";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void save() {
  }

  public Project getProject() {
    return myProject;
  }

  public String[] getRootPaths() {
    return new String[0];
  }

  @Nullable
  public LvcsFile findFile(String filePath) {
    return null;
  }

  @Nullable
  public LvcsFile findFile(String filePath, boolean ignoreDeleted) {
    return null;
  }

  @Nullable
  public LvcsFileRevision findFileRevisionByDate(String filePath, long date) {
    return null;
  }

  @Nullable
  public LvcsDirectory findDirectory(String dirPath) {
    return null;
  }

  @Nullable
  public LvcsDirectory findDirectory(String dirPath, boolean ignoreDeleted) {
    return null;
  }

  public LvcsLabel addLabel(String name, String path) {
    return doPutLabel(name, path);
  }

  public LvcsLabel addLabel(byte type, String name, String path) {
    return doPutLabel(name, path);
  }

  private LvcsLabel doPutLabel(String name, String path) {
    LocalHistory.putSystemLabel(myProject, name);
    return null;
  }

  public LvcsAction startAction(final String name, String path, boolean isExternalChanges) {
    return new LvcsAction() {
      LocalHistoryAction a = LocalHistory.startAction(myProject, name);

      public void finish() {
        a.finish();
      }

      public String getName() {
        return name;
      }
    };
  }

  public LvcsRevision[] getRevisions(final String path, final LvcsLabel label) {
    return new LvcsRevision[0];
  }

  public LvcsRevision[] getRevisions(final LvcsLabel label1, final LvcsLabel label2) {
    return new LvcsRevision[0];
  }

  public boolean isUnderVcs(VirtualFile file) {
    return false;
  }

  public boolean isAvailable() {
    return true;
  }

  public LocalVcsPurgingProvider getLocalVcsPurgingProvider() {
    return new LocalVcsPurgingProvider() {
      public void registerLocker(LocalVcsItemsLocker locker) {
      }

      public void unregisterLocker(LocalVcsItemsLocker locker) {
      }

      public boolean itemCanBePurged(LvcsRevision lvcsRevisionFor) {
        return true;
      }
    };
  }

  public LvcsLabel[] getAllLabels() {
    return new LvcsLabel[0];
  }

  public void addLvcsLabelListener(LvcsLabelListener listener) {
  }

  public void removeLvcsLabelListener(final LvcsLabelListener listener) {
  }

  public UpToDateLineNumberProvider getUpToDateLineNumberProvider(final Document document, final String upToDateContent) {
    return new DeprecatedUpToDateLineNumberProviderImpl(document, myProject, upToDateContent);
  }

  public LocalHistoryConfiguration getConfiguration() {
    return myConfiguration;
  }
}
