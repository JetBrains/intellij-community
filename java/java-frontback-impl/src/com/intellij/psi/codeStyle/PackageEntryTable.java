// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PackageEntryTable implements JDOMExternalizable, Cloneable {
  private static final String NAME = "name";
  private static final String STATIC = "static";
  private static final String MODULE = "module";
  private static final String SUBPACKAGES = "withSubpackages";
  private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(NAME, STATIC, MODULE, SUBPACKAGES);

  private final List<PackageEntry> myEntries = new ArrayList<>();

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PackageEntryTable other)) {
      return false;
    }
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

  @Override
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
    return myEntries.toArray(new PackageEntry[0]);
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
    boolean checkCompatibilityEnabled = Registry.is("code.style.package.entry.table.check.compatibility", false);
    myEntries.clear();
    List<Element> children = element.getChildren();
    for (final Element e : children) {
      @NonNls String name = e.getName();
      if ("package".equals(name)) {
        if (checkCompatibilityEnabled && !isCompatible(e)) {
          continue;
        }
        String packageName = e.getAttributeValue(NAME);
        boolean isStatic = Boolean.parseBoolean(e.getAttributeValue(STATIC));
        boolean isModule = Boolean.parseBoolean(e.getAttributeValue(MODULE));
        boolean withSubpackages = Boolean.parseBoolean(e.getAttributeValue(SUBPACKAGES));
        if (packageName == null) {
          throw new InvalidDataException();
        }
        PackageEntry entry;
        if (packageName.isEmpty()) {
          if (isModule) {
            entry = PackageEntry.ALL_MODULE_IMPORTS;
          }
          else if (isStatic) {
            entry = PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY;
          }
          else {
            entry = PackageEntry.ALL_OTHER_IMPORTS_ENTRY;
          }
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

  private static boolean isCompatible(Element e) {
    for (Attribute attribute : e.getAttributes()) {
      if (!ALLOWED_ATTRIBUTES.contains(attribute.getName())) {
        return false;
      }
    }
    return true;
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
        element.setAttribute(NAME, entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY ||
                                     entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY ||
                                     entry == PackageEntry.ALL_MODULE_IMPORTS ? "": packageName);
        element.setAttribute(SUBPACKAGES, entry.isWithSubpackages() ? "true" : "false");
        element.setAttribute(STATIC, entry.isStatic() ? "true" : "false");
        if (entry == PackageEntry.ALL_MODULE_IMPORTS) {
          element.setAttribute(MODULE, "true");
        }
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
