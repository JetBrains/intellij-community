// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.javascript.boilerplate;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.NullableFunction;
import com.intellij.util.PlatformUtils;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;

public abstract class AbstractGithubTagDownloadedProjectGenerator extends WebProjectTemplate<GithubTagInfo> {

  private static final Logger LOG = Logger.getInstance(AbstractGithubTagDownloadedProjectGenerator.class);

  @Override
  public final @NotNull @Nls String getName() {
    return getDisplayName();
  }

  protected abstract @NotNull @Nls String getDisplayName();

  public abstract @NotNull String getGithubUserName();

  public abstract @NotNull String getGithubRepositoryName();

  @Override
  public abstract @Nullable String getDescription();

  private @NlsContexts.ProgressTitle String getTitle() {
    return getDisplayName();
  }

  @Override
  public @Nullable String getHelpId() {
    return "create.from.template." + getGithubUserName() + "." + getGithubRepositoryName();
  }

  public @Nullable String getDefaultBranchName() {
    return "master";
  }

  @Override
  public void generateProject(final @NotNull Project project, final @NotNull VirtualFile baseDir,
                              @NotNull GithubTagInfo tag, @NotNull Module module) {
    try {
      unpackToDir(project, VfsUtilCore.virtualToIoFile(baseDir), tag);
    }
    catch (GeneratorException e) {
      reportError(project, e);
    }
    ApplicationManager.getApplication().runWriteAction(() -> baseDir.refresh(true, true));
  }

  @Override
  public @NotNull GithubProjectGeneratorPeer createPeer() {
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
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new GeneratorException(LangBundle.message("dialog.message.download.skipped.in.unit.test.mode", tag.getZipballUrl()));
      }
      downloadAndUnzip(project, tag.getZipballUrl(), zipArchiveFile, extractToDir, true);
    }
  }

  private void downloadAndUnzip(@Nullable Project project,
                                @NotNull String url,
                                @NotNull File zipArchiveFile,
                                @NotNull File extractToDir,
                                boolean retryOnError) throws GeneratorException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        File file = URLUtil.urlToFile(new URL(url));
        ZipUtil.unzip(null, extractToDir, file, null, null, true);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
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
    ZipUtil.unzipWithProgressSynchronously(project, getTitle(), zipArchiveFile, extractToDir, getPathConvertor(), true);
  }

  protected @Nullable NullableFunction<String, String> getPathConvertor() {
    return null;
  }

  public abstract @Nullable String getPrimaryZipArchiveUrlForDownload(@NotNull GithubTagInfo tag);

  private @NotNull File getCacheFile(@NotNull GithubTagInfo tag) {
    String fileName = URLUtil.encodeURIComponent(tag.getName() + ".zip");
    return GithubDownloadUtil.findCacheFile(getGithubUserName(), getGithubRepositoryName(), fileName);
  }

  private void reportError(@NotNull Project project, @NotNull GeneratorException e) {
    String message = LangBundle.message("dialog.message.error.creating.project", getDisplayName());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(message, e);
      return;
    }
    LOG.info(message, e);
    String title = LangBundle.message("dialog.title.create.project", getDisplayName());
    Messages.showErrorDialog(project, message + ". " + e.getMessage(), title);
  }

  public ActionLink createGitHubLink() {
    ActionLink link = new ActionLink(LangBundle.message("link.label.on.github", getName()), e -> {
      BrowserUtil.open("https://github.com/" + getGithubUserName() + "/" + getGithubRepositoryName());
    });
    link.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    return link;
  }
}
