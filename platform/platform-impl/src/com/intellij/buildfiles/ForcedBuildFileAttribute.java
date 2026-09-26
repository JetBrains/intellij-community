// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class ForcedBuildFileAttribute {
  private static final Logger LOG = Logger.getInstance(ForcedBuildFileAttribute.class);

  private static final FileAttribute FRAMEWORK_FILE_ATTRIBUTE = new FileAttribute("forcedBuildFileFrameworkAttribute", 2, false);
  private static final Key<String> FRAMEWORK_FILE_MARKER = Key.create("forcedBuildFileFrameworkAttribute");


  private ForcedBuildFileAttribute() {
  }

  public static boolean belongsToFramework(VirtualFile file, @NotNull String frameworkId) {
    return frameworkId.equals(getFrameworkIdOfBuildFile(file));
  }

  public static @Nullable String getFrameworkIdOfBuildFile(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      try (DataInputStream is = FRAMEWORK_FILE_ATTRIBUTE.readFileAttribute(file)) {
        if (is != null) {
          if (is.available() == 0) {
            return null;
          }
          return IOUtil.readString(is);
        }
      }
      catch (IOException e) {
        LOG.error(file.getPath(), e);
      }
      return "";
    }
    return file.getUserData(FRAMEWORK_FILE_MARKER);
  }


  public static void forceFileToFramework(VirtualFile file, String frameworkId, boolean value) {
    // belongs to another framework - do not override!
    String existingFrameworkId = getFrameworkIdOfBuildFile(file);
    if (!StringUtil.isEmpty(existingFrameworkId) && !frameworkId.equals(existingFrameworkId)) {
      return;
    }

    if (value) {
      // write a framework
      forceBuildFile(file, frameworkId);
    }
    else {
      forceBuildFile(file, null);
    }
  }

  private static void forceBuildFile(VirtualFile file, @Nullable String value) {
    if (file instanceof NewVirtualFile) {
      try (DataOutputStream os = FRAMEWORK_FILE_ATTRIBUTE.writeFileAttribute(file)) {
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
