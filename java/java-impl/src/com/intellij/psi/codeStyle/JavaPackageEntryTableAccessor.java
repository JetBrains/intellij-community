// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.codeStyle.properties.ValueListPropertyAccessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.toCommaSeparatedString;

public class JavaPackageEntryTableAccessor extends ValueListPropertyAccessor<PackageEntryTable> {

  public static final char BLANK_LINE_CHAR = '|';
  public static final String STATIC_PREFIX = "$";
  public static final String MODULE_PREFIX = "@";
  private final Field myField;

  public JavaPackageEntryTableAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
    myField = field;
  }

  @Override
  protected @Nullable PackageEntryTable fromExternal(@NotNull List<String> strList) {
    PackageEntryTable entryTable = new PackageEntryTable();
    for (String strValue : strList) {
      String parseStr = strValue.trim();
      if (!parseStr.isEmpty() && parseStr.charAt(0) == BLANK_LINE_CHAR) {
        for (int i = 0; i < parseStr.length(); i ++) {
          if (parseStr.charAt(i) == BLANK_LINE_CHAR) {
            entryTable.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          }
        }
      }
      else {
        boolean isStatic = false;
        boolean isWithSubpackages = false;
        boolean isModule = false;
        if (parseStr.startsWith(STATIC_PREFIX)) {
          parseStr = parseStr.substring(STATIC_PREFIX.length()).trim();
          isStatic = true;
        }
        if (parseStr.startsWith(MODULE_PREFIX)) {
          parseStr = parseStr.substring(MODULE_PREFIX.length()).trim();
          isModule = true;
        }
        parseStr = parseStr.trim();
        if (parseStr.endsWith("**")) {
          isWithSubpackages = true;
          parseStr = parseStr.substring(0, parseStr.length() - 1);
        }
        if ("*".equals(parseStr)) {
          if (isModule) {
            entryTable.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
          }
          else if (isStatic) {
            entryTable.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
          }
          else {
            entryTable.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
          }
        }
        else {
          entryTable.addEntry(new PackageEntry(isStatic, parseStr, isWithSubpackages));
        }
      }
    }
    if ("imports_layout".equals(getPropertyName()) &&
        JavaCodeStyleSettings.class.equals(myField.getDeclaringClass()) &&
        !ContainerUtil.exists(entryTable.getEntries(), entry -> entry == PackageEntry.ALL_MODULE_IMPORTS)) {
      entryTable.insertEntryAt(PackageEntry.ALL_MODULE_IMPORTS, 0);
    }
    return entryTable;
  }

  @Override
  protected @NotNull List<String> toExternal(@NotNull PackageEntryTable value) {
    List<String> externalList = new ArrayList<>();
    for (PackageEntry entry : value.getEntries()) {
      if (entry == PackageEntry.BLANK_LINE_ENTRY) {
        externalList.add(String.valueOf(BLANK_LINE_CHAR));
      }
      else {
        StringBuilder entryBuilder = new StringBuilder();
        if (entry.isStatic()) {
          entryBuilder.append(STATIC_PREFIX);
        }
        if (entry == PackageEntry.ALL_MODULE_IMPORTS) {
          entryBuilder.append(MODULE_PREFIX);
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

  @Override
  protected @Nullable String valueToString(@NotNull List<String> value) {
    return toCommaSeparatedString(value);
  }

  @Override
  public boolean isEmptyListAllowed() {
    return false;
  }
}
