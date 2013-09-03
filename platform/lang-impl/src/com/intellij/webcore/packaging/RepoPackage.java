package com.intellij.webcore.packaging;

import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 */
public class RepoPackage implements Comparable {
  private final String myName;
  private final String myRepoUrl;
  @Nullable final String myLatestVersion;

  public RepoPackage(String name, String repoUrl) {
    myName = name;
    myRepoUrl = repoUrl;
    myLatestVersion = null;
  }

  public RepoPackage(String name, String repoUrl, @Nullable String latestVersion) {
    myName = name;
    myRepoUrl = repoUrl;
    myLatestVersion = latestVersion;
  }

  public String getName() {
    return myName;
  }

  public String getRepoUrl() {
    return myRepoUrl;
  }

  @Nullable
  public String getLatestVersion() {
    return myLatestVersion;
  }

  @Override
  public int compareTo(Object o) {
    if (o instanceof RepoPackage)
      return myName.compareTo(((RepoPackage)o).getName());
    return 0;
  }
}