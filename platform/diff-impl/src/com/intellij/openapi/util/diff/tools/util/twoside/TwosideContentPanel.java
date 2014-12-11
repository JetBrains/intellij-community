package com.intellij.openapi.util.diff.tools.util.twoside;

import com.intellij.openapi.util.diff.tools.util.DiffSplitter;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TwosideContentPanel extends JPanel {
  @Nullable private final DiffSplitter mySplitter;

  public TwosideContentPanel(@NotNull List<JComponent> titleComponents,
                             @Nullable JComponent editor1,
                             @Nullable JComponent editor2) {
    super(new BorderLayout());
    assert titleComponents.size() == 2;

    if (editor1 != null && editor2 != null) {
      mySplitter = new DiffSplitter();
      mySplitter.setFirstComponent(new MyPanel(editor1, titleComponents.get(0)));
      mySplitter.setSecondComponent(new MyPanel(editor2, titleComponents.get(1)));
      add(mySplitter, BorderLayout.CENTER);
    }
    else {
      mySplitter = null;
      if (editor1 != null) {
        add(new MyPanel(editor1, titleComponents.get(0)), BorderLayout.CENTER);
      }
      else if (editor2 != null) {
        add(new MyPanel(editor2, titleComponents.get(1)), BorderLayout.CENTER);
      }
    }
  }

  @CalledInAwt
  public void setPainter(@Nullable DiffSplitter.Painter painter) {
    if (mySplitter != null) mySplitter.setPainter(painter);
  }

  public void repaintDivider() {
    if (mySplitter != null) mySplitter.repaintDivider();
  }

  private static class MyPanel extends JPanel {
    public MyPanel(@NotNull JComponent editor, @Nullable JComponent title) {
      super(new BorderLayout());
      add(editor, BorderLayout.CENTER);
      if (title != null) add(title, BorderLayout.NORTH);
    }
  }
}
