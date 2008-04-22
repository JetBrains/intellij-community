package com.intellij.facet.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface FacetEditor {

  FacetEditorTab[] getEditorTabs();

  <T extends FacetEditorTab> T getEditorTab(@NotNull Class<T> aClass);

}
