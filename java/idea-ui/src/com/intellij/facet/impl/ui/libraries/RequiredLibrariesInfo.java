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

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class RequiredLibrariesInfo {
  private final List<LibraryInfo> myLibraryInfos = new ArrayList<>();

  public RequiredLibrariesInfo(LibraryInfo... libs) {
    myLibraryInfos.addAll(new ArrayList<>(Arrays.asList(libs)));
  }

  @Nullable
  public RequiredClassesNotFoundInfo checkLibraries(VirtualFile[] libraryFiles) {
    return checkLibraries(Arrays.asList(libraryFiles));
  }

  @Nullable
  public RequiredClassesNotFoundInfo checkLibraries(List<VirtualFile> libraryFiles) {
    List<LibraryInfo> infos = new ArrayList<>();
    List<String> classes = new ArrayList<>();

    for (LibraryInfo info : myLibraryInfos) {
      boolean notFound;
      final String md5 = info.getMd5();
      if (!StringUtil.isEmptyOrSpaces(md5)) {
        notFound = true;
        for (VirtualFile libraryFile : libraryFiles) {
           final VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(libraryFile);
          if (md5.equals(md5(jarFile))) {
            notFound = false;
            break;
          }
        }
      } else {
        notFound = false;
        for (String className : info.getRequiredClasses()) {
          if (!LibraryUtil.isClassAvailableInLibrary(libraryFiles, className)) {
            classes.add(className);
            notFound = true;
          }
        }
      }

      if (notFound) {
        infos.add(info);
      }
    }
    if (infos.isEmpty()) {
      return null;
    }
    return new RequiredClassesNotFoundInfo(ArrayUtil.toStringArray(classes), infos.toArray(new LibraryInfo[infos.size()]));
  }

  @Nullable
  public static String md5(@NotNull VirtualFile file) {
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      md5.update(file.contentsToByteArray());
      final byte[] digest = md5.digest();

      return new String(Hex.encodeHex(digest));
    }
    catch (Exception e) {
      return null;
    }
  }

  public static String getLibrariesPresentableText(final LibraryInfo[] libraryInfos) {
    StringBuilder missedJarsText = new StringBuilder();
    for (int i = 0; i < libraryInfos.length; i++) {
      if (i > 0) {
        missedJarsText.append(", ");
      }

      missedJarsText.append(libraryInfos[i].getName());
    }
    return missedJarsText.toString();
  }

  public static class RequiredClassesNotFoundInfo {
    private final String[] myClassNames;
    private final LibraryInfo[] myLibraryInfos;

    public RequiredClassesNotFoundInfo(final String[] classNames, final LibraryInfo[] libraryInfos) {
      myClassNames = classNames;
      myLibraryInfos = libraryInfos;
    }

    public String[] getClassNames() {
      return myClassNames;
    }

    public LibraryInfo[] getLibraryInfos() {
      return myLibraryInfos;
    }

    public String getMissingJarsText() {
      return getLibrariesPresentableText(myLibraryInfos);
    }
  }
}
