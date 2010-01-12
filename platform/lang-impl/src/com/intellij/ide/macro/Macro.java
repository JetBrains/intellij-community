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

package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class Macro {
  public static final ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("com.intellij.macro");

  public static final class ExecutionCancelledException extends Exception {
  }

  protected String myCachedPreview;

  @NonNls public abstract String getName();
  public abstract String getDescription();
  @Nullable
  public abstract String expand(DataContext dataContext) throws ExecutionCancelledException;

  public void cachePreview(DataContext dataContext) {
    try{
      myCachedPreview = expand(dataContext);
    }
    catch(ExecutionCancelledException e){
      myCachedPreview = "";
    }
  }

  public final String preview() {
    return myCachedPreview;
  }

  /**
   * @return never null
   */
  static String getPath(VirtualFile file) {
    return file.getPath().replace('/', File.separatorChar);
  }

  static File getIOFile(VirtualFile file) {
    return new File(getPath(file));
  }

  @Nullable
  protected static VirtualFile getVirtualDirOrParent(DataContext dataContext) {
    VirtualFile vFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (vFile != null && !vFile.isDirectory()) {
      vFile = vFile.getParent();
    }
    return vFile;
  }

  public static class Silent extends Macro {
    private final Macro myDelegate;
    private final String myValue;

    public Silent(Macro delegate, String value) {
      myDelegate = delegate;
      myValue = value;
    }

    public String expand(DataContext dataContext) throws ExecutionCancelledException {
      return myValue;
    }

    public String getDescription() {
      return myDelegate.getDescription();
    }

    public String getName() {
      return myDelegate.getName();
    }
  }
}
