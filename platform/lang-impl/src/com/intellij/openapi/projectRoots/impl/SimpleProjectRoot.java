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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author mike
 */
public class SimpleProjectRoot implements ProjectRoot, JDOMExternalizable {
  private String myUrl;
  private VirtualFile myFile;
  private final VirtualFile[] myFileArrray = new VirtualFile[1];
  private boolean myInitialized = false;
  @NonNls private static final String ATTRIBUTE_URL = "url";

  SimpleProjectRoot(@NotNull VirtualFile file) {
    myFile = file;
    myUrl = myFile.getUrl();
  }

  public SimpleProjectRoot(@NotNull String url) {
    myUrl = url;
  }

  SimpleProjectRoot() {
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public String getPresentableString() {
    String path = VirtualFileManager.extractPath(myUrl);
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  public VirtualFile[] getVirtualFiles() {
    if (!myInitialized) initialize();

    if (myFile == null) {
      return VirtualFile.EMPTY_ARRAY;
    }

    myFileArrray[0] = myFile;
    return myFileArrray;
  }

  public String[] getUrls() {
    return new String[]{myUrl};
  }

  public boolean isValid() {
    if (!myInitialized) {
      initialize();
    }

    return myFile != null && myFile.isValid();
  }

  public void update() {
    initialize();
  }

  private void initialize() {
    myInitialized = true;

    if (myFile == null || !myFile.isValid()) {
      myFile = VirtualFileManager.getInstance().findFileByUrl(myUrl);
      if (myFile != null && cantHaveChildren()) {
        myFile = null;
      }
    }
  }

  private boolean cantHaveChildren() {
    if (myFile.getFileSystem() instanceof HttpFileSystem) return false;
    return !myFile.isDirectory();
  }

  public String getUrl() {
    return myUrl;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myUrl = element.getAttributeValue(ATTRIBUTE_URL);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!myInitialized) {
      initialize();
    }

    element.setAttribute(ATTRIBUTE_URL, myUrl);
  }

}
