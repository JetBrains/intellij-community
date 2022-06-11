// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.cache;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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

public final class CodeStyleCachingServiceImpl implements CodeStyleCachingService, Disposable {
  public static final int MAX_CACHE_SIZE = 100;

  private static final Key<CodeStyleCachedValueProvider> PROVIDER_KEY = Key.create("code.style.cached.value.provider");

  private final Map<String, FileData> myFileDataCache = new HashMap<>();

  private final Object CACHE_LOCK = new Object();

  private final PriorityQueue<FileData> myRemoveQueue = new PriorityQueue<>(
    MAX_CACHE_SIZE,
    Comparator.comparingLong(fileData -> fileData.lastRefTimeStamp));

  public CodeStyleCachingServiceImpl() {
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
  public @Nullable CodeStyleSettings tryGetSettings(@NotNull PsiFile file) {
    CodeStyleCachedValueProvider provider = getOrCreateCachedValueProvider(file);
    return provider != null ? provider.tryGetSettings() : null;
  }

  @Override
  public void scheduleWhenSettingsComputed(@NotNull PsiFile file, @NotNull Runnable runnable) {
    CodeStyleCachedValueProvider provider = getOrCreateCachedValueProvider(file);
    if (provider != null) {
      provider.scheduleWhenComputed(runnable);
    }
    else {
      runnable.run();
    }
  }

  private @Nullable CodeStyleCachedValueProvider getOrCreateCachedValueProvider(@NotNull PsiFile file) {
    synchronized (CACHE_LOCK) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        FileData fileData = getOrCreateFileData(getFileKey(virtualFile));
        CodeStyleCachedValueProvider provider = fileData.getUserData(PROVIDER_KEY);
        if (provider == null || provider.isExpired()) {
          provider = new CodeStyleCachedValueProvider(file);
          fileData.putUserData(PROVIDER_KEY, provider);
        }
        return provider;
      }
      return null;
    }
  }

  private void clearCache() {
    synchronized (CACHE_LOCK) {
      myFileDataCache.values().forEach(fileData -> {
        CodeStyleCachedValueProvider provider = fileData.getUserData(PROVIDER_KEY);
        if (provider != null) {
          provider.cancelComputation();
        }
      });
      myFileDataCache.clear();
      myRemoveQueue.clear();
    }
  }


  @Override
  public @NotNull UserDataHolder getDataHolder(@NotNull VirtualFile virtualFile) {
    return getOrCreateFileData(getFileKey(virtualFile));
  }

  private synchronized @NotNull FileData getOrCreateFileData(@NotNull String path) {
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

  private static @NotNull String getFileKey(VirtualFile file) {
    return file.getUrl();
  }

  @Override
  public void dispose() {
    clearCache();
  }

  private static final class FileData extends UserDataHolderBase {
    private long lastRefTimeStamp;

    private FileData() {
      update();
    }

    void update() {
      lastRefTimeStamp = System.currentTimeMillis();
    }
  }
}
