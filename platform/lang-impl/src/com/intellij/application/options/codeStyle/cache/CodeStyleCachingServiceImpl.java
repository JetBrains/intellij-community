// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.cache;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class CodeStyleCachingServiceImpl implements CodeStyleCachingService, Disposable {
  public final static int MAX_CACHE_SIZE = 100;

  private final static Key<CodeStyleCachedValueProvider> PROVIDER_KEY = Key.create("code.style.cached.value.provider");

  private final Map<String, FileData> myFileDataCache = new HashMap<>();
  private final Project myProject;

  private final Object CACHE_LOCK = new Object();

  private final PriorityQueue<FileData> myRemoveQueue = new PriorityQueue<>(
    MAX_CACHE_SIZE,
    Comparator.comparingLong(fileData -> fileData.lastRefTimeStamp));

  public CodeStyleCachingServiceImpl(Project project) {
    myProject = project;
    ApplicationManager.getApplication().getMessageBus().connect(this).
      subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
        @Override
        public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
          clearCache();
        }
        @Override
        public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
          clearCache();
        }
      });
  }

  @Override
  @Nullable
  public CodeStyleSettings tryGetSettings(@NotNull PsiFile file) {
    CodeStyleCachedValueProvider provider = getOrCreateCachedValueProvider(file);
    return provider != null ? provider.tryGetSettings() : null;
  }

  @Nullable
  private CodeStyleCachedValueProvider getOrCreateCachedValueProvider(@NotNull PsiFile file) {
    synchronized (CACHE_LOCK) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        String filePath = getFilePath(virtualFile);
        if (filePath != null) {
          FileData fileData = getOrCreateFileData(filePath);
          CodeStyleCachedValueProvider provider = fileData.getUserData(PROVIDER_KEY);
          if (provider == null || provider.isExpired()) {
            provider = new CodeStyleCachedValueProvider(file);
            fileData.putUserData(PROVIDER_KEY, provider);
          }
          return provider;
        }
      }
      return null;
    }
  }

  private void clearCache() {
    synchronized (CACHE_LOCK) {
      myFileDataCache.clear();
      myRemoveQueue.clear();
    }
  }


  @Override
  @Nullable
  public UserDataHolder getDataHolder(@NotNull VirtualFile virtualFile) {
    String filePath = getFilePath(virtualFile);
    return filePath != null ? getOrCreateFileData(filePath) : null;
  }

  @NotNull
  private synchronized FileData getOrCreateFileData(@NotNull String path) {
    if (myFileDataCache.containsKey(path)) {
      final FileData fileData = myFileDataCache.get(path);
      fileData.update();
      return fileData;
    }
    FileData newData = new FileData();
    if (myFileDataCache.size() >= MAX_CACHE_SIZE) {
      FileData fileData = myRemoveQueue.poll();
      if (fileData != null) {
        myFileDataCache.values().remove(fileData);
      }
    }
    myFileDataCache.put(path, newData);
    myRemoveQueue.add(newData);
    return newData;
  }

  @Nullable
  private String getFilePath(VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return myProject.getBasePath() +
             "/" + file.getNameWithoutExtension() +
             "." + file.getFileType().getDefaultExtension();
    }
    return file.getCanonicalPath();
  }

  @Override
  public void dispose() {
    clearCache();
  }

  private static class FileData extends UserDataHolderBase {
    private long lastRefTimeStamp;

    private FileData() {
      update();
    }

    void update() {
      lastRefTimeStamp = System.currentTimeMillis();
    }
  }
}
