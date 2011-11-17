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
package com.intellij.ide.util.projectWizard.importSources.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class CommonSourceRootDetectionUtil {

  private CommonSourceRootDetectionUtil() {
  }

  @Nullable
  public static Pair<File,String> suggestRootForFileWithPackageStatement(File file,
                                                                         File topmostPossibleRoot,
                                                                         NullableFunction<CharSequence, String> packageNameFetcher) {
    if (!file.isFile()) return null;

    final CharSequence chars;
    try {
      chars = new CharArrayCharSequence(FileUtil.loadFileText(file));
    }
    catch(IOException e){
      return null;
    }

    String packageName = packageNameFetcher.fun(chars);
    if (packageName != null) {
      File root = file.getParentFile();
      int index = packageName.length();
      while (index > 0) {
        int index1 = packageName.lastIndexOf('.', index - 1);
        String token = packageName.substring(index1 + 1, index);
        String dirName = root.getName();
        final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
        if (!equalsToToken || root.equals(topmostPossibleRoot)) {
          return Pair.create(root, packageName.substring(0, index));
        }
        String parent = root.getParent();
        if (parent == null) {
          return null;
        }
        root = new File(parent);
        index = index1;
      }
      return Pair.create(root, "");
    }

    return null;
  }
}
