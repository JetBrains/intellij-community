package com.intellij.ide.structureView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to specify searchable text separately from its {@link ItemPresentation} for {@link StructureViewTreeElement}.
 */
public interface SearchableTextProvider extends StructureViewTreeElement {
  /**
   * Returns text to match for {@link StructureViewTreeElement}.
   */
  default @NlsSafe @Nullable String getSearchableText() {
    return null;
  }
}
