package com.intellij.openapi.util.diff.tools.util.twoside;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TwosideTextContentPanel extends TwosideContentPanel {
  public TwosideTextContentPanel(@NotNull List<JComponent> titleComponents,
                                 @Nullable Editor editor1,
                                 @Nullable Editor editor2) {
    super(titleComponents, getComponent(editor1), getComponent(editor2));
  }

  @Nullable
  private static JComponent getComponent(@Nullable Editor editor) {
    return editor != null ? editor.getComponent() : null;
  }
}
