// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.codeStyle.properties.ValueListPropertyAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.toCommaSeparatedString;

public class JavaPackageEntryTableAccessor extends ValueListPropertyAccessor<PackageEntryTable> {

  public static final char BLANK_LINE_CHAR = '|';
  public static final String STATIC_PREFIX = "$";

  public JavaPackageEntryTableAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected PackageEntryTable fromExternal(@NotNull List<String> strList) {
    PackageEntryTable entryTable = new PackageEntryTable();
    for (String strValue : strList) {
      String parseStr = strValue.trim();
      if (parseStr.length() > 0 && parseStr.charAt(0) == BLANK_LINE_CHAR) {
        for (int i = 0; i < parseStr.length(); i ++) {
          if (parseStr.charAt(i) == BLANK_LINE_CHAR) {
            entryTable.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          }
        }
      }
      else {
        boolean isStatic = false;
        boolean isWithSubpackages = false;
        if (parseStr.startsWith(STATIC_PREFIX)) {
          parseStr = parseStr.substring(STATIC_PREFIX.length()).trim();
          isStatic = true;
        }
        parseStr = parseStr.trim();
        if (parseStr.endsWith("**")) {
          isWithSubpackages = true;
          parseStr = parseStr.substring(0, parseStr.length() - 1);
        }
        if ("*".equals(parseStr)) {
          entryTable.addEntry(isStatic ? PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY : PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
        }
        else {
          entryTable.addEntry(new PackageEntry(isStatic, parseStr, isWithSubpackages));
        }
      }
    }
    return entryTable;
  }

  @NotNull
  @Override
  protected List<String> toExternal(@NotNull PackageEntryTable value) {
    List<String> externalList = new ArrayList<>();
    for (PackageEntry entry : value.getEntries()) {
      if (entry == PackageEntry.BLANK_LINE_ENTRY) {
        externalList.add(String.valueOf(BLANK_LINE_CHAR));
      }
      else {
        StringBuilder entryBuilder = new StringBuilder();
        if (entry.isStatic()) {
          entryBuilder.append("$");
        }
        if (entry.isSpecial()) {
          entryBuilder.append("*");
        }
        else {
          entryBuilder.append(entry.getPackageName()).append(".*");
          if (entry.isWithSubpackages()) {
            entryBuilder.append("*");
          }
        }
        externalList.add(entryBuilder.toString());
      }
    }
    return externalList;
  }

  @Nullable
  @Override
  protected String valueToString(@NotNull List<String> value) {
    return toCommaSeparatedString(value);
  }
}
