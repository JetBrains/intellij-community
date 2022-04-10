package org.jetbrains.jps.cache.client;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.cache.model.DownloadableFileUrl;
import org.jetbrains.jps.cache.model.JpsLoaderContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.jetbrains.jps.cache.JpsCachesLoaderUtil.EXECUTOR_SERVICE;
import static org.jetbrains.jps.cache.client.JpsServerConnectionUtil.saveToFile;

class JpsCachesDownloader {
  private static final Logger LOG = Logger.getInstance(JpsCachesDownloader.class);
  private static final byte MAX_RETRY_COUNT = 3;
  private static final String CDN_CACHE_HEADER = "X-Cache";
  private int hitsCount = 0;
  private final List<DownloadableFileUrl> myFilesDescriptions;
  private final JpsNettyClient myNettyClient;
  private JpsLoaderContext myContext;

  JpsCachesDownloader(@NotNull List<DownloadableFileUrl> filesDescriptions,
                      @NotNull JpsNettyClient nettyClient,
                      @Nullable JpsLoaderContext context) {
    myFilesDescriptions = filesDescriptions;
    myNettyClient = nettyClient;
    myContext = context;
  }

  @NotNull
  List<Pair<File, DownloadableFileUrl>> download(@NotNull File targetDir) throws IOException {
    List<Pair<File, DownloadableFileUrl>> downloadedFiles = new CopyOnWriteArrayList<>();
    List<Pair<File, DownloadableFileUrl>> existingFiles = new CopyOnWriteArrayList<>();

    try {
      int expectedDownloads;
      if (myContext != null) {
        expectedDownloads = myContext.getTotalExpectedDownloads();
      } else {
        expectedDownloads = 0;
      }
      myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.downloading.0.files.text", myFilesDescriptions.size()));
      long start = System.currentTimeMillis();
      List<Future<Void>> results = new ArrayList<>();
      final AtomicLong totalSize = new AtomicLong();
      for (final DownloadableFileUrl description : myFilesDescriptions) {
        results.add(EXECUTOR_SERVICE.submit(() -> {
          if (myContext != null) myContext.checkCanceled();

          final File existing = new File(targetDir, description.getDefaultFileName());
          byte attempt = 0;
          File downloaded = null;
          while (downloaded == null && attempt++ < MAX_RETRY_COUNT) {
            try {
              downloaded = downloadFile(description, existing, expectedDownloads);
            } catch (IOException e) {
              int httpStatusCode = -1;
              //if (e  instanceof HttpRequests.HttpStatusException) {
              //  httpStatusCode = ((HttpRequests.HttpStatusException)e).getStatusCode();
              //  if (httpStatusCode == 404) {
              //    LOG.info("File not found to download " + description.getDownloadUrl());
              //    indicator.finished();
              //    return null;
              //  }
              //} else {
              //  if (Registry.is("jps.cache.check.internet.connection")){
              //    JpsServerConnectionUtil.checkDomainIsReachable("google.com");
              //    JpsServerConnectionUtil.checkDomainIsReachable("d1lc5k9lerg6km.cloudfront.net");
              //    JpsServerConnectionUtil.checkDomainRouting("d1lc5k9lerg6km.cloudfront.net");
              //  }
              //}

              // If max attempt count exceeded, rethrow exception further
              if (attempt != MAX_RETRY_COUNT) {
                if (httpStatusCode != -1) {
                  LOG.info("Failed to download " + description.getDownloadUrl() + " HTTP code: " + httpStatusCode + ". Attempt " + attempt + " to download file again");
                } else {
                  LOG.info("Failed to download " + description.getDownloadUrl() + " Root cause: " + e + ". Attempt " + attempt + " to download file again");
                }
                Thread.sleep(250);
              } else {
                throw new IOException(JpsBuildBundle.message("error.file.download.failed", description.getDownloadUrl(), e.getMessage()), e);
              }
            }
          }

          assert downloaded != null : "Download result shouldn't be NULL";
          if (FileUtil.filesEqual(downloaded, existing)) {
            existingFiles.add(Pair.create(existing, description));
          }
          else {
            totalSize.addAndGet(downloaded.length());
            downloadedFiles.add(Pair.create(downloaded, description));
          }
          return null;
        }));
      }

      for (Future<Void> result : results) {
        try {
          result.get();
        }
        catch (InterruptedException e) {
          throw new ProcessCanceledException();
        }
        catch (ExecutionException e) {
          if (e.getCause() instanceof IOException) {
            throw ((IOException)e.getCause());
          }
          if (e.getCause() instanceof ProcessCanceledException) {
            throw ((ProcessCanceledException)e.getCause());
          }
          LOG.error(e);
        }
      }
      long duration = System.currentTimeMillis() - start;
      LOG.info("Downloaded " + StringUtil.formatFileSize(totalSize.get()) + " in " + StringUtil.formatDuration(duration) +
               "(" + duration + "ms). Percentage of CDN cache hits: " + (hitsCount * 100/myFilesDescriptions.size()) + "%");

      List<Pair<File, DownloadableFileUrl>> localFiles = new ArrayList<>();
      localFiles.addAll(moveToDir(downloadedFiles, targetDir));
      localFiles.addAll(existingFiles);
      return localFiles;
    }
    catch (ProcessCanceledException | IOException e) {
      for (Pair<File, DownloadableFileUrl> pair : downloadedFiles) {
        FileUtil.delete(pair.getFirst());
      }
      throw e;
    }
  }

  @NotNull
  private File downloadFile(@NotNull final DownloadableFileUrl description, @NotNull final File existingFile, int expectedDownloads) throws IOException {
    final String presentableUrl = description.getPresentableDownloadUrl();
    Map<String, String> headers = JpsServerAuthUtil.getRequestHeaders();

    try (CloseableHttpClient client = HttpClientBuilder.create().disableAutomaticRetries().build()) {
      HttpGet httpRequest = new HttpGet(description.getDownloadUrl());
      headers.forEach((k, v) -> httpRequest.setHeader(k, v));
      HttpResponse response = client.execute(httpRequest);
      HttpEntity responseEntity = response.getEntity();
      if (response.getStatusLine().getStatusCode() == 200) {
        long size = responseEntity.getContentLength();
        if (existingFile.exists() && size == existingFile.length()) {
          return existingFile;
        }

        Header header = response.getFirstHeader(CDN_CACHE_HEADER);
        if (header != null && header.getValue().startsWith("Hit")) hitsCount++;

        myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.download.file.text", description.getPresentableFileName(), presentableUrl), expectedDownloads);
        return saveToFile(FileUtil.createTempFile("download.", ".tmp").toPath(), responseEntity, myContext).toFile();
      }
      else {
        String errorText = StreamUtil.readText(new InputStreamReader(responseEntity.getContent(), StandardCharsets.UTF_8));
        throw new IOException("Request: " + description.getDownloadUrl() + " Error: " + response.getStatusLine().getStatusCode() + " body: " + errorText);
      }
    }
  }

  private static List<Pair<File, DownloadableFileUrl>> moveToDir(List<Pair<File, DownloadableFileUrl>> downloadedFiles,
                                                                         final File targetDir) throws IOException {
    FileUtil.createDirectory(targetDir);
    List<Pair<File, DownloadableFileUrl>> result = new ArrayList<>();
    for (Pair<File, DownloadableFileUrl> pair : downloadedFiles) {
      final DownloadableFileUrl description = pair.getSecond();
      final String fileName = description.generateFileName(s -> !new File(targetDir, s).exists());
      final File toFile = new File(targetDir, fileName);
      FileUtil.rename(pair.getFirst(), toFile);
      result.add(Pair.create(toFile, description));
    }
    return result;
  }
}