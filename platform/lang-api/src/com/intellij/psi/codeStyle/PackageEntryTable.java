/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
* User: cdr
*/
public class PackageEntryTable implements JDOMExternalizable, Cloneable {
  private final List<PackageEntry> myEntries = new ArrayList<>();

  public boolean equals(Object obj) {
    if (!(obj instanceof PackageEntryTable)) {
      return false;
    }
    PackageEntryTable other = (PackageEntryTable)obj;
    if (other.myEntries.size() != myEntries.size()) {
      return false;
    }
    for (int i = 0; i < myEntries.size(); i++) {
      PackageEntry entry = myEntries.get(i);
      PackageEntry otherentry = other.myEntries.get(i);
      if (!Comparing.equal(entry, otherentry)) {
        return false;
      }
    }
    return true;
  }

  public int hashCode() {
    if (!myEntries.isEmpty() && myEntries.get(0) != null) {
      return myEntries.get(0).hashCode();
    }
    return 0;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    PackageEntryTable clon = new PackageEntryTable();
    clon.copyFrom(this);
    return clon;
  }

  public void copyFrom(PackageEntryTable packageTable) {
    myEntries.clear();
    myEntries.addAll(packageTable.myEntries);
  }

  public PackageEntry[] getEntries() {
    return myEntries.toArray(new PackageEntry[myEntries.size()]);
  }

  public void insertEntryAt(PackageEntry entry, int i) {
    myEntries.add(i, entry);
  }

  public void removeEntryAt(int i) {
    myEntries.remove(i);
  }

  public PackageEntry getEntryAt(int i) {
    return myEntries.get(i);
  }

  public int getEntryCount() {
    return myEntries.size();
  }

  public void setEntryAt(PackageEntry entry, int i) {
    myEntries.set(i, entry);
  }

  public boolean contains(String packageName) {
    for (PackageEntry entry : myEntries) {
      if (packageName.startsWith(entry.getPackageName())) {
        if (packageName.length() == entry.getPackageName().length()) return true;
        if (entry.isWithSubpackages()) {
          if (packageName.charAt(entry.getPackageName().length()) == '.') return true;
        }
      }
    }
    return false;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myEntries.clear();
    List children = element.getChildren();
    for (final Object aChildren : children) {
      @NonNls Element e = (Element)aChildren;
      @NonNls String name = e.getName();
      if ("package".equals(name)) {
        String packageName = e.getAttributeValue("name");
        boolean isStatic = Boolean.parseBoolean(e.getAttributeValue("static"));
        boolean withSubpackages = Boolean.parseBoolean(e.getAttributeValue("withSubpackages"));
        if (packageName == null) {
          throw new InvalidDataException();
        }
        PackageEntry entry;
        if (packageName.isEmpty()) {
          entry = isStatic ? PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY : PackageEntry.ALL_OTHER_IMPORTS_ENTRY;
        }
        else {
          entry = new PackageEntry(isStatic, packageName, withSubpackages);
        }
        myEntries.add(entry);
      }
      else {
        if ("emptyLine".equals(name)) {
          myEntries.add(PackageEntry.BLANK_LINE_ENTRY);
        }
      }
    }
  }

  @Override
  public void writeExternal(Element parentNode) throws WriteExternalException {
    for (PackageEntry entry : myEntries) {
      if (entry == PackageEntry.BLANK_LINE_ENTRY) {
        @NonNls Element element = new Element("emptyLine");
        parentNode.addContent(element);
      }
      else {
        @NonNls Element element = new Element("package");
        parentNode.addContent(element);
        String packageName = entry.getPackageName();
        element.setAttribute("name", entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY || entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY ? "": packageName);
        element.setAttribute("withSubpackages", entry.isWithSubpackages() ? "true" : "false");
        element.setAttribute("static", entry.isStatic() ? "true" : "false");
      }
    }
  }

  public void removeEmptyPackages() {
    for(int i = myEntries.size()-1; i>=0; i--){
      PackageEntry entry = myEntries.get(i);
      if(StringUtil.isEmptyOrSpaces(entry.getPackageName())) {
        removeEntryAt(i);
      }
    }
  }

  public void addEntry(PackageEntry entry) {
    myEntries.add(entry);
  }
}
