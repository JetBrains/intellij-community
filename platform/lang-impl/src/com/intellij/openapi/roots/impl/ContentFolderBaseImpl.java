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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public abstract class ContentFolderBaseImpl extends RootModelComponentBase implements ContentFolder, Comparable<ContentFolderBaseImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleContentFolderBaseImpl");
  private final VirtualFilePointer myFilePointer;
  protected final ContentEntryImpl myContentEntry;
  @NonNls public static final String URL_ATTRIBUTE = "url";

  ContentFolderBaseImpl(VirtualFile file, ContentEntryImpl contentEntry) {
    this(VirtualFilePointerManager.getInstance().create(file, contentEntry.getRootModel().getModule(), contentEntry.getRootModel().myVirtualFilePointerListener), contentEntry);
  }

  ContentFolderBaseImpl(String url, ContentEntryImpl contentEntry) {
    this(VirtualFilePointerManager.getInstance().create(url, contentEntry.getRootModel().getModule(), contentEntry.getRootModel().myVirtualFilePointerListener), contentEntry);
  }

  protected ContentFolderBaseImpl(ContentFolderBaseImpl that, ContentEntryImpl contentEntry) {
    this(VirtualFilePointerManager.getInstance().duplicate(that.myFilePointer, contentEntry.getRootModel().getModule(), contentEntry.getRootModel().myVirtualFilePointerListener), contentEntry);
  }

  ContentFolderBaseImpl(Element element, ContentEntryImpl contentEntry) throws InvalidDataException {
    this(getUrlFrom(element), contentEntry);
  }

  private static String getUrlFrom(Element element) throws InvalidDataException {
    String url = element.getAttributeValue(URL_ATTRIBUTE);
    if (url == null) throw new InvalidDataException();
    return url;
  }

  ContentFolderBaseImpl(VirtualFilePointer filePointer, ContentEntryImpl contentEntry) {
    super(contentEntry.getRootModel());
    myContentEntry = contentEntry;
    myFilePointer = filePointer;
  }

  public VirtualFile getFile() {
    final VirtualFile file = myFilePointer.getFile();
    if (file == null || file.isDirectory()) {
      return file;
    }
    else {
      return null;
    }
  }

  @NotNull
  public ContentEntry getContentEntry() {
    return myContentEntry;
  }

  protected void writeFolder(Element element, String elementName) {
    LOG.assertTrue(element.getName().equals(elementName));
    element.setAttribute(URL_ATTRIBUTE, myFilePointer.getUrl());
  }

  @NotNull
  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public boolean isSynthetic() {
    return false;
  }

  public int compareTo(ContentFolderBaseImpl folder) {
    return getUrl().compareTo(folder.getUrl());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ContentFolderBaseImpl)) return false;
    return compareTo((ContentFolderBaseImpl)obj) == 0;
  }

  @Override
  public int hashCode() {
    return getUrl().hashCode();
  }
}
