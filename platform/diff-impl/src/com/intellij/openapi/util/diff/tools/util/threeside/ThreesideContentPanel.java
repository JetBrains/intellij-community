package com.intellij.openapi.util.diff.tools.util.threeside;

import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.Side;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ThreesideContentPanel extends JPanel {
  private final List<DiffDivider> myDividers;
  private final List<JComponent> myContents;

  public ThreesideContentPanel(@NotNull List<JComponent> editors, @NotNull List<JComponent> title) {
    assert editors.size() == 3;

    myContents = new ArrayList<JComponent>(3);
    for (int i = 0; i < 3; i++) {
      myContents.add(new MyPanel(editors.get(i), title.get(i)));
    }

    myDividers = ContainerUtil.newArrayList(new DiffDivider(), new DiffDivider());

    addAll(myContents);
    addAll(myDividers);
  }

  private void addAll(@NotNull List<? extends JComponent> components) {
    for (JComponent component : components) {
      add(component, -1);
    }
  }

  @NotNull
  public List<DiffDivider> getDividers() {
    return myDividers;
  }


  @CalledInAwt
  public void setPainter(@Nullable DiffDivider.Painter painter, @NotNull Side side) {
    side.selectN(getDividers()).setPainter(painter);
  }

  public void repaintDividers() {
    for (DiffDivider divider : getDividers()) {
      divider.repaint();
    }
  }

  public void repaintDivider(@NotNull Side side) {
    side.selectN(getDividers()).repaint();
  }

  public void doLayout() {
    int width = getWidth();
    int height = getHeight();
    int dividersTotalWidth = 0;
    for (JComponent divider : myDividers) {
      dividersTotalWidth += divider.getPreferredSize().width;
    }
    int panelWidth = (width - dividersTotalWidth) / 3;
    int x = 0;
    for (int i = 0; i < myContents.size(); i++) {
      JComponent editor = myContents.get(i);
      editor.setBounds(x, 0, panelWidth, height);
      editor.validate();
      x += panelWidth;
      if (i < myDividers.size()) {
        JComponent divider = myDividers.get(i);
        int dividerWidth = divider.getPreferredSize().width;
        divider.setBounds(x, 0, dividerWidth, height);
        divider.validate();
        x += dividerWidth;
      }
    }
  }

  public static class DiffDivider extends JComponent {
    @Nullable private Painter myPainter;

    public Dimension getPreferredSize() {
      return new Dimension(30, 1);
    }

    public void paint(Graphics g) {
      super.paint(g);
      if (myPainter != null) myPainter.paint(g, this);
    }

    @CalledInAwt
    public void setPainter(@Nullable Painter painter) {
      myPainter = painter;
    }

    public interface Painter {
      void paint(@NotNull Graphics g, @NotNull Component divider);
    }
  }

  private static class MyPanel extends JPanel {
    public MyPanel(@NotNull JComponent editor, @Nullable JComponent title) {
      super(new BorderLayout());
      add(editor, BorderLayout.CENTER);
      if (title != null) add(title, BorderLayout.NORTH);
    }
  }
}
