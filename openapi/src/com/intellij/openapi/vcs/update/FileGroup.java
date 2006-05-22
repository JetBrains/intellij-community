/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.SimpleTextAttributes;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class FileGroup implements JDOMExternalizable {

  public String myUpdateName;
  public String myStatusName;

  private final Collection<String> myFiles = new ArrayList<String>();
  public boolean mySupportsDeletion;
  public boolean myCanBeAbsent;
  public String myId;
  @NonNls private static final String PATH = "PATH";

  private final List<FileGroup> myChildren = new ArrayList<FileGroup>();
  @NonNls private static final String FILE_GROUP_ELEMENT_NAME = "FILE-GROUP";
  @NonNls public static final String MODIFIED_ID = "MODIFIED";
  @NonNls public static final String MERGED_WITH_CONFLICT_ID = "MERGED_WITH_CONFLICTS";
  @NonNls public static final String MERGED_ID = "MERGED";
  @NonNls public static final String UNKNOWN_ID = "UNKNOWN";
  @NonNls public static final String LOCALLY_ADDED_ID = "LOCALLY_ADDED";
  @NonNls public static final String LOCALLY_REMOVED_ID = "LOCALLY_REMOVED";
  @NonNls public static final String UPDATED_ID = "UPDATED";
  @NonNls public static final String REMOVED_FROM_REPOSITORY_ID = "REMOVED_FROM_REPOSITORY";
  @NonNls public static final String CREATED_ID = "CREATED";
  @NonNls public static final String RESTORED_ID = "RESTORED";
  @NonNls public static final String CHANGED_ON_SERVER_ID = "CHANGED_ON_SERVER";
  @NonNls public static final String SKIPPED_ID = "SKIPPED";

  /**
   * @param updateName - Name for "update" action
   * @param statusName - Name for "status action"
   * @param supportsDeletion - User can perform delete action for files from the group
   * @param id - Using in order to find the group
   * @param canBeAbsent - If canBeAbsent == true absent files from the group will not be marked as invalid
   */
  public FileGroup(String updateName, String statusName, boolean supportsDeletion, String id, boolean canBeAbsent) {
    mySupportsDeletion = supportsDeletion;
    myId = id;
    myCanBeAbsent = canBeAbsent;
    myUpdateName = updateName;
    myStatusName = statusName;
  }

  public FileGroup() {
  }

  public void addChild(FileGroup child) {
    myChildren.add(child);
  }

  public boolean getSupportsDeletion() {
    return mySupportsDeletion;
  }

  public void add(final String path) {
    myFiles.add(path);
  }

  public Collection<String> getFiles() {
    return myFiles;
  }

  public boolean isEmpty() {
    if (!myFiles.isEmpty()) return false;
    for (Iterator<FileGroup> iterator = myChildren.iterator(); iterator.hasNext();) {
      FileGroup child = iterator.next();
      if (!child.isEmpty()) return false;
    }
    return true;
  }

  public SimpleTextAttributes getInvalidAttributes() {
    if (myCanBeAbsent) {
      return new SimpleTextAttributes(Font.PLAIN, FileStatus.DELETED.getColor());
    }
    else {
      return SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
  }

  public String getId() {
    return myId;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (Iterator iterator = myFiles.iterator(); iterator.hasNext();) {
      String s = (String)iterator.next();
      Element path = new Element(PATH);
      path.setText(s);
      element.addContent(path);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    List pathElements = element.getChildren(PATH);
    for (Iterator iterator = pathElements.iterator(); iterator.hasNext();) {
      Element pathElement = (Element)iterator.next();
      myFiles.add(pathElement.getText());
    }
  }

  public List<FileGroup> getChildren() {
    return myChildren;
  }

  public static void writeGroupsToElement(List<FileGroup> groups, Element element) throws WriteExternalException {
    for (Iterator<FileGroup> iterator = groups.iterator(); iterator.hasNext();) {
      FileGroup fileGroup = iterator.next();
      Element groupElement = new Element(FILE_GROUP_ELEMENT_NAME);
      element.addContent(groupElement);
      fileGroup.writeExternal(groupElement);
      writeGroupsToElement(fileGroup.getChildren(), groupElement);
    }
  }

  public static void readGroupsFromElement(List<FileGroup> groups, Element element) throws InvalidDataException {
    List groupElements = element.getChildren();
    for (Iterator iterator = groupElements.iterator(); iterator.hasNext();) {
      Element groupElement = (Element)iterator.next();
      FileGroup fileGroup = new FileGroup();
      fileGroup.readExternal(groupElement);
      groups.add(fileGroup);
      readGroupsFromElement(fileGroup.myChildren, groupElement);
    }
  }

  public String getStatusName() {
    return myStatusName;
  }

  public String getUpdateName() {
    return myUpdateName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return myId + " " + myFiles.size() + " items";
  }
}
