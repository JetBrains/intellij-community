/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ScratchpadManagerImpl extends ScratchpadManager implements Disposable {
  private final Project myProject;
  private final Map<String, Integer> myExtensionsCounterMap = ContainerUtil.newHashMap();
  private Language myLatestLanguage;

  public ScratchpadManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public VirtualFile createScratchFile(@NotNull final Language language) {
    myLatestLanguage = language;
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        String name = generateFileName(language);
        return ScratchpadFileSystem.getScratchFileSystem().addFile(name, language, calculatePrefix(ScratchpadManagerImpl.this.myProject));
      }
    });
  }

  @Override
  public boolean isScratchFile(@NotNull final VirtualFile file) {
    return file.getFileSystem() instanceof ScratchpadFileSystem;
  }

  @NotNull
  private static String calculatePrefix(@NotNull Project project) {
    return project.getLocationHash();
  }

  @Override
  public Language getLatestLanguage() {
    return myLatestLanguage;
  }

  @NotNull
  private String generateFileName(@NotNull Language language) {
    LanguageFileType associatedFileType = language.getAssociatedFileType();
    String ext = associatedFileType != null ? associatedFileType.getDefaultExtension() : "unknown";
    Integer prev = myExtensionsCounterMap.get(ext);
    int updated = prev == null ? 1 : ++prev;
    myExtensionsCounterMap.put(ext, updated);
    String index = updated == 1 ? "" : updated + ".";
    return "scratch." + index + ext;
  }

  @Override
  public void dispose() {
    ScratchpadFileSystem.getScratchFileSystem().removeByPrefix(calculatePrefix(myProject));
  }
}