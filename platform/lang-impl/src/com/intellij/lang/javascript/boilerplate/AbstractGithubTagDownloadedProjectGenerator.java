package com.intellij.lang.javascript.boilerplate;

import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.PlatformUtils;
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

  @Override
  @Nullable
  public abstract String getDescription();

  private String getTitle() {
    return getDisplayName();
  }

  @Nullable
  @Override
  public String getHelpId() {
    return "create.from.template." + getGithubUserName() + "." + getGithubRepositoryName();
  }

  @Override
  public void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                              @NotNull GithubTagInfo tag, @NotNull Module module) {
    try {
      unpackToDir(project, new File(baseDir.getPath()), tag);
    }
    catch (GeneratorException e) {
      showErrorMessage(project, e.getMessage());
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        baseDir.refresh(true, true);
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
    return PlatformUtils.isWebStorm();
  }

  private void unpackToDir(@Nullable Project project,
                           @NotNull File extractToDir,
                           @NotNull GithubTagInfo tag) throws GeneratorException {
    File zipArchiveFile = getCacheFile(tag);
    boolean brokenZip = true;
    if (zipArchiveFile.isFile()) {
      try {
        ZipUtil.unzipWithProgressSynchronously(project, getTitle(), zipArchiveFile, extractToDir, true);
        brokenZip = false;
      }
      catch (GeneratorException ignored) {
      }
    }
    if (brokenZip) {
      String primaryUrl = getPrimaryZipArchiveUrlForDownload(tag);
      boolean downloaded = false;
      if (primaryUrl != null) {
        try {
          downloadAndUnzip(project, primaryUrl, zipArchiveFile, extractToDir, false);
          downloaded = true;
        } catch (GeneratorException e) {
          LOG.info("Can't download " + primaryUrl, e);
          FileUtil.delete(zipArchiveFile);
        }
      }
      if (!downloaded) {
        downloadAndUnzip(project, tag.getZipballUrl(), zipArchiveFile, extractToDir, true);
      }
    }
  }

  private void downloadAndUnzip(@Nullable Project project,
                                @NotNull String url,
                                @NotNull File zipArchiveFile,
                                @NotNull File extractToDir,
                                boolean retryOnError) throws GeneratorException {
    GithubDownloadUtil.downloadContentToFileWithProgressSynchronously(
      project,
      url,
      getTitle(),
      zipArchiveFile,
      getGithubUserName(),
      getGithubRepositoryName(),
      retryOnError
    );
    LOG.info("Content of " + url + " has been successfully downloaded to " + zipArchiveFile.getAbsolutePath()
             + ", size " + zipArchiveFile.length() + " bytes");
    ZipUtil.unzipWithProgressSynchronously(project, getTitle(), zipArchiveFile, extractToDir, true);
  }

  @Nullable
  public abstract String getPrimaryZipArchiveUrlForDownload(@NotNull GithubTagInfo tag);

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

  private void showErrorMessage(@NotNull Project project, @NotNull String message) {
    String fullMessage = "Error creating " + getDisplayName() + " project. " + message;
    String title = "Create " + getDisplayName() + " Project";
    Messages.showErrorDialog(project, fullMessage, title);
  }

}
