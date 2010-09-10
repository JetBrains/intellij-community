/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.roots.*;
import org.jetbrains.annotations.Nullable;

/**
* @author nik
*/
class ClasspathTableItem {
  @Nullable protected final OrderEntry myEntry;
  private final boolean myRemovable;

  @Nullable
  public static ClasspathTableItem createItem(OrderEntry orderEntry) {
    if (orderEntry instanceof JdkOrderEntry) {
      return new ClasspathTableItem(orderEntry, false);
    }
    else if (orderEntry instanceof LibraryOrderEntry) {
      return createLibItem((LibraryOrderEntry)orderEntry);
    }
    else if (orderEntry instanceof ModuleOrderEntry) {
      return new ClasspathTableItem(orderEntry, true);
    }
    else if (orderEntry instanceof ModuleSourceOrderEntry) {
      return new ClasspathTableItem(orderEntry, false);
    }
    return null;
  }

  public static ClasspathTableItem createLibItem(LibraryOrderEntry orderEntry) {
    return new ClasspathTableItem(orderEntry, true) {
      public boolean isEditable() {
        return myEntry != null && myEntry.isValid();
      }
    };
  }

  protected ClasspathTableItem(@Nullable OrderEntry entry, boolean removable) {
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

  @Nullable
  public final DependencyScope getScope() {
    return myEntry instanceof ExportableOrderEntry ? ((ExportableOrderEntry) myEntry).getScope() : null;
  }

  public final void setScope(DependencyScope scope) {
    if (myEntry instanceof ExportableOrderEntry) {
      ((ExportableOrderEntry) myEntry).setScope(scope);
    }
  }

  @Nullable
  public final OrderEntry getEntry() {
    return myEntry;
  }

  public boolean isRemovable() {
    return myRemovable;
  }

  public boolean isEditable() {
    return false;
  }
}
