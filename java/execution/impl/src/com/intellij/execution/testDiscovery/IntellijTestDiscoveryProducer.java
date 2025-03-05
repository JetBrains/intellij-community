// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.gson.annotations.SerializedName;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@InternalIgnoreDependencyViolation
public final class IntellijTestDiscoveryProducer implements TestDiscoveryProducer {
  private static final String INTELLIJ_TEST_DISCOVERY_HOST = "https://intellij-test-discovery.labs.intellij.net";

  private static final NotNullLazyValue<ObjectReader> JSON_READER = NotNullLazyValue.createValue(() -> new ObjectMapper().readerFor(TestsSearchResult.class));

  @Override
  public @NotNull MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                                              @NotNull List<? extends Couple<String>> classesAndMethods,
                                                              byte frameworkId) {
    if (!ApplicationManager.getApplication().isInternal()) {
      return MultiMap.empty();
    }
    try {
      List<String> bareClasses = new SmartList<>();
      List<Couple<String>> allTogether = new SmartList<>();

      classesAndMethods.forEach(couple -> {
        if (couple.second == null) bareClasses.add(couple.first);
        else allTogether.add(couple);
      });

      MultiMap<String, String> result = new MultiMap<>();
      result.putAllValues(request(allTogether, couple -> "\"" + couple.first + "." + couple.second + "\"", "methods"));
      result.putAllValues(request(bareClasses, s -> "\"" + s + "\"", "classes"));
      return result;
    }
    catch (HttpRequests.HttpStatusException http) {
      LOG.debug("No tests found", http);
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    return MultiMap.empty();
  }

  private static @NotNull <T> MultiMap<String, String> request(List<T> collection, Function<? super T, String> toString, String what) throws IOException {
    if (collection.isEmpty()) return MultiMap.empty();
    String url = INTELLIJ_TEST_DISCOVERY_HOST + "/search/tests/by-" + what;
    LOG.debug(url);
    return HttpRequests.post(url, "application/json").productNameAsUserAgent().gzip(true).connect(r -> {
      r.write(collection.stream().map(toString).collect(Collectors.joining(",", "[", "]")));
      TestsSearchResult search = JSON_READER.getValue().readValue(r.getInputStream());
      MultiMap<String, String> result = new MultiMap<>();
      search.getTests().forEach((classFqn, testMethodName) -> result.putValues(classFqn, testMethodName));
      return result;
    });
  }

  @Override
  public boolean isRemote() {
    return true;
  }

  @Override
  public @NotNull MultiMap<String, String> getDiscoveredTestsForFiles(@NotNull Project project, @NotNull List<String> filePaths, byte frameworkId) {
    try {
      return request(filePaths, s -> "\"" + s + "\"", "files");
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    return MultiMap.empty();
  }

  @Override
  public @NotNull List<String> getAffectedFilePaths(@NotNull Project project, @NotNull List<? extends Couple<String>> testFqns, byte frameworkId) {
    String url = INTELLIJ_TEST_DISCOVERY_HOST + "/search/test/details";
    return executeQuery(() -> HttpRequests.post(url, "application/json").productNameAsUserAgent().gzip(true).connect(
      r -> {
        r.write(testFqns.stream().map(s -> "\"" + s.getFirst() + "." + s.getSecond() + "\"").collect(Collectors.joining(",", "[", "]")));
        return Arrays.stream(new ObjectMapper().readValue(r.getInputStream(), TestDetails[].class))
          .map(details -> details.files)
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
      }), project);
  }

  @Override
  public @NotNull List<String> getAffectedFilePathsByClassName(@NotNull Project project, @NotNull String testClassName, byte frameworkId) {
    String url = INTELLIJ_TEST_DISCOVERY_HOST + "/search/files/affected/by-test-classes";
    return executeQuery(() -> HttpRequests.post(url, "application/json").productNameAsUserAgent().gzip(true).connect(
      r -> {
        r.write("[\"" + testClassName +  "\"]");
        Map<String, List<String>> map = new ObjectMapper().readValue(r.getInputStream(), new TypeReference<>() {
        });
        return ObjectUtils.notNull(ContainerUtil.getFirstItem(map.values()), Collections.emptyList());
      }), project);
  }

  @Override
  public @NotNull List<String> getFilesWithoutTests(@NotNull Project project, @NotNull Collection<String> paths) throws IOException {
    if (paths.isEmpty()) return Collections.emptyList();
    String url = INTELLIJ_TEST_DISCOVERY_HOST + "/search/files-without-related-tests";
    LOG.debug(url);
    return HttpRequests.post(url, "application/json").productNameAsUserAgent().gzip(true).connect(r -> {
      r.write(paths.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]")));
      return new ObjectMapper().readValue(r.getInputStream(), new TypeReference<>() {
      });
    });
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TestsSearchResult {
    private @Nullable String method;

    @SerializedName("class") @JsonProperty("class") private @Nullable String className;

    private int found;

    private @NotNull Map<String, List<String>> tests = new HashMap<>();

    private @Nullable String message;

    public @Nullable String getMethod() {
      return method;
    }

    public TestsSearchResult setMethod(String method) {
      this.method = method;
      return this;
    }

    public @Nullable String getClassName() {
      return className;
    }

    public TestsSearchResult setClassName(String name) {
      this.className = name;
      return this;
    }

    public int getFound() {
      return found;
    }

    public TestsSearchResult setFound(int found) {
      this.found = found;
      return this;
    }

    public @NotNull Map<String, List<String>> getTests() {
      return tests;
    }

    public TestsSearchResult setTests(@NotNull Map<String, List<String>> tests) {
      this.tests = tests;
      return this;
    }

    public @Nullable String getMessage() {
      return message;
    }

    public TestsSearchResult setMessage(String message) {
      this.message = message;
      return this;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private static class TestDetails {
    private @Nullable String method;

    @SerializedName("class") @JsonProperty("class") private @Nullable String className;

    private @Nullable List<String> files = new SmartList<>();

    private @Nullable String message;

    public @Nullable String getMethod() {
      return method;
    }

    public TestDetails setMethod(String method) {
      this.method = method;
      return this;
    }

    public @Nullable String getClassName() {
      return className;
    }

    public TestDetails setClassName(String name) {
      this.className = name;
      return this;
    }

    public @NotNull List<String> getFiles() {
      if (files == null) return Collections.emptyList();
      return files;
    }

    public TestDetails setFiles(final @NotNull List<String> files) {
      this.files = files;
      return this;
    }

    public @Nullable String getMessage() {
      return message;
    }

    public TestDetails setMessage(String message) {
      this.message = message;
      return this;
    }
  }

  private static @NotNull List<String> executeQuery(@NotNull ThrowableComputable<? extends List<String>, IOException> query, @NotNull Project project) {
    try {
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        List<String> result = ProgressManager.getInstance().run(
          new Task.WithResult<List<String>, IOException>(project,
                                                         ExecutionBundle.message("searching.for.affected.file.paths"),
                                                         true) {
            @Override
            protected List<String> compute(@NotNull ProgressIndicator indicator) throws IOException {
              return query.compute();
            }
          });
        return result == null ? Collections.emptyList() : result;
      }
      return query.compute();
    }
    catch (IOException e) {
      LOG.warn("Can't execute remote query", e);
      return Collections.emptyList();
    }
  }
}
