// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GithubTagInfo {
  private final String myName;
  private final String myZipballUrl;
  @JsonIgnore
  private Version myVersion;
  @JsonIgnore
  private boolean myRecentTag = false;


  public GithubTagInfo(@NotNull String name, @NotNull String zipballUrl) {
    myName = name;
    myZipballUrl = zipballUrl;
  }

  @JsonCreator
  public static GithubTagInfo createInstance(
    @JsonProperty("name") String name,
    @JsonProperty("zipball_url") String zipballUrl) {
    return new GithubTagInfo(name, zipballUrl);
  }

  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  public @NotNull String getZipballUrl() {
    return myZipballUrl;
  }

  public void setRecentTag(boolean recentTag) {
    myRecentTag = recentTag;
  }

  public boolean isRecentTag() {
    return myRecentTag;
  }

  public @NotNull Version getVersion() {
    if (myVersion == null) {
      myVersion = createVersionComponents();
    }
    return myVersion;
  }

  private @NotNull Version createVersionComponents() {
    String tagName = myName;
    if (tagName.startsWith("v.")) { //NON-NLS
      tagName = tagName.substring(2);
    } else if (StringUtil.startsWithChar(tagName, 'v')) {
      tagName = tagName.substring(1);
    }
    IntList intComponents=new IntArrayList();
    int startInd = 0;
    while (true) {
      int ind = tagName.indexOf('.', startInd);
      if (ind == -1) {
        break;
      }
      String s = tagName.substring(startInd, ind);
      try {
        int x = Integer.parseInt(s);
        intComponents.add(x);
        startInd = ind + 1;
      }
      catch (NumberFormatException e) {
        break;
      }
    }
    int nonDigitInd = startInd;
    while (nonDigitInd < tagName.length()) {
      if (!Character.isDigit(tagName.charAt(nonDigitInd))) {
        break;
      }
      nonDigitInd++;
    }
    String digitStr = tagName.substring(startInd, nonDigitInd);
    if (!digitStr.isEmpty()) {
      intComponents.add(Integer.parseInt(digitStr));
    }
    String labelWithVersion = tagName.substring(nonDigitInd);
    int lastNonDigitInd = labelWithVersion.length() - 1;
    while (lastNonDigitInd >= 0) {
      if (!Character.isDigit(labelWithVersion.charAt(lastNonDigitInd))) {
        break;
      }
      lastNonDigitInd--;
    }
    String labelVersionStr = labelWithVersion.substring(lastNonDigitInd + 1);
    String label = labelWithVersion.substring(0, lastNonDigitInd + 1);
    int labelVersion = Integer.MAX_VALUE;
    if (!labelVersionStr.isEmpty()) {
      labelVersion = Integer.parseInt(labelVersionStr);
    }
    return new Version(intComponents, label, labelVersion);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GithubTagInfo info = (GithubTagInfo)o;

    return myName.equals(info.myName) && myZipballUrl.equals(info.myZipballUrl);
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myZipballUrl.hashCode();
    return result;
  }

  public static final class Version implements Comparable<Version> {
    private final IntList myIntComponents;
    private final String myLabel;
    private final int myLabelVersion;

    public Version(@NotNull IntList intComponents,
                   @NotNull String label,
                   int labelVersion) {
      myIntComponents = new IntArrayList(intComponents);
      myLabel = label;
      myLabelVersion = labelVersion;
    }

    @Override
    public int compareTo(Version other) {
      int minSize = Math.min(myIntComponents.size(), other.myIntComponents.size());
      for (int i = 0; i < minSize; i++) {
        int thisN = myIntComponents.getInt(i);
        int otherN = other.myIntComponents.getInt(i);
        if (thisN != otherN) {
          return thisN - otherN;
        }
      }
      if (myIntComponents.size() != other.myIntComponents.size()) {
        return myIntComponents.size() - other.myIntComponents.size();
      }
      int labelCompare = myLabel.compareTo(other.myLabel);
      if (labelCompare != 0) {
        if (myLabel.isEmpty()) {
          return 1;
        }
        if (other.myLabel.isEmpty()) {
          return -1;
        }
        return labelCompare;
      }
      return myLabelVersion - other.myLabelVersion;
    }
  }

  public static @Nullable GithubTagInfo tryCast(@Nullable Object o) {
    return ObjectUtils.tryCast(o, GithubTagInfo.class);
  }

  @Override
  public String toString() {
    return getName();
  }
}
