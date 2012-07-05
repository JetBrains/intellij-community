/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class FileTypeDescriptor extends FileChooserDescriptor {

  private final ImmutableList<String> myExtensions;

  public FileTypeDescriptor(String title, @NotNull String... extensions) {
    super(true, false, false, true, false, false);
    assert extensions.length > 0 : "There should be at least one extension";
    myExtensions = ContainerUtil.immutableList(ContainerUtil.map(extensions, new Function<String, String>() {
      @Override
      public String fun(String ext) {
        if (ext.startsWith(".")) {
          return ext;
        }
        return "." + ext;
      }
    }));

    setTitle(title);
  }

  @Override
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {        
    if (!showHiddenFiles && FileElement.isFileHidden(file)) {
      return false;
    }
    
    if (file.isDirectory()) {
      return true;
    }

    String name = file.getName();
    for (String extension : myExtensions) {
      if (name.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isFileSelectable(VirtualFile file) {
    return !file.isDirectory() && isFileVisible(file, true);
  }
}
