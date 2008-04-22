package com.intellij.facet.ui;

import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public interface MultipleFacetEditorHelper {

  void bind(@NotNull ThreeStateCheckBox common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JCheckBox> fun);

  void bind(@NotNull JTextField common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JTextField> fun);

  void bind(@NotNull JComboBox common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JComboBox> fun);


  void unbind();
}
