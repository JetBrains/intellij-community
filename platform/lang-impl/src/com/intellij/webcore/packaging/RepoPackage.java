package com.intellij.webcore.packaging;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * User: catherine
 */
public class RepoPackage implements Comparable {
  private final String myName;
  private final String myRepoUrl;
  @Nullable final String myLatestVersion;
  private final Collection<String> myKeywords;

  public RepoPackage(String name, String repoUrl) {
   this(name, repoUrl, null);
  }

  public RepoPackage(String name, String repoUrl, @Nullable String latestVersion) {
    this(name, repoUrl, latestVersion, Collections.<String>emptyList());
  }

  public RepoPackage(String name, String repoUrl, @Nullable String latestVersion, Collection<String> keywords) {
    myName = name;
    myRepoUrl = repoUrl;
    myLatestVersion = latestVersion;
    myKeywords = keywords;
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

  public Collection<String> getKeywords() { return myKeywords; }

  @Override
  public int compareTo(Object o) {
    if (o instanceof RepoPackage)
      return myName.compareTo(((RepoPackage)o).getName());
    return 0;
  }
}