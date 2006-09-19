/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.openapi.compiler.options;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ExcludedEntriesConfiguration implements JDOMExternalizable {
  private static final @NonNls String FILE = "file";
  private static final @NonNls String DIRECTORY = "directory";
  private static final @NonNls String URL = "url";
  private static final @NonNls String INCLUDE_SUBDIRECTORIES = "includeSubdirectories";
  private List<ExcludeEntryDescription> myExcludeEntryDescriptions = new ArrayList<ExcludeEntryDescription>();
  private Factory<FileChooserDescriptor> myFileChooserDescriptorFactory;


  public ExcludedEntriesConfiguration(final Factory<FileChooserDescriptor> factory) {
    myFileChooserDescriptorFactory = factory;
  }

  public ExcludeEntryDescription[] getExcludeEntryDescriptions() {
    return myExcludeEntryDescriptions.toArray(new ExcludeEntryDescription[myExcludeEntryDescriptions.size()]);
  }

  public void addExcludeEntryDescription(ExcludeEntryDescription description) {
    myExcludeEntryDescriptions.add(description);
  }

  public void removeAllExcludeEntryDescriptions() {
    myExcludeEntryDescriptions.clear();
  }

  public void readExternal(final Element node) {
    for (final Object o : node.getChildren()) {
      Element element = (Element)o;
      String url = element.getAttributeValue(URL);
      if (url == null) continue;
      if (FILE.equals(element.getName())) {
        ExcludeEntryDescription excludeEntryDescription = new ExcludeEntryDescription(url, false, true);
        myExcludeEntryDescriptions.add(excludeEntryDescription);
      }
      if (DIRECTORY.equals(element.getName())) {
        boolean includeSubdirectories = Boolean.parseBoolean(element.getAttributeValue(INCLUDE_SUBDIRECTORIES));
        ExcludeEntryDescription excludeEntryDescription = new ExcludeEntryDescription(url, includeSubdirectories, false);
        myExcludeEntryDescriptions.add(excludeEntryDescription);
      }
    }
  }

  public void writeExternal(final Element element) {
    for (final ExcludeEntryDescription description : myExcludeEntryDescriptions) {
      if (description.isFile()) {
        Element entry = new Element(FILE);
        entry.setAttribute(URL, description.getUrl());
        element.addContent(entry);
      }
      else {
        Element entry = new Element(DIRECTORY);
        entry.setAttribute(URL, description.getUrl());
        entry.setAttribute(INCLUDE_SUBDIRECTORIES, Boolean.toString(description.isIncludeSubdirectories()));
        element.addContent(entry);
      }
    }
  }

  public boolean isExcluded(VirtualFile virtualFile) {
    for (final ExcludeEntryDescription entryDescription : myExcludeEntryDescriptions) {
      VirtualFile descriptionFile = entryDescription.getVirtualFile();
      if (descriptionFile == null) {
        continue;
      }
      if (entryDescription.isFile()) {
        if (descriptionFile.equals(virtualFile)) {
          return true;
        }
      }
      else if (entryDescription.isIncludeSubdirectories()) {
        if (VfsUtil.isAncestor(descriptionFile, virtualFile, false)) {
          return true;
        }
      }
      else {
        if (virtualFile.isDirectory()) {
          continue;
        }
        if (descriptionFile.equals(virtualFile.getParent())) {
          return true;
        }
      }
    }
    return false;
  }

  public FileChooserDescriptor getFileChooserDescriptor() {
    return myFileChooserDescriptorFactory.create();
  }
}
