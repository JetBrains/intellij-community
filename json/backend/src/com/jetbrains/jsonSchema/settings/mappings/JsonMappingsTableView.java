// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.json.JsonBundle;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.StatusText;
import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellEditor;

final class JsonMappingsTableView extends TableView<UserDefinedJsonSchemaConfiguration.Item> {
  private final StatusText myEmptyText;

  JsonMappingsTableView(JsonSchemaMappingsView.MyAddActionButtonRunnable runnable) {
    myEmptyText = new StatusText() {
      @Override
      protected boolean isStatusVisible() {
        return isEmpty();
      }
    };
    myEmptyText.setText(JsonBundle.message("no.schema.mappings.defined"))
               .appendSecondaryText(JsonBundle.message("add.mapping.for.a"), SimpleTextAttributes.REGULAR_ATTRIBUTES, null);

    JsonMappingKind[] values = JsonMappingKind.values();
    for (int i = 0; i < values.length; i++) {
      JsonMappingKind kind = values[i];
      myEmptyText.appendSecondaryText(kind.getDescription(), SimpleTextAttributes.LINK_ATTRIBUTES,
                           e -> runnable.doRun(kind));
      if (i < values.length - 1) {
        myEmptyText.appendSecondaryText(", ", SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
      }
    }

    setFocusTraversalKeysEnabled(false);
  }

  @Override
  public void setCellEditor(TableCellEditor anEditor) {
    super.setCellEditor(anEditor);
    if (anEditor != null) {
      ((JsonMappingsTableCellEditor)anEditor).myComponent.getTextField().requestFocus();
    }
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return myEmptyText;
  }
}
