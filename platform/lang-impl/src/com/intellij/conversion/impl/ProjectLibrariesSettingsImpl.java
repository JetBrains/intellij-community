/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ProjectLibrariesSettings;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectLibrariesSettingsImpl implements ProjectLibrariesSettings {
  private SettingsXmlFile myProjectFile;
  private final List<SettingsXmlFile> myLibrariesFiles;

  public ProjectLibrariesSettingsImpl(@Nullable File projectFile, @Nullable File[] librariesFiles,
                                      ConversionContextImpl context) throws CannotConvertException {
    if (projectFile == null && librariesFiles == null) {
      throw new IllegalArgumentException("Either project file or libraries files should be not null");
    }

    if (projectFile != null && projectFile.exists()) {
      myProjectFile = context.getOrCreateFile(projectFile);
    }

    myLibrariesFiles = new ArrayList<SettingsXmlFile>();
    if (librariesFiles != null) {
      for (File file : librariesFiles) {
        myLibrariesFiles.add(context.getOrCreateFile(file));
      }
    }
  }

  @Override
  @NotNull
  public Collection<? extends Element> getProjectLibraries() {
    final List<Element> result = new ArrayList<Element>();
    if (myProjectFile != null) {
      result.addAll(JDOMUtil.getChildren(myProjectFile.findComponent("libraryTable"), LibraryImpl.ELEMENT));
    }

    for (SettingsXmlFile file : myLibrariesFiles) {
      result.addAll(JDOMUtil.getChildren(file.getRootElement(), LibraryImpl.ELEMENT));
    }

    return result;
  }

  public Collection<File> getAffectedFiles() {
    final List<File> files = new ArrayList<File>();
    if (myProjectFile != null) {
      files.add(myProjectFile.getFile());
    }
    for (SettingsXmlFile file : myLibrariesFiles) {
      files.add(file.getFile());
    }
    return files;
  }

}
