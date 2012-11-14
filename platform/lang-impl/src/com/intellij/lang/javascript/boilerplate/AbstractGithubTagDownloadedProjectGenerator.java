package com.intellij.lang.javascript.boilerplate;

import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.templates.github.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * @author Sergey Simonchik
 */
public abstract class AbstractGithubTagDownloadedProjectGenerator extends WebProjectTemplate<GithubTagInfo> {

  private static final Logger LOG = Logger.getInstance(AbstractGithubTagDownloadedProjectGenerator.class);

  @NotNull
  @Nls
  @Override
  public final String getName() {
    return getDisplayName();
  }

  @NotNull
  protected abstract String getDisplayName();

  @NotNull
  protected abstract String getGithubUserName();

  @NotNull
  protected abstract String getGithubRepositoryName();

  @Nullable
  public abstract String getDescription();

  private String getTitle() {
    return getDisplayName();
  }

  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @NotNull GithubTagInfo tag, @NotNull Module module) {
    try {
      unpackToDir(project, new File(baseDir.getPath()), tag);
    }
    catch (GeneratorException e) {
      showErrorMessage(e.getMessage());
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        baseDir.refresh(false, true);
      }
    });
  }

  @NotNull
  @Override
  public GithubProjectGeneratorPeer createPeer() {
    return new GithubProjectGeneratorPeer(this);
  }

  @Override
  public boolean isPrimaryGenerator() {
    return "WebStorm".equals(ApplicationNamesInfo.getInstance().getProductName());
  }

  private void unpackToDir(@Nullable Project project,
                           @NotNull File extractToDir,
                           @NotNull GithubTagInfo tag) throws GeneratorException {
    File zipArchiveFile = getCacheFile(tag);
    boolean brokenZip = true;
    if (zipArchiveFile.isFile()) {
      try {
        ZipUtil.unzipWithProgressSynchronously(project, getTitle(), zipArchiveFile, extractToDir);
        brokenZip = false;
      }
      catch (GeneratorException ignored) {
      }
    }
    if (brokenZip) {
      GithubDownloadUtil.downloadContentToFileWithProgressSynchronously(
        project,
        tag.getZipballUrl(),
        getTitle(),
        zipArchiveFile,
        getGithubUserName(),
        getGithubRepositoryName()
      );
      LOG.info("Downloaded " + zipArchiveFile.getAbsolutePath() + " of size " + zipArchiveFile.length() + " bytes");
      ZipUtil.unzipWithProgressSynchronously(project, getTitle(), zipArchiveFile, extractToDir);
    }
  }

  @NotNull
  private File getCacheFile(@NotNull GithubTagInfo tag) {
    String fileName = tag.getName() + ".zip";
    try {
      fileName = URLEncoder.encode(fileName, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      LOG.warn("Can't urlEncode", e);
    }
    return GithubDownloadUtil.findCacheFile(getGithubUserName(), getGithubRepositoryName(), fileName);
  }

  private void showErrorMessage(@NotNull String message) {
    String fullMessage = "Error creating " + getDisplayName() + " project. " + message;
    String title = "Create " + getDisplayName() + " Project";
    Project project = null;
    Messages.showErrorDialog(project, fullMessage, title);
  }

}
