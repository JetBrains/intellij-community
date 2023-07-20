// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class RepoPackage implements Comparable {
  private final String myName;
  final @Nullable String myRepoUrl;
  final @Nullable String myLatestVersion;
  private final Collection<String> myKeywords;

  public RepoPackage(String name, String repoUrl) {
   this(name, repoUrl, null);
  }

  public RepoPackage(String name, final @Nullable String repoUrl, @Nullable String latestVersion) {
    this(name, repoUrl, latestVersion, Collections.emptyList());
  }

  public RepoPackage(String name, final @Nullable String repoUrl, @Nullable String latestVersion, Collection<String> keywords) {
    myName = name;
    myRepoUrl = repoUrl;
    myLatestVersion = latestVersion;
    myKeywords = keywords;
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public @Nullable @NlsSafe String getRepoUrl() {
    return myRepoUrl;
  }

  public @Nullable String getLatestVersion() {
    return myLatestVersion;
  }

  public Collection<String> getKeywords() { return myKeywords; }

  @Override
  public int compareTo(Object o) {
    if (o instanceof RepoPackage)
      return myName.compareTo(((RepoPackage)o).getName());
    return 0;
  }
}