// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class JavaPackageEntryTableAccessor extends CodeStylePropertyAccessor<PackageEntryTable> {

  public static final String BLANK_LINE_ENTRY = "blank_line";
  public static final String STATIC_PREFIX = "static";

  public JavaPackageEntryTableAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected PackageEntryTable parseString(@NotNull String str) {
    PackageEntryTable entryTable = new PackageEntryTable();
    String[] parts = str.split(",");
    for (String part : parts) {
      String parseStr = part.trim();
      if (BLANK_LINE_ENTRY.equals(parseStr)) {
        entryTable.addEntry(PackageEntry.BLANK_LINE_ENTRY);
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
  protected String asString(@NotNull PackageEntryTable value) {
    StringBuilder sb = new StringBuilder();
    for (PackageEntry entry : value.getEntries()) {
      if (sb.length() > 0) sb.append(",");
      if (entry == PackageEntry.BLANK_LINE_ENTRY) {
        sb.append(BLANK_LINE_ENTRY);
      }
      else {
        if (entry.isStatic()) {
          sb.append(STATIC_PREFIX + " ");
        }
        if (entry.isSpecial()) {
          sb.append("*");
        }
        else {
          sb.append(entry.getPackageName()).append(".*");
          if (entry.isWithSubpackages()) {
            sb.append("*");
          }
        }
      }
    }
    return sb.toString();
  }
}
