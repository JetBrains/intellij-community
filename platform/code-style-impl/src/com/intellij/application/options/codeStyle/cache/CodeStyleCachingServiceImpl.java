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
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.Supplier;

@ApiStatus.Internal
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
      String key = getFileKey(virtualFile);
      FileData fileData = getFileData(key);
      CodeStyleCachedValueProvider provider = null;
      boolean needsFreshFileData = false;
      if (fileData == null) {
        needsFreshFileData = true;
      }
      else {
        SoftReference<CodeStyleCachedValueProvider> providerRef = fileData.getUserData(PROVIDER_KEY);
        provider = providerRef != null ? providerRef.get() : null;
        if (provider == null || provider.isExpired()) {
          needsFreshFileData = true;
        }
        else {
          Supplier<VirtualFile> supplier = provider.getFileSupplier();
          // IJPL-165316 Check whether it is a different VirtualFile at the same URL
          if (supplier instanceof VirtualFileGetter
              && !((VirtualFileGetter)supplier).virtualFile.equals(virtualFile)) {
            needsFreshFileData = true;
          }
          // Do not recompute for LightVirtualFiles.
          // Since we create copies to avoid leaks via PSI, the equality check always fails,
          // but recomputing on every access might affect performance significantly.
          // We assume that the computed settings do not depend on `virtualFile` itself.
        }
      }
      if (needsFreshFileData) {
        fileData = createFileData(key, fileData);
        Supplier<VirtualFile> fileSupplier;
        if (virtualFile instanceof LightVirtualFile) {
          LightVirtualFile copy = getCopy((LightVirtualFile)virtualFile);
          // create a new copy each time
          // it requested to make sure the attached PSI is collected
          fileSupplier = new LightVirtualFileCopyGetter(copy);
        }
        else {
          fileSupplier = new VirtualFileGetter(virtualFile);
        }
        provider = new CodeStyleCachedValueProvider(fileSupplier, myProject, fileData);
        fileData.putUserData(PROVIDER_KEY, new SoftReference<>(provider));
      }
      return provider;
    }
  }

  private record VirtualFileGetter(@NotNull VirtualFile virtualFile) implements Supplier<VirtualFile> {
    @Override
    public @NotNull VirtualFile get() {
      return virtualFile;
    }
  }

  private record LightVirtualFileCopyGetter(@NotNull LightVirtualFile virtualFile) implements Supplier<VirtualFile> {
    @Override
    public @NotNull VirtualFile get() {
      return getCopy(virtualFile);
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
    synchronized (CACHE_LOCK) {
      String key = getFileKey(virtualFile);
      FileData stored = getFileData(key);
      return stored != null ? stored : createFileData(key, stored);
    }
  }

  private @Nullable FileData getFileData(@NotNull String path) {
    final FileData fileData = myFileDataCache.get(path);
    if (fileData != null) {
      myRemoveQueue.remove(fileData);
      fileData.update();
      myRemoveQueue.add(fileData);
    }
    return fileData;
  }

  /**
   * Create a new FileData object and associate it with {@code path}.
   *
   * @param existingData the result of calling {@code getDataHolder(path)} within a same synchronized block
   */
  private @NotNull FileData createFileData(@NotNull String path, @Nullable FileData existingData) {
    if (existingData != null) {
      myFileDataCache.remove(path);
      myRemoveQueue.remove(existingData);
    }
    FileData newData = new FileData();
    if (existingData == null && myFileDataCache.size() >= MAX_CACHE_SIZE) {
      FileData fileData = myRemoveQueue.poll();
      if (fileData != null) {
        myFileDataCache.values().remove(fileData);
      }
    }
    // This service gets instantiated alongside JavaFileCodeStyleFacade in decompiler.
    // If this project is default, then it should not be used as a storage of any (temporary) data, as it has lifecycle bigger than any data.
    // In particular, default projects are disposed as application services, and during dispose of the default project any leftover data would
    // cause a leak.
    if (!myProject.isDefault()) {
      myFileDataCache.put(path, newData);
      myRemoveQueue.add(newData);
    }
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
