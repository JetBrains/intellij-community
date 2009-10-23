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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: cdr
 */
public class PackageEntry {
  private final String myPackageName;
  private final boolean myWithSubpackages;
  private final boolean isStatic;

  public PackageEntry(boolean isStatic, @NotNull @NonNls String packageName, boolean withSubpackages) {
    this.isStatic = isStatic;
    myPackageName = packageName;
    myWithSubpackages = withSubpackages;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public boolean isWithSubpackages() {
    return myWithSubpackages;
  }

  public boolean isStatic() {
    return isStatic;
  }

  public boolean matchesPackageName(String packageName) {
    if (packageName.startsWith(myPackageName)) {
      if (packageName.length() == myPackageName.length()) return true;
      if (myWithSubpackages) {
        if (packageName.charAt(myPackageName.length()) == '.') return true;
      }
    }
    return false;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof PackageEntry)) {
      return false;
    }
    PackageEntry entry = (PackageEntry)obj;
    return entry.myWithSubpackages == myWithSubpackages
           && entry.isStatic() == isStatic()
           && Comparing.equal(entry.myPackageName, myPackageName);
  }

  public int hashCode() {
    return myPackageName.hashCode();
  }
  
  public static final PackageEntry BLANK_LINE_ENTRY = new PackageEntry(false, "<blank line>", true){
    @Override
    public boolean matchesPackageName(String packageName) {
      return false;
    }
  };
  public static final PackageEntry ALL_OTHER_IMPORTS_ENTRY = new PackageEntry(false, "<all other imports>", true){
    @Override
    public boolean matchesPackageName(String packageName) {
      return true;
    }
  };
  public static final PackageEntry ALL_OTHER_STATIC_IMPORTS_ENTRY = new PackageEntry(true, "<all other static imports>", true){
    @Override
    public boolean matchesPackageName(String packageName) {
      return true;
    }
  };

  public boolean isSpecial() {
    return this == BLANK_LINE_ENTRY || this == ALL_OTHER_IMPORTS_ENTRY || this == ALL_OTHER_STATIC_IMPORTS_ENTRY;
  }

  public boolean isBetterMatchForPackageThan(@Nullable PackageEntry entry, @NotNull String packageName, boolean isStatic) {
    if (isStatic() != isStatic || !matchesPackageName(packageName)) return false;
    if (entry == null) {
      return true;
    }
    if (entry.isStatic() != isStatic) return false;
    if (entry.isWithSubpackages() != isWithSubpackages()) {
      return !isWithSubpackages();
    }
    if (entry == ALL_OTHER_IMPORTS_ENTRY || entry == ALL_OTHER_STATIC_IMPORTS_ENTRY) return true;
    if (this == ALL_OTHER_IMPORTS_ENTRY || this == ALL_OTHER_STATIC_IMPORTS_ENTRY) return false;
    return StringUtil.countChars(entry.getPackageName(), '.') < StringUtil.countChars(getPackageName(), '.');
  }

  @NonNls
  @Override
  public String toString() {
    return (isStatic() ? "static " : "") + getPackageName();
  }
}
