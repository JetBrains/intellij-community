package com.intellij.dvcs.branch;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Objects;

@Tag("branch-info")
public class DvcsBranchInfo {
  @Attribute(value = "repo") public final String repoPath;
  @Attribute(value = "source") public final String sourceName;

  @SuppressWarnings("unused")
  public DvcsBranchInfo() {
    this("", "");
  }

  public DvcsBranchInfo(String repositoryPath, String source) {
    repoPath = repositoryPath;
    sourceName = source;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DvcsBranchInfo info = (DvcsBranchInfo)o;

    if (repoPath != null ? !repoPath.equals(info.repoPath) : info.repoPath != null) return false;
    if (sourceName != null ? !sourceName.equals(info.sourceName) : info.sourceName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(repoPath, sourceName);
  }
}