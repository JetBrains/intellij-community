// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.services.bintray;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.io.HttpRequests;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * @author ibessonov
 */
public class BintrayEndpoint {

  public static final String BINTRAY_API_URL = "https://bintray.com/api/v1/";

  @FunctionalInterface
  public interface ExceptionHandler<E extends Throwable> {
    void handle(Throwable throwable) throws IOException, E;
  }

  private final Gson gson = new Gson();


  @Nullable
  public RemoteRepositoryDescription getRepository(@NotNull String subject, @NotNull String repo) throws IOException {
    // workaround, API requires authorization
    String url = BintrayModel.Repository.getUrl(subject, repo);
    return HttpRequests.request(url).accept("application/xml").connect(request -> {
      try {
        request.getConnection();
      }
      catch (HttpRequests.HttpStatusException e) {
        if (e.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
          return null;
        }
        throw e;
      }
      return toRepositoryDescription(new BintrayModel.Repository(subject, repo));
    });
  }

  @NotNull
  public List<RemoteRepositoryDescription> getRepositories(@NotNull String subject) throws IOException {
    // workaround, API requires authorization
    String url = BintrayModel.Repository.getUrl(subject, null);
    return HttpRequests.request(url).accept("application/xml").connect(request -> {
      try {
        String response = request.readString(null);

        Pattern extractRepoNamePattern = Pattern.compile("<a\\shref=\"([^/]+)/\">\\1/</a>");

        List<RemoteRepositoryDescription> result = new ArrayList<>();
        Matcher matcher = extractRepoNamePattern.matcher(response);
        while (matcher.find()) {
          result.add(toRepositoryDescription(new BintrayModel.Repository(subject, matcher.group(1))));
        }
        return result;
      }
      catch (HttpRequests.HttpStatusException e) {
        if (e.getStatusCode() == HttpResponseStatus.UNAUTHORIZED.code()) {
          return emptyList();
        }
        throw e;
      }
    });
  }


  public List<RepositoryArtifactDescription> getArtifacts(@NotNull String subject, @Nullable String repo, @NotNull String className) {
    return emptyList(); // no such API
  }

  public List<RepositoryArtifactDescription> getArtifacts(@NotNull String subject, @Nullable String repo,
                                                          @Nullable String groupIdTemplate, @Nullable String artifactIdTemplate)
      throws IOException {
    StringBuilder urlBuilder = new StringBuilder(BINTRAY_API_URL + "search/packages/maven?subject=").append(subject);
    if (isNotEmpty(repo)) {
      urlBuilder.append("&repo=").append(repo);
    }
    if (isEmpty(groupIdTemplate) && isEmpty(artifactIdTemplate)) {
      return emptyList();
    }

    urlBuilder.append("&q=*").append(join(asList(groupIdTemplate, artifactIdTemplate), ":")).append("*");

    List<RepositoryArtifactDescription> artifacts = new ArrayList<>();
    executeRequest(urlBuilder.toString(), BintrayModel.Package[].class, packages -> {
      for (BintrayModel.Package p : packages) {
        for (String groupAndArtifactId : p.system_ids) {
          List<String> list = split(groupAndArtifactId, ":");
          if (list.size() != 2) continue;

          String groupId = list.get(0);
          String artifactId = list.get(1);
          for (String version : p.versions) {
            RepositoryArtifactDescription artifact = new RepositoryArtifactDescription(
              groupId, artifactId, version, "jar", null, null, getRepositoryId(p.owner, p.repo));
            artifacts.add(artifact);
          }
        }
      }
    }, BintrayEndpoint::defaultExceptionHandler, null);
    return artifacts;
  }

  private static String getRepositoryId(String subject, String repo) {
    return "bintray/" + subject + "/" + repo;
  }

  private static RemoteRepositoryDescription toRepositoryDescription(BintrayModel.Repository r) {
    return new RemoteRepositoryDescription(getRepositoryId(r.subject, r.repo), r.repo, r.getUrl());
  }

  public <Data, E extends Throwable>
  void executeRequest(@NotNull String url, @NotNull Class<Data> responseDataClass,
                      @NotNull ThrowableConsumer<Data, IOException> responseHandler,
                      @NotNull ExceptionHandler<E> exceptionHandler,
                      @Nullable DoubleConsumer progressHandler) throws IOException, E {
    AtomicReference<Throwable> exception = new AtomicReference<>();

    HttpRequests.request(url).accept("application/json").connect(request -> {
      URLConnection urlConnection = request.getConnection();
      handleRequest(request, responseDataClass, responseHandler);

      int endPos = urlConnection.getHeaderFieldInt("X-RangeLimit-EndPos", Integer.MAX_VALUE);
      int totalEntries = urlConnection.getHeaderFieldInt("X-RangeLimit-Total", -1);
      if (endPos >= totalEntries) {
        if (progressHandler != null) {
          progressHandler.accept(1d);
        }
      }
      else {
        int totalIterations = 1 + (totalEntries - 1) / endPos;
        int threadsCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        boolean useThreadPool = threadsCount > 1 && totalIterations >= 10;
        CountDownLatch cdl = useThreadPool ? new CountDownLatch(threadsCount) : null;

        AtomicInteger iterationsCounter = new AtomicInteger(0);
        AtomicInteger finishedIterations = new AtomicInteger(1);

        if (progressHandler != null) {
          progressHandler.accept(1d / totalIterations);
        }
        Runnable task = () -> {
          try {
            while (true) {
              int i = iterationsCounter.incrementAndGet();
              if (i >= totalIterations) {
                break;
              }

              try {
                HttpRequests.request(url + "&start_pos=" + (i * endPos)).accept("application/json").connect(r -> {
                  handleRequest(r, responseDataClass, responseHandler);
                  if (progressHandler != null) {
                    progressHandler.accept(1d * finishedIterations.incrementAndGet() / totalIterations);
                  }
                  return null;
                });
              }
              catch (Throwable e) {
                exception.set(e);
              }
              if (exception.get() != null) {
                break;
              }
            }
          }
          finally {
            if (useThreadPool) {
              cdl.countDown();
            }
          }
        };
        if (useThreadPool) {
          ExecutorService executorService = Executors.newFixedThreadPool(threadsCount);
          for (int i = 0; i < threadsCount; i++) {
            executorService.submit(task);
          }
          try {
            cdl.await();
          }
          catch (InterruptedException ignored) {
          }
        }
        else {
          task.run();
        }
      }
      return null;
    });

    Throwable t = exception.get();
    if (t != null) {
      exceptionHandler.handle(t);
    }
  }

  private <Data> void handleRequest(HttpRequests.Request request, Class<Data> responseDataClass,
                                    ThrowableConsumer<Data, IOException> responseHandler) throws IOException {
    try (InputStream in = request.getInputStream();
         Reader reader = new InputStreamReader(in)) {
      Data data = gson.fromJson(reader, responseDataClass);
      responseHandler.consume(data);
    }
    catch (JsonParseException jsonException) {
      throw new IOException("Unexpected response format", jsonException);
    }
  }

  private static void defaultExceptionHandler(Throwable throwable) throws IOException {
    if (throwable instanceof IOException) {
      throw (IOException)throwable;
    }
    throw new IOException(throwable);
  }
}
