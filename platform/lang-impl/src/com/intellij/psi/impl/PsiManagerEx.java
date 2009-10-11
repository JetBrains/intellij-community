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
package com.intellij.psi.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class PsiManagerEx extends PsiManager {
  public abstract boolean isBatchFilesProcessingMode();

  public abstract boolean isAssertOnFileLoading(VirtualFile file);

  public abstract void nonPhysicalChange();

  public abstract void physicalChange();

  public abstract ResolveCache getResolveCache();

  public abstract void registerRunnableToRunOnChange(Runnable runnable);

  public abstract void registerWeakRunnableToRunOnChange(Runnable runnable);

  public abstract void registerRunnableToRunOnAnyChange(Runnable runnable);

  public abstract void registerRunnableToRunAfterAnyChange(Runnable runnable);

  public abstract FileManager getFileManager();

  public abstract void invalidateFile(PsiFile file);

  public abstract void beforeChildRemoval(final PsiTreeChangeEventImpl event);

  public abstract CacheManager getCacheManager();

  @NotNull
  public abstract List<? extends LanguageInjector> getLanguageInjectors();
}
