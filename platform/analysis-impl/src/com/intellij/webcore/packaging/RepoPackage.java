// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.webcore.packaging;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class RepoPackage implements Comparable {
  private final String myName;
  @Nullable final String myRepoUrl;
  @Nullable final String myLatestVersion;
  private final Collection<String> myKeywords;

  public RepoPackage(String name, String repoUrl) {
   this(name, repoUrl, null);
  }

  public RepoPackage(String name, @Nullable final String repoUrl, @Nullable String latestVersion) {
    this(name, repoUrl, latestVersion, Collections.emptyList());
  }

  public RepoPackage(String name, @Nullable final String repoUrl, @Nullable String latestVersion, Collection<String> keywords) {
    myName = name;
    myRepoUrl = repoUrl;
    myLatestVersion = latestVersion;
    myKeywords = keywords;
  }

  public @NlsSafe String getName() {
    return myName;
  }

  @Nullable
  public @NlsSafe String getRepoUrl() {
    return myRepoUrl;
  }

  @Nullable
  public String getLatestVersion() {
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