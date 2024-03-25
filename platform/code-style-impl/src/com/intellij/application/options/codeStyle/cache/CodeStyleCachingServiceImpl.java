// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Supplier;

public final class CodeStyleCachingServiceImpl implements CodeStyleCachingService, Disposable {
  public static final int MAX_CACHE_SIZE = 100;

  private static final Key<SoftReference<CodeStyleCachedValueProvider>> PROVIDER_KEY = Key.create("code.style.cached.value.provider");

  private final Map<String, FileData> myFileDataCache = new HashMap<>();

  private final Object CACHE_LOCK = new Object();

  private final PriorityQueue<FileData> myRemoveQueue = new PriorityQueue<>(
    MAX_CACHE_SIZE,
    Comparator.comparingLong(fileData -> fileData.lastRefTimeStamp));
  private final Project myProject;

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
  public CodeStyleSettings tryGetSettings(@NotNull VirtualFile file) {
    return getOrCreateCachedValueProvider(file).tryGetSettings();
  }

  @Override
  public void scheduleWhenSettingsComputed(@NotNull PsiFile file, @NotNull Runnable runnable) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      runnable.run();
    }
    else {
      getOrCreateCachedValueProvider(virtualFile).scheduleWhenComputed(runnable);
    }
  }

  private @NotNull CodeStyleCachedValueProvider getOrCreateCachedValueProvider(@NotNull VirtualFile virtualFile) {
    synchronized (CACHE_LOCK) {
      FileData fileData = getOrCreateFileData(getFileKey(virtualFile));
      SoftReference<CodeStyleCachedValueProvider> providerRef = fileData.getUserData(PROVIDER_KEY);
      CodeStyleCachedValueProvider provider = providerRef != null ? providerRef.get() : null;
      if (provider == null || provider.isExpired()) {
        Supplier<VirtualFile> fileSupplier;
        if (virtualFile instanceof LightVirtualFile) {
          LightVirtualFile copy = getCopy((LightVirtualFile)virtualFile);
          fileSupplier = () -> getCopy(copy); // create new copy each time it requested to make sure the attached PSI is collected
        }
        else {
          fileSupplier = () -> virtualFile;
        }
        provider = new CodeStyleCachedValueProvider(fileSupplier, myProject, fileData);
        fileData.putUserData(PROVIDER_KEY, new SoftReference<>(provider));
      }
      return provider;
    }
  }

  private static @NotNull LightVirtualFile getCopy(@NotNull LightVirtualFile original) {
    VirtualFile parent = original.getParent();
    return new LightVirtualFile(original, original.getContent(), original.getModificationStamp()) {
      @Override
      public VirtualFile getParent() {
        return parent;
      }
    };
  }

  private void clearCache() {
    synchronized (CACHE_LOCK) {
      myFileDataCache.values().forEach(fileData -> {
        SoftReference<CodeStyleCachedValueProvider> providerRef = fileData.getUserData(PROVIDER_KEY);
        CodeStyleCachedValueProvider provider = providerRef != null ? providerRef.get() : null;
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
