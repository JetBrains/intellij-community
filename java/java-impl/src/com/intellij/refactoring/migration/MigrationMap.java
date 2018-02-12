
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.migration;

import com.intellij.openapi.util.io.FileUtil;

import java.util.ArrayList;

public class MigrationMap {
  private String myName;
  private String myDescription;
  private final ArrayList<MigrationMapEntry> myEntries = new ArrayList<>();
  private String myFileName;

  public MigrationMap() {

  }

  public MigrationMap(MigrationMapEntry[] entries) {
    for (int i = 0; i < entries.length; i++) {
      MigrationMapEntry entry = entries[i];
      addEntry(entry);
    }
  }

  public MigrationMap cloneMap() {
    MigrationMap newMap = new MigrationMap();
    newMap.myName = myName;
    newMap.myDescription = myDescription;
    newMap.myFileName = myFileName;
    for(int i = 0; i < myEntries.size(); i++){
      MigrationMapEntry entry = getEntryAt(i);
      newMap.addEntry(entry.cloneEntry());
    }
    return newMap;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public void addEntry(MigrationMapEntry entry) {
    myEntries.add(entry);
  }

  public void removeEntryAt(int index) {
    myEntries.remove(index);
  }

  public void removeAllEntries() {
    myEntries.clear();
  }

  public int getEntryCount() {
    return myEntries.size();
  }

  public MigrationMapEntry getEntryAt(int index) {
    return myEntries.get(index);
  }

  public void setEntryAt(MigrationMapEntry entry, int index) {
    myEntries.set(index, entry);
  }

  public String toString() {
    return getName();
  }

  public String getFileName() {
    if (myFileName == null) {
      return FileUtil.sanitizeFileName(myName, false);
    }
    return myFileName;
  }

  public void setFileName(String fileName) {
    myFileName = fileName;
  }
}



