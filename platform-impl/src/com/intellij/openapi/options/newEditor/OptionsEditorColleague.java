package com.intellij.openapi.options.newEditor;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.options.Configurable;

interface OptionsEditorColleague {
  void onSelected(@Nullable Configurable configurable, final Configurable oldConfigurable);

  void onModifiedAdded(final Configurable configurable);

  void onModifiedRemoved(final Configurable configurable);

  void onErrorsChanged();

  class Adapter implements OptionsEditorColleague {
    public void onSelected(@Nullable final Configurable configurable, final Configurable oldConfigurable) {
    }

    public void onModifiedAdded(final Configurable configurable) {
    }

    public void onModifiedRemoved(final Configurable configurable) {
    }

    public void onErrorsChanged() {
    }
  }

}