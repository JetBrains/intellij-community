/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.impl.generic.GenericCompilerCache;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.generic.GenericCompiler;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2008
 */
public class CompilerCacheManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompilerCacheManager");
  private final Map<Compiler, Object> myCompilerToCacheMap = new HashMap<>();
  private final Map<GenericCompiler<?,?,?>, GenericCompilerCache<?,?,?>> myGenericCachesMap = new HashMap<>();
  private final List<Disposable> myCacheDisposables = new ArrayList<>();
  private final File myCachesRoot;
  private final Project myProject;

  public CompilerCacheManager(Project project) {
    myProject = project;
    myCachesRoot = CompilerPaths.getCacheStoreDirectory(project);
  }

  public static CompilerCacheManager getInstance(Project project) {
    return project.getComponent(CompilerCacheManager.class);
  }
  
  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "CompilerCacheManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    flushCaches();
  }
  
  private File getCompilerRootDir(final Compiler compiler) {
    final File dir = new File(myCachesRoot, getCompilerIdString(compiler));
    dir.mkdirs();
    return dir;
  }

  public synchronized <Key, SourceState, OutputState> GenericCompilerCache<Key, SourceState, OutputState>
                                 getGenericCompilerCache(final GenericCompiler<Key, SourceState, OutputState> compiler) throws IOException {
    GenericCompilerCache<?,?,?> cache = myGenericCachesMap.get(compiler);
    if (cache == null) {
      final GenericCompilerCache<?,?,?> genericCache = new GenericCompilerCache<>(compiler, GenericCompilerRunner
        .getGenericCompilerCacheDir(myProject, compiler));
      myGenericCachesMap.put(compiler, genericCache);
      myCacheDisposables.add(new Disposable() {
        public void dispose() {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Closing cache for feneric compiler " + compiler.getId());
          }
          genericCache.close();
        }
      });
      cache = genericCache;
    }
    //noinspection unchecked
    return (GenericCompilerCache<Key, SourceState, OutputState>)cache;
  }

  public synchronized FileProcessingCompilerStateCache getFileProcessingCompilerCache(final FileProcessingCompiler compiler) throws IOException {
    Object cache = myCompilerToCacheMap.get(compiler);
    if (cache == null) {
      final File compilerRootDir = getCompilerRootDir(compiler);
      final FileProcessingCompilerStateCache stateCache = new FileProcessingCompilerStateCache(compilerRootDir, compiler);
      myCompilerToCacheMap.put(compiler, stateCache);
      myCacheDisposables.add(new Disposable() {
        public void dispose() {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Closing cache for compiler " + compiler.getDescription() + "; cache root dir: " + compilerRootDir);
          }
          stateCache.close();
        }
      });
      cache = stateCache;
    }
    else {
      LOG.assertTrue(cache instanceof FileProcessingCompilerStateCache);
    }
    return (FileProcessingCompilerStateCache)cache;
  }

  public static String getCompilerIdString(Compiler compiler) {
    @NonNls String description = compiler.getDescription();
    return description.replaceAll("\\s+", "_").replaceAll("[\\.\\?]", "_").toLowerCase();
  }
  
  public synchronized void flushCaches() {
    for (Disposable disposable : myCacheDisposables) {
      try {
        disposable.dispose();
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }
    myCacheDisposables.clear();
    myGenericCachesMap.clear();
    myCompilerToCacheMap.clear();
  }

  public void clearCaches(final CompileContext context) {
    flushCaches();
    final File[] children = myCachesRoot.listFiles();
    if (children != null) {
      for (final File child : children) {
        final boolean deleteOk = FileUtil.delete(child);
        if (!deleteOk) {
          context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.failed.to.delete", child.getPath()), null, -1, -1);
        }
      }
    }
  }
}
