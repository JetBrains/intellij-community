package org.jetbrains.jps.cache.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cache.JpsCachesPluginUtil;
import org.jetbrains.jps.cache.git.GitRepositoryUtil;
import org.jetbrains.jps.cache.model.AffectedModule;
import org.jetbrains.jps.cache.model.DownloadableFileUrl;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.model.OutputLoadResult;
import org.jetbrains.jps.cache.ui.SegmentedProgressIndicatorManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class JpsServerClientImpl implements JpsServerClient {
  private static final Logger LOG = Logger.getInstance(JpsServerClientImpl.class);
  private static final Type GSON_MAPPER = new TypeToken<Map<String, List<String>>>() {}.getType();
  static final JpsServerClientImpl INSTANCE = new JpsServerClientImpl();
  private final String stringThree;

  private JpsServerClientImpl() {
    byte[] decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly9kMWxjNWs5bGVyZzZrbS5jbG91ZGZyb250Lm5ldA==");
    stringThree = new String(decodedBytes, StandardCharsets.UTF_8);
  }

  @NotNull
  @Override
  public Map<String, Set<String>> getCacheKeysPerRemote(@NotNull JpsNettyClient nettyClient) {
    Map<String, List<String>> response = doGetRequest(nettyClient);
    if (response == null) return Collections.emptyMap();
    Map<String, Set<String>> result = new HashMap<>();
    response.forEach((key, value) -> result.put(GitRepositoryUtil.getRemoteRepoName(key), new HashSet<>(value)));
    return result;
  }

  @Nullable
  @Override
  public File downloadMetadataById(@NotNull JpsNettyClient nettyClient, @NotNull String metadataId, @NotNull File targetDir) {
    String downloadUrl = stringThree + "/metadata/" + metadataId;
    String fileName = "metadata.json";
    DownloadableFileUrl description = new DownloadableFileUrl(downloadUrl, fileName);
    JpsCachesDownloader downloader = new JpsCachesDownloader(Collections.singletonList(description), nettyClient);

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
    String downloadUrl = stringThree + "/caches/" + cacheId;
    String fileName = "portable-build-cache.zip";
    DownloadableFileUrl description = new DownloadableFileUrl(downloadUrl, fileName);
    JpsCachesDownloader downloader = new JpsCachesDownloader(Collections.singletonList(description), context.getNettyClient());

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
    File targetDir = new File(PathManager.getPluginTempPath(), JpsCachesPluginUtil.PLUGIN_NAME);
    if (targetDir.exists()) FileUtil.delete(targetDir);
    targetDir.mkdirs();

    Map<String, AffectedModule> urlToModuleNameMap = affectedModules.stream().collect(Collectors.toMap(
                            module -> stringThree + "/" + module.getType() + "/" + module.getName() + "/" + module.getHash(),
                            module -> module));

    List<DownloadableFileUrl> descriptions = ContainerUtil.map(urlToModuleNameMap.entrySet(),
                                                                       entry -> new DownloadableFileUrl(entry.getKey(),
                                                                       entry.getValue().getOutPath().getName() + ".zip"));
    JpsCachesDownloader downloader = new JpsCachesDownloader(descriptions, context.getNettyClient());

    List<File> downloadedFiles = new ArrayList<>();
    try {
      // Downloading process
      List<Pair<File, DownloadableFileUrl>> download = downloader.download(targetDir);
      //downloadIndicatorManager.finished(this);

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

  private @Nullable Map<String, List<String>> doGetRequest(@NotNull JpsNettyClient nettyClient) {
    Map<String, String> headers = JpsServerAuthUtil.getRequestHeaders(nettyClient);
    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      String url = stringThree + "/commit_history.json";
      HttpGet httpRequest = new HttpGet(url);
      headers.forEach((k, v) -> httpRequest.setHeader(k, v));
      HttpResponse response = client.execute(httpRequest);
      HttpEntity responseEntity = response.getEntity();
      if (response.getStatusLine().getStatusCode() == 200) {
        Gson gson = new Gson();
        //String contentEncoding = responseEntity.getContentEncoding().getName();
        InputStream inputStream = responseEntity.getContent();
        return gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), GSON_MAPPER);
      }
      else {
        String errorText = StreamUtil.readText(new InputStreamReader(responseEntity.getContent(), StandardCharsets.UTF_8));
        LOG.info("Request: " + url + " Error: " + response.getStatusLine().getStatusCode() + " body: " + errorText);
        return null;
      }
    }
    catch (IOException e) {
      LOG.warn("Failed request to cache server", e);
      //JpsLoaderNotifications.ATTENTION
      //  .createNotification(JpsCacheBundle.message("notification.title.compiler.caches.loader"), JpsCacheBundle.message("notification.content.failed.request.to.cache.server", e.getMessage()), NotificationType.ERROR)
      //  .notify(project);
    }
    return null;
  }

  //private static InputStream getInputStream(HttpEntity responseEntity) throws IOException {
  //  String contentEncoding = responseEntity.getContentEncoding().getName();
  //  InputStream inputStream = responseEntity.getContent();
  //  if (contentEncoding != null && StringUtil.toLowerCase(contentEncoding).contains("gzip")) {
  //    return new GZIPInputStream(inputStream);
  //  }
  //  return new InputStreamReader(inputStream, contentEncoding);
  //}
}
