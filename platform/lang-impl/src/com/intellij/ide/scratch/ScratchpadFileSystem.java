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

import com.intellij.icons.AllIcons;
import com.intellij.ide.presentation.Presentation;
import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class ScratchpadFileSystem extends DummyFileSystem {
  private static final String PROTOCOL = "scratchpad";
  private final Map<String, VirtualFile> myCachedFiles = ContainerUtil.newHashMap();

  public static ScratchpadFileSystem getScratchFileSystem() {
    return (ScratchpadFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public void removeByPrefix(@NotNull final String prefix) {
    List<String> toRemove = ContainerUtil.findAll(myCachedFiles.keySet(), new Condition<String>() {
      @Override
      public boolean value(String s) {
        return s.startsWith(prefix);
      }
    });
    for (String s : toRemove) {
      myCachedFiles.remove(s);
    }
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    VirtualFile file = myCachedFiles.get(path);
    if (file != null && file.isValid()) return file;
    return null;
  }

  @NotNull
  public VirtualFile addFile(@NotNull String name, @NotNull Language language, @NotNull String prefix) {
    VirtualFile file = new MyLightVirtualFile(name, language, prefix);
    myCachedFiles.put(file.getPath(), file);
    return file;
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @NotNull
  @Override
  public String extractPresentableUrl(@NotNull String path) {
    String substring = StringUtil.substringAfter(path, "/");
    return substring != null ? substring : super.extractPresentableUrl(path);
  }

  @Presentation(provider = ScratchPresentation.class)
  private static class MyLightVirtualFile extends LightVirtualFile {
    private final String myPrefix;

    public MyLightVirtualFile(@NotNull String fileName, @NotNull Language language, @NotNull String projectPrefix) {
      super(fileName, language, "");
      myPrefix = projectPrefix;
    }

    @NotNull
    @Override
    public VirtualFileSystem getFileSystem() {
      return getScratchFileSystem();
    }

    @NotNull
    @Override
    public String getPath() {
      return myPrefix + super.getPath();
    }
  }

  public static class ScratchPresentation extends PresentationProvider<MyLightVirtualFile> {
    @Override
    public Icon getIcon(@NotNull MyLightVirtualFile file) {
      return LayeredIcon.create(file.getFileType().getIcon(), AllIcons.Actions.New);
    }
  }
}
