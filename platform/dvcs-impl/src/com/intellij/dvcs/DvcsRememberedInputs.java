// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class DvcsRememberedInputs {

  private State myState = new State();

  public static class State {
    public List<UrlAndUserName> visitedUrls = new ArrayList<>();
    public String cloneParentDir = "";
  }

  public static class UrlAndUserName {
    public String url;
    public String userName;
  }

  @NotNull
  public State getState() {
    return myState;
  }

  public void loadState(@NotNull State state) {
    myState = state;
  }

  public void addUrl(@NotNull String url) {
    addUrl(url, "");
  }

  public void addUrl(@NotNull String url, @NotNull String userName) {
    for (UrlAndUserName visitedUrl : myState.visitedUrls) {
      if (visitedUrl.url.equalsIgnoreCase(url)) {  // don't add multiple entries for a single url
        if (!userName.isEmpty()) {                 // rewrite username, unless no username is specified
          visitedUrl.userName = userName;
        }
        return;
      }
    }

    UrlAndUserName urlAndUserName = new UrlAndUserName();
    urlAndUserName.url = url;
    urlAndUserName.userName = userName;
    myState.visitedUrls.add(urlAndUserName);
  }

  @Nullable
  public String getUserNameForUrl(@NotNull String url) {
    for (UrlAndUserName urlAndUserName : myState.visitedUrls) {
      if (urlAndUserName.url.equalsIgnoreCase(url)) {
        return urlAndUserName.userName;
      }
    }
    return null;
  }

  @NotNull
  public List<String> getVisitedUrls() {
    List<String> urls = new ArrayList<>(myState.visitedUrls.size());
    for (UrlAndUserName urlAndUserName : myState.visitedUrls) {
      urls.add(urlAndUserName.url);
    }
    return urls;
  }

  public String getCloneParentDir() {
    return myState.cloneParentDir;
  }

  public void setCloneParentDir(String cloneParentDir) {
    myState.cloneParentDir = cloneParentDir;
  }
}
