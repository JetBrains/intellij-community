// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

class ClasspathTableItem<T extends OrderEntry> {
  protected final @Nullable T myEntry;
  private final boolean myRemovable;

  public static @Nullable ClasspathTableItem<?> createItem(OrderEntry orderEntry, StructureConfigurableContext context) {
    return switch (orderEntry) {
      case JdkOrderEntry entry -> new ClasspathTableItem<>(entry, false);
      case LibraryOrderEntry entry -> createLibItem(entry, context);
      case ModuleOrderEntry entry -> new ClasspathTableItem<>(entry, true);
      case ModuleSourceOrderEntry entry -> new ClasspathTableItem<>(entry, false);
      case null, default -> null;
    };
  }

  public static ClasspathTableItem<LibraryOrderEntry> createLibItem(final LibraryOrderEntry orderEntry, final StructureConfigurableContext context) {
    return new LibraryItem(orderEntry, context);
  }

  protected ClasspathTableItem(@Nullable T entry, boolean removable) {
    myEntry = entry;
    myRemovable = removable;
  }

  public final boolean isExportable() {
    return myEntry instanceof ExportableOrderEntry;
  }

  public final boolean isExported() {
    return myEntry instanceof ExportableOrderEntry && ((ExportableOrderEntry)myEntry).isExported();
  }

  public final void setExported(boolean isExported) {
    if (myEntry instanceof ExportableOrderEntry) {
      ((ExportableOrderEntry)myEntry).setExported(isExported);
    }
  }

  public final @Nullable DependencyScope getScope() {
    return myEntry instanceof ExportableOrderEntry ? ((ExportableOrderEntry) myEntry).getScope() : null;
  }

  public final void setScope(DependencyScope scope) {
    if (myEntry instanceof ExportableOrderEntry) {
      ((ExportableOrderEntry) myEntry).setScope(scope);
    }
  }

  public final @Nullable T getEntry() {
    return myEntry;
  }

  public boolean isRemovable() {
    return myRemovable;
  }

  public boolean isEditable() {
    return false;
  }

  public @Nullable @NlsContexts.Tooltip String getTooltipText() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClasspathTableItem item = (ClasspathTableItem)o;
    return Comparing.equal(myEntry, item.myEntry);
  }

  @Override
  public int hashCode() {
    return myEntry != null ? myEntry.hashCode() : 0;
  }
}
