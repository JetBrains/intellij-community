package com.intellij.lang.javascript.boilerplate;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.platform.templates.github.DownloadUtil;
import com.intellij.platform.templates.github.GeneratorException;
import com.intellij.platform.templates.github.GithubTagInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Simonchik
 */
public class GithubTagListProvider {

  private static final Logger LOG = Logger.getInstance(GithubTagListProvider.class);

  private final String myUserName;
  private final String myRepositoryName;

  public GithubTagListProvider(@Nullable String userName, @NotNull String repositoryName) {
    myUserName = userName;
    myRepositoryName = repositoryName;
  }

  @Nullable
  public ImmutableSet<GithubTagInfo> getCachedTags() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    File cacheFile = getCacheFile();
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

  public Task.Backgroundable updateTagListAsynchronously(final GithubProjectGeneratorPeer peer) {
    final String url = formatTagListDownloadUrl();
    Task.Backgroundable task =
      new Task.Backgroundable(null, "Updating versions of " + GithubTagListProvider.this.myRepositoryName + " repository...", true, null) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          File cacheFile = getCacheFile();
          try {
            DownloadUtil.downloadAtomically(indicator, url, cacheFile, myUserName, myRepositoryName);
            final ImmutableSet<GithubTagInfo> infos = readTagsFromFile(cacheFile);
            peer.setErrorMessage(null);
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                peer.updateTagList(infos);
              }
            });
          }
          catch (IOException e) {
            peer.setErrorMessage("Can not fetch tag list from '" + url + "'!");
          }
          catch (GeneratorException e) {
            peer.setErrorMessage(getGeneratorName() + " cache update failed");
          }
        }
      };

    LOG.info(getGeneratorName() + " starting cache update from " + url + " ...");
    ProgressManager.getInstance().run(task);
    return task;
  }

  private String getGeneratorName() {
    return "[" + myUserName + "/" + myRepositoryName + "] ";
  }

  @NotNull
  private ImmutableSet<GithubTagInfo> readTagsFromFile(@NotNull File file) throws GeneratorException {
    final String content;
    try {
      content = Files.toString(file, Charsets.UTF_8);
    }
    catch (IOException e) {
      throw new GeneratorException("Can not read '" + file.getAbsolutePath() + "'!", e);
    }
    try {
      return parseContent(content);
    } catch (GeneratorException e) {
      String message = String.format("%s parsing version list was failed: %s\n%s",
                                     getGeneratorName(),
                                     e.getMessage(),
                                     content);
      LOG.info(message, e);
      throw e;
    }
  }

  @NotNull
  private static ImmutableSet<GithubTagInfo> parseContent(@NotNull String tagFileContent) throws GeneratorException {
    if (tagFileContent.trim().isEmpty()) {
      throw new GeneratorException("Can not parse fetched version list: got empty response");
    }
    final JsonElement jsonElement;
    try {
      JsonParser jsonParser = new JsonParser();
      jsonElement = jsonParser.parse(tagFileContent);
    } catch (Exception e) {
      throw new GeneratorException("Can not parse fetched version list: malformed JSON was received");
    }
    return toGithubTagList(jsonElement);
  }

  @NotNull
  private static ImmutableSet<GithubTagInfo> toGithubTagList(@NotNull JsonElement jsonElement) throws GeneratorException {
    if (jsonElement instanceof JsonArray) {
      JsonArray array = (JsonArray) jsonElement;
      ImmutableSet.Builder<GithubTagInfo> tags = ImmutableSet.builder();
      for (JsonElement element : array) {
        if (element instanceof JsonObject) {
          JsonObject obj = (JsonObject) element;
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
          throw new GeneratorException("Unexpected child element " + element.getClass().getName());
        }
      }
      return tags.build();
    }
    else {
      throw new GeneratorException("jsonElement is expected be instance of " + JsonArray.class.getName());
    }
  }

  @NotNull
  private File getCacheFile() {
    return DownloadUtil.findCacheFile(myUserName, myRepositoryName, "tags.json");
  }

  @NotNull
  private String formatTagListDownloadUrl() {
    StringBuilder builder = new StringBuilder("https://api.github.com/repos/");
    if (myUserName != null) {
      builder.append(myUserName).append("/");
    }
    return builder.append(myRepositoryName).append("/tags").toString();
  }
}
