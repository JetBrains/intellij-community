// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Highlighters implements DnDEvent.DropTargetHighlightingType {
  private static final List<DropTargetHighlighter> ourHightlighters = new ArrayList<>();

  private static final ArrayList<DropTargetHighlighter> ourCurrentHighlighters = new ArrayList<>();

  static {
    ourHightlighters.add(new RectangleHighlighter());
    ourHightlighters.add(new FilledRectangleHighlighter());
    ourHightlighters.add(new HorizontalLinesHighlighter());
    ourHightlighters.add(new TextHighlighter());
    ourHightlighters.add(new ErrorTextHighlighter());
    ourHightlighters.add(new VerticalLinesHighlighter());
  }

  static void show(int aType, JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
    List<DropTargetHighlighter> toShow = new ArrayList<>();
    for (DropTargetHighlighter ourHightlighter : ourHightlighters) {
      DropTargetHighlighter each = (DropTargetHighlighter)ourHightlighter;
      if ((each.getMask() & aType) != 0) {
        toShow.add(each);
      }
    }

    for (int i = 0; i < toShow.size(); i++) {
      DropTargetHighlighter each = toShow.get(i);
      each.show(aPane, aRectangle, aEvent);
    }
    ourCurrentHighlighters.addAll(toShow);
  }

  static void hideAllBut(int aType) {
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      final DropTargetHighlighter each = ourCurrentHighlighters.get(i);
      if ((each.getMask() & aType) == 0) {
        each.vanish();
        ourCurrentHighlighters.remove(each);
      }
    }
  }

  static void hide() {
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      (ourCurrentHighlighters.get(i)).vanish();
    }
    ourCurrentHighlighters.clear();
  }

  static void hide(int aType) {
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      final DropTargetHighlighter each = ourCurrentHighlighters.get(i);
      if ((each.getMask() & aType) != 0) {
        each.vanish();
        ourCurrentHighlighters.remove(each);
      }
    }
  }

  static boolean isVisibleExcept(int type) {
    int resultType = type;
    for (int i = 0; i < ourCurrentHighlighters.size(); i++) {
      final DropTargetHighlighter each = ourCurrentHighlighters.get(i);
      resultType = resultType | each.getMask();
    }

    return type != resultType;
  }

  static boolean isVisible() {
    return ourCurrentHighlighters.size() > 0;
  }

  private static abstract class AbstractComponentHighlighter extends JPanel implements DropTargetHighlighter {

    protected AbstractComponentHighlighter() {
      setOpaque(false);
      setLayout(new BorderLayout());
    }

    @Override
    public final void show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      if (getParent() != aPane) {
        vanish();
        aPane.add(this, getLayer());
      }
      _show(aPane, aRectangle, aEvent);
    }

    protected Integer getLayer() {
      return JLayeredPane.MODAL_LAYER;
    }

    @Override
    public void vanish() {
      final Container parent = getParent();
      Rectangle bounds = getBounds();
      if (parent != null) {
        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    protected abstract void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent);
  }

  public abstract static class BaseTextHighlighter implements DropTargetHighlighter {

    private Balloon myCurrentBalloon;
    private final MessageType myMessageType;

    public BaseTextHighlighter(MessageType type) {
      myMessageType = type;
    }

    @Override
    public void show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      if (!Registry.is("ide.dnd.textHints")) return;

      final String result = aEvent.getExpectedDropResult();
      if (result != null && result.length() > 0) {
        RelativePoint point  = null;
        for (DropTargetHighlighter each : ourHightlighters) {
          if (each instanceof AbstractComponentHighlighter) {
            Rectangle rec = ((AbstractComponentHighlighter)each).getBounds();
            point = new RelativePoint(aPane, new Point(rec.x + rec.width, rec.y + rec.height / 2));
            break;
          }
        }

        if (point == null) {
          point = new RelativePoint(aPane, new Point(aRectangle.x + aRectangle.width, aRectangle.y + aRectangle.height / 2));
        }

        myCurrentBalloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(result, myMessageType, null).createBalloon();
        myCurrentBalloon.show(point, Balloon.Position.atRight);
      }
    }

    @Override
    public void vanish() {
      if (myCurrentBalloon != null) {
        myCurrentBalloon.hide();
        myCurrentBalloon = null;
      }
    }

    protected Integer getLayer() {
      return JLayeredPane.POPUP_LAYER;
    }

  }

  public static class TextHighlighter extends BaseTextHighlighter {

    public TextHighlighter() {
      super(MessageType.INFO);
    }

    @Override
    public int getMask() {
      return TEXT;
    }
  }

  private static class ErrorTextHighlighter extends BaseTextHighlighter {
    public ErrorTextHighlighter() {
      super(MessageType.ERROR);
    }

    @Override
    public int getMask() {
      return ERROR_TEXT;
    }
  }

  private static class FilledRectangleHighlighter extends AbstractComponentHighlighter {
    public FilledRectangleHighlighter() {
      super();
      setOpaque(true);
      setBorder(BorderFactory.createLineBorder(JBColor.RED));
      setBackground(JBColor.RED);
    }

    @Override
    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      setBounds(aRectangle);
    }

    @Override
    public int getMask() {
      return FILLED_RECTANGLE;
    }
  }

  private static class RectangleHighlighter extends AbstractComponentHighlighter {
    public RectangleHighlighter() {
      super();
      setOpaque(false);
      setBorder(BorderFactory.createLineBorder(JBColor.RED));
    }

    @Override
    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      setBounds(aRectangle);
    }

    @Override
    public int getMask() {
      return RECTANGLE;
    }
  }

  private static class HorizontalLinesHighlighter extends AbstractComponentHighlighter {

    @Override
    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      final Rectangle rectangle = new Rectangle(aRectangle.x - AllIcons.Ide.Dnd.Left.getIconWidth(), aRectangle.y - AllIcons.Ide.Dnd.Left
        .getIconHeight(), aRectangle.width + AllIcons.Ide.Dnd.Left.getIconWidth() + AllIcons.Ide.Dnd.Right.getIconWidth(), aRectangle.height + AllIcons.Ide.Dnd.Left
        .getIconHeight());
      setBounds(rectangle);
    }

    @Override
    protected void paintComponent(Graphics g) {
      AllIcons.Ide.Dnd.Left.paintIcon(this, g, 0, (getHeight() / 2));
      AllIcons.Ide.Dnd.Right.paintIcon(this, g, getWidth() - AllIcons.Ide.Dnd.Right.getIconWidth(), (getHeight() / 2));
    }

    @Override
    public int getMask() {
      return H_ARROWS;
    }
  }

  private static class VerticalLinesHighlighter extends AbstractComponentHighlighter {
    private static final Icon TOP = AllIcons.Ide.Dnd.Top;
    private static final Icon BOTTOM = AllIcons.Ide.Dnd.Bottom;

    @Override
    protected void _show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent) {
      final Rectangle rectangle = new Rectangle(aRectangle.x, aRectangle.y - TOP.getIconHeight(), aRectangle.width, aRectangle.height + TOP.getIconHeight() + BOTTOM
        .getIconHeight());
      setBounds(rectangle);
    }

    @Override
    protected void paintComponent(Graphics g) {
      TOP.paintIcon(this, g, (getWidth() - TOP.getIconWidth()) / 2, 0);
      BOTTOM.paintIcon(this, g, (getWidth() - BOTTOM.getIconWidth()) / 2, getHeight() - BOTTOM.getIconHeight());
    }

    @Override
    public int getMask() {
      return V_ARROWS;
    }
  }
}
