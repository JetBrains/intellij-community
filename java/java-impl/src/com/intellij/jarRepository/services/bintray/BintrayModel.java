// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.services.bintray;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author ibessonov
 */
public class BintrayModel {

  public static class Repository {
    public String subject;
    public String repo;

    public Repository(@NotNull String subject, @Nullable String repo) {
      this.subject = subject;
      this.repo = repo;
    }

    public String getUrl() {
      return getUrl(subject, repo);
    }

    public static String getUrl(String subject, String repo) {
      return "https://dl.bintray.com/" + subject + (repo != null ? "/" + repo : "");
    }
  }

  public static class Package {
    public String desc;
    public String owner;
    public String repo;
    public List<String> system_ids;
    public List<String> versions;
  }
}
