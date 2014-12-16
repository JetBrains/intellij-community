package com.intellij.openapi.util.diff.tools.util.threeside;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ThreesideTextContentPanel extends ThreesideContentPanel {
  @NotNull private final List<? extends Editor> myEditors;

  public ThreesideTextContentPanel(@NotNull List<? extends Editor> editors,
                                   @NotNull List<JComponent> titleComponents) {
    super(getComponents(editors), titleComponents);
    myEditors = editors;
  }

  @NotNull
  private static List<JComponent> getComponents(@NotNull List<? extends Editor> editors) {
    List<JComponent> result = new ArrayList<JComponent>();

    for (Editor editor : editors) {
      result.add(editor != null ? editor.getComponent() : null);
    }
    return result;
  }

  public void setScrollbarPainter(@NotNull ButtonlessScrollBarUI.ScrollbarRepaintCallback painter) {
    ((EditorEx)myEditors.get(1)).registerScrollBarRepaintCallback(painter);
  }
}
