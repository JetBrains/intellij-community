/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.buildfiles;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ForcedBuildFileAttribute {
  private static final Logger LOG = Logger.getInstance(ForcedBuildFileAttribute.class);

  private static final FileAttribute FRAMEWORK_FILE_ATTRIBUTE = new FileAttribute("forcedBuildFileFrameworkAttribute", 2, false);
  private static final Key<String> FRAMEWORK_FILE_MARKER = Key.create("forcedBuildFileFrameworkAttribute");


  private ForcedBuildFileAttribute() {
  }

  public static boolean belongsToFramework(VirtualFile file, @NotNull String frameworkId) {
    return frameworkId.equals(getFrameworkIdOfBuildFile(file));
  }

  @Nullable
  public static String getFrameworkIdOfBuildFile(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      final DataInputStream is = FRAMEWORK_FILE_ATTRIBUTE.readAttribute(file);
      if (is != null) {
        try {
          try {
            if (is.available() == 0) {
              return null;
            }
            return IOUtil.readString(is);
          }
          finally {
            is.close();
          }
        }
        catch (IOException e) {
          LOG.error(file.getPath(), e);
        }
      }
      return "";
    }
    return file.getUserData(FRAMEWORK_FILE_MARKER);
  }


  public static void forceFileToFramework(VirtualFile file, String frameworkId, boolean value) {
    //belongs to other framework - do not override!
    String existingFrameworkId = getFrameworkIdOfBuildFile(file);
    if (!StringUtil.isEmpty(existingFrameworkId) && !frameworkId.equals(existingFrameworkId)) {
      return;
    }

    if (value) {//write framework
      forceBuildFile(file, frameworkId);
    }
    else {
      forceBuildFile(file, null);
    }
  }

  private static void forceBuildFile(VirtualFile file, @Nullable String value) {
    if (file instanceof NewVirtualFile) {
      try (DataOutputStream os = FRAMEWORK_FILE_ATTRIBUTE.writeAttribute(file)) {
        IOUtil.writeString(StringUtil.notNullize(value), os);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      file.putUserData(FRAMEWORK_FILE_MARKER, value);
    }
  }
}
