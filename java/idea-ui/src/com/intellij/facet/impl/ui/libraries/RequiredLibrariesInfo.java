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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class RequiredLibrariesInfo {

  private final List<LibraryInfo> myLibraryInfos = new ArrayList<LibraryInfo>();

  public RequiredLibrariesInfo() {}

  public RequiredLibrariesInfo(LibraryInfo... libs) {
    myLibraryInfos.addAll(new ArrayList<LibraryInfo>(Arrays.asList(libs)));
  }

  public void addLibraryInfo(LibraryInfo lib) {
    myLibraryInfos.add(lib);
  }

  public @Nullable RequiredClassesNotFoundInfo checkLibraries(VirtualFile[] libraryFiles, boolean all) {
    List<LibraryInfo> infos = new ArrayList<LibraryInfo>();
    List<String> classes = new ArrayList<String>();

    for (LibraryInfo info : myLibraryInfos) {
      boolean notFound = all && info.getRequiredClasses().length == 0;
      for (String className : info.getRequiredClasses()) {
        if (!LibraryUtil.isClassAvailableInLibrary(libraryFiles, className)) {
          classes.add(className);
          notFound = true;
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
