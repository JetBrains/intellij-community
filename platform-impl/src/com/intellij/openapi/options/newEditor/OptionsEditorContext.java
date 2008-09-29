package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.speedSearch.ElementFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OptionsEditorContext {

  void select(@Nullable Configurable configurable, @NotNull Listener requestor);

  @NotNull
  ElementFilter<Configurable> getFilter();
  

  interface Listener {
    void onSelected(@Nullable Configurable configurable);
  }

}