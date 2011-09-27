
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
package com.intellij.refactoring.migration;

public class MigrationMapEntry implements Cloneable {
  private String myOldName;
  private String myNewName;
  private int myType;
  private boolean isRecursive;

  public static final int PACKAGE = 0;
  public static final int CLASS = 1;

  public MigrationMapEntry() {

  }

  public MigrationMapEntry(String oldName, String newName, int type, boolean recursive) {
    myOldName = oldName;
    myNewName = newName;
    myType = type;
    isRecursive = recursive;
  }

  public MigrationMapEntry cloneEntry() {
    MigrationMapEntry newEntry = new MigrationMapEntry();
    newEntry.myOldName = myOldName;
    newEntry.myNewName = myNewName;
    newEntry.myType = myType;
    newEntry.isRecursive = isRecursive;
    return newEntry;
  }

  public String getOldName() {
    return myOldName;
  }

  public String getNewName() {
    return myNewName;
  }

  public boolean isRecursive() {
    return isRecursive;
  }

  public int getType() {
    return myType;
  }

  public void setOldName(String name) {
    myOldName = name;
  }

  public void setNewName(String name) {
    myNewName = name;
  }

  public void setType(int type) {
    myType = type;
  }

  public void setRecursive(boolean val) {
    isRecursive = val;
  }
}
