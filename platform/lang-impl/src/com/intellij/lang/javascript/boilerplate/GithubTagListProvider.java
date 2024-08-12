// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.javascript.boilerplate;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class GithubTagListProvider {

  private static final Logger LOG = Logger.getInstance(GithubTagListProvider.class);

  private final String myUserName;
  private final String myRepositoryName;

  public GithubTagListProvider(@NotNull String userName, @NotNull String repositoryName) {
    myUserName = userName;
    myRepositoryName = repositoryName;
  }

  public @Nullable ImmutableSet<GithubTagInfo> getCachedTags() {
    ThreadingAssertions.assertEventDispatchThread();
    File cacheFile = getTagsCacheFile();
    if (cacheFile.isFile()) {
      try {
        ImmutableSet<GithubTagInfo> tags = readTagsFromFile(cacheFile);
        LOG.info(getGeneratorName() + "tag info list has been successfully read from cache file " + cacheFile.getAbsolutePath());
        return tags;
      } catch (GeneratorException e) {
        LOG.warn("Can't read cache file " + cacheFile.getAbsolutePath(), e);
      }
    }
    return null;
  }

  public void updateTagListAsynchronously(final @NotNull GithubProjectGeneratorPeer peer) {
    Runnable action = createUpdateTagListAction(peer);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      action.run();
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(action);
    }
  }

  private Runnable createUpdateTagListAction(final @NotNull GithubProjectGeneratorPeer peer) {
    return () -> {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        peer.onTagsUpdated(Collections.emptySet());
        return;
      }
      final String[] urls = formatTagListDownloadUrls();
      @NlsContexts.DialogMessage String firstErrorMessage = null;
      for (String url : urls) {
        @NlsContexts.DialogMessage String errorMessage;
        try {
          final ImmutableSet<GithubTagInfo> tags = fetchGithubTagsByUrl(url);
          LOG.info(getGeneratorName() + "Cache has been successfully updated");
          UIUtil.invokeLaterIfNeeded(() -> peer.onTagsUpdated(tags));
          return;
        }
        catch (IOException e) {
          errorMessage = LangBundle.message("dialog.message.can.not.fetch.tags.from", url);
          LOG.warn(getGeneratorName() + errorMessage, e);
        }
        catch (GeneratorException e) {
          errorMessage = LangBundle.message("dialog.message.malformed.json.received.from", url);
          LOG.warn(getGeneratorName() + errorMessage, e);
        }
        if (firstErrorMessage == null) {
          firstErrorMessage = errorMessage;
        }
      }
      if (firstErrorMessage != null) {
        peer.onTagsUpdateError(firstErrorMessage);
      }
    };
  }

  private ImmutableSet<GithubTagInfo> fetchGithubTagsByUrl(final @NotNull String url) throws IOException, GeneratorException {
    LOG.info(getGeneratorName() + "starting cache update from " + url + " ...");
    File cacheFile = getTagsCacheFile();
    GithubDownloadUtil.downloadAtomically(null, url, cacheFile, myUserName, myRepositoryName);
    return readTagsFromFile(cacheFile);
  }

  private String getGeneratorName() {
    return "[" + myUserName + "/" + myRepositoryName + "] ";
  }

  private @NotNull ImmutableSet<GithubTagInfo> readTagsFromFile(@NotNull File file) throws GeneratorException {
    final String content;
    try {
      content = Files.toString(file, StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      throw new GeneratorException(LangBundle.message("dialog.message.can.read", file.getAbsolutePath()), e);
    }
    try {
      return parseContent(content);
    } catch (GeneratorException e) {
      String message = String.format("%s parsing version list failed: %s\n%s",
                                     getGeneratorName(),
                                     e.getMessage(),
                                     content);
      LOG.info(message, e);
      throw e;
    }
  }

  private static @NotNull ImmutableSet<GithubTagInfo> parseContent(@NotNull String tagFileContent) throws GeneratorException {
    if (tagFileContent.trim().isEmpty()) {
      throw new GeneratorException(LangBundle.message("dialog.message.can.parse.fetched.version.list.got.empty.response"));
    }
    final JsonElement jsonElement;
    try {
      JsonParser jsonParser = new JsonParser();
      jsonElement = jsonParser.parse(tagFileContent);
    } catch (Exception e) {
      throw new GeneratorException(LangBundle.message("dialog.message.can.parse.fetched.version.list.malformed.json.was.received"));
    }
    return toGithubTagList(jsonElement);
  }

  private static @NotNull ImmutableSet<GithubTagInfo> toGithubTagList(@NotNull JsonElement jsonElement) throws GeneratorException {
    if (jsonElement instanceof JsonArray array) {
      ImmutableSet.Builder<GithubTagInfo> tags = ImmutableSet.builder();
      for (JsonElement element : array) {
        if (element instanceof JsonObject obj) {
          JsonElement nameElement = obj.get("name");
          String name = null;
          if (nameElement != null) {
            name = nameElement.getAsString();
          }
          String zipball = null;
          JsonElement zipballElement = obj.get("zipball_url");
          if (zipballElement != null) {
            zipball = zipballElement.getAsString();
          }
          if (name != null && zipball != null) {
            tags.add(new GithubTagInfo(name, zipball));
          }
        }
        else {
          throw new GeneratorException(LangBundle.message("dialog.message.unexpected.child.element", element.getClass().getName()));
        }
      }
      return tags.build();
    }
    else {
      throw new GeneratorException(LangBundle.message("dialog.message.jsonelement.expected.be.instance", JsonArray.class.getName()));
    }
  }

  private @NotNull File getTagsCacheFile() {
    File dir = GithubDownloadUtil.getCacheDir(myUserName, myRepositoryName);
    return new File(dir, "tags.json");
  }

  private String @NotNull [] formatTagListDownloadUrls() {
    return new String[] {
      "https://api.github.com/repos/" + myUserName + "/" + myRepositoryName + "/tags",
      "https://download.jetbrains.com/idea/project_templates/github-tags/" + myUserName + "-" + myRepositoryName + "-tags.json"
    };
  }

}
