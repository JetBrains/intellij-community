package org.jetbrains.jps.cache.client;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cache.JpsCachesLoaderUtil;
import org.jetbrains.jps.cache.model.AffectedModule;
import org.jetbrains.jps.cache.model.DownloadableFileUrl;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.model.OutputLoadResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class JpsServerClientImpl implements JpsServerClient {
  private static final Logger LOG = Logger.getInstance(JpsServerClientImpl.class);
  private final String myServerUrl;

  JpsServerClientImpl(@NotNull String serverUrl) {
    myServerUrl = serverUrl;
  }

  @Override
  public @Nullable File downloadMetadataById(@NotNull JpsNettyClient nettyClient, @NotNull String metadataId, @NotNull File targetDir) {
    String downloadUrl = myServerUrl + "/metadata/" + metadataId;
    String fileName = "metadata.json";
    DownloadableFileUrl description = new DownloadableFileUrl(downloadUrl, fileName);
    JpsCachesDownloader downloader = new JpsCachesDownloader(Collections.singletonList(description), nettyClient, null);

    LOG.debug("Downloading JPS metadata from: " + downloadUrl);
    File metadataFile;
    try {
      List<Pair<File, DownloadableFileUrl>> pairs = downloader.download(targetDir);
      Pair<File, DownloadableFileUrl> first = ContainerUtil.getFirstItem(pairs);
      metadataFile = first != null ? first.first : null;
      if (metadataFile == null) {
        LOG.warn("Failed to download JPS metadata");
        return null;
      }
      return metadataFile;
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS metadata from URL: " + downloadUrl, e);
      return null;
    }
  }

  @Nullable
  @Override
  public File downloadCacheById(@NotNull JpsLoaderContext context, @NotNull String cacheId, @NotNull File targetDir) {
    String downloadUrl = myServerUrl + "/caches/" + cacheId;
    String fileName = "portable-build-cache.zip";
    DownloadableFileUrl description = new DownloadableFileUrl(downloadUrl, fileName);
    JpsCachesDownloader downloader = new JpsCachesDownloader(Collections.singletonList(description), context.getNettyClient(), context);

    LOG.debug("Downloading JPS caches from: " + downloadUrl);
    File zipFile;
    try {
      List<Pair<File, DownloadableFileUrl>> pairs = downloader.download(targetDir);
      //downloadIndicatorManager.finished(this);

      Pair<File, DownloadableFileUrl> first = ContainerUtil.getFirstItem(pairs);
      zipFile = first != null ? first.first : null;
      if (zipFile != null) return zipFile;
      LOG.warn("Failed to download JPS caches");
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS caches from URL: " + downloadUrl, e);
    }
    return null;
  }

  @Override
  public List<OutputLoadResult> downloadCompiledModules(@NotNull JpsLoaderContext context, @NotNull List<AffectedModule> affectedModules) {
    File targetDir = new File(PathManager.getPluginTempPath(), JpsCachesLoaderUtil.LOADER_TMP_FOLDER_NAME);
    if (targetDir.exists()) FileUtil.delete(targetDir);
    targetDir.mkdirs();

    Map<String, AffectedModule> urlToModuleNameMap = affectedModules.stream().collect(Collectors.toMap(
                            module -> myServerUrl + "/" + module.getType() + "/" + module.getName() + "/" + module.getHash(),
                            module -> module));

    List<DownloadableFileUrl> descriptions = ContainerUtil.map(urlToModuleNameMap.entrySet(),
                                                                       entry -> new DownloadableFileUrl(entry.getKey(),
                                                                       entry.getValue().getOutPath().getName() + ".zip"));
    JpsCachesDownloader downloader = new JpsCachesDownloader(descriptions, context.getNettyClient(), context);

    List<File> downloadedFiles = new ArrayList<>();
    try {
      // Downloading process
      List<Pair<File, DownloadableFileUrl>> download = downloader.download(targetDir);
      downloadedFiles = ContainerUtil.map(download, pair -> pair.first);
      return ContainerUtil.map(download, pair -> {
        String downloadUrl = pair.second.getDownloadUrl();
        return new OutputLoadResult(pair.first, downloadUrl, urlToModuleNameMap.get(downloadUrl));
      });
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS compilation outputs", e);
      if (targetDir.exists()) FileUtil.delete(targetDir);
      downloadedFiles.forEach(zipFile -> FileUtil.delete(zipFile));
      return null;
    }
  }
}
