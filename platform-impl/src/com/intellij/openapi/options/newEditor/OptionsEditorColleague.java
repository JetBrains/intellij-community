package com.intellij.openapi.options.newEditor;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.ActionCallback;

interface OptionsEditorColleague {
  ActionCallback onSelected(@Nullable Configurable configurable, final Configurable oldConfigurable);

  ActionCallback onModifiedAdded(final Configurable configurable);

  ActionCallback onModifiedRemoved(final Configurable configurable);

  ActionCallback onErrorsChanged();

  class Adapter implements OptionsEditorColleague {
    public ActionCallback onSelected(@Nullable final Configurable configurable, final Configurable oldConfigurable) {
      return new ActionCallback.Done();
    }

    public ActionCallback onModifiedAdded(final Configurable configurable) {
      return new ActionCallback.Done();
    }

    public ActionCallback onModifiedRemoved(final Configurable configurable) {
      return new ActionCallback.Done();
    }

    public ActionCallback onErrorsChanged() {
      return new ActionCallback.Done();
    }
  }

}