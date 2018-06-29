// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntellijTestDiscoveryProducer implements TestDiscoveryProducer {
  private static final String INTELLIJ_TEST_DISCOVERY_HOST = "http://intellij-test-discovery";

  @NotNull
  @Override
  public MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                                     @NotNull String classFQName,
                                                     @NotNull String methodName,
                                                     byte frameworkId) {
    if (!ApplicationManager.getApplication().isInternal()) {
      return MultiMap.emptyInstance();
    }
    String methodFqn = classFQName + "." + methodName;
    String url = INTELLIJ_TEST_DISCOVERY_HOST + "/search/tests/by-method?fqn=" + methodFqn;
    LOG.debug(url);

    RequestBuilder r = HttpRequests.request(url)
                                   .productNameAsUserAgent()
                                   .gzip(true);
    try {
      return r.connect(request -> {
        MultiMap<String, String> map = new MultiMap<>();
        TestsSearchResult result = new ObjectMapper().readValue(request.getInputStream(), TestsSearchResult.class);
        result.getTests().forEach((classFqn, testMethodName) -> map.putValues(classFqn, testMethodName));
        return map;
      });
    }
    catch (HttpRequests.HttpStatusException http) {
      LOG.debug("No tests found for " + methodFqn);
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    return MultiMap.empty();
  }

  @Override
  public boolean isRemote() {
    return true;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TestsSearchResult {
    @Nullable
    private String method;

    @SerializedName("class")
    @JsonProperty("class")
    @Nullable
    private String className;

    private int found;

    @NotNull
    private Map<String, List<String>> tests = new HashMap<>();

    @Nullable
    private String message;

    @Nullable
    public String getMethod() {
      return method;
    }

    public TestsSearchResult setMethod(String method) {
      this.method = method;
      return this;
    }

    @Nullable
    public String getClassName() {
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

    @NotNull
    public Map<String, List<String>> getTests() {
      return tests;
    }

    public TestsSearchResult setTests(@NotNull Map<String, List<String>> tests) {
      this.tests = tests;
      return this;
    }

    @Nullable
    public String getMessage() {
      return message;
    }

    public TestsSearchResult setMessage(String message) {
      this.message = message;
      return this;
    }
  }
}
