package com.intellij.openapi.editor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * @author spleaner
 */
public class ContextMenuImpl extends JPanel implements Disposable {
  @NonNls
  public static final String ACTION_GROUP = "EditorContextBarMenu";

  private ActionManager myActionManager;
  private ActionGroup myActionGroup;
  private JComponent myComponent;
  private boolean myShowing = false;
  private int myCurrentOpacity;
  private Timer myShowTimer;
  private Timer myHideTimer;

  public ContextMenuImpl(@NotNull final JScrollPane container, @NotNull final EditorImpl editor) {
    setLayout(new BorderLayout(0, 0));
    myActionManager = ActionManager.getInstance();

    //editor.addEditorMouseListener(new EditorMouseAdapter() {
    //  @Override
    //  public void mouseExited(final EditorMouseEvent e) {
    //    toggleContextToolbar(false);
    //  }
    //});

    editor.addEditorMouseMotionListener(new EditorMouseMotionAdapter() {
      @Override
      public void mouseMoved(final EditorMouseEvent e) {
        final Editor editor = e.getEditor();
        final JComponent c = editor.getComponent();

        final Rectangle r = c.getBounds();
        final Point viewPosition = container.getViewport().getViewPosition();

        final Rectangle activationArea = new Rectangle(0, 0, r.width, 150);
        final MouseEvent event = e.getMouseEvent();
        final Point p = event.getPoint();
        toggleContextToolbar(activationArea.contains(p.x, p.y - viewPosition.y));
      }
    });

    AnAction action = myActionManager.getAction("EditorContextBarMenu");
    if (action == null) {
      action = new DefaultActionGroup();
      myActionManager.registerAction(ACTION_GROUP, action);
    }

    if (action instanceof ActionGroup) {
      myActionGroup = (ActionGroup)action;
    }

    myComponent = createComponent();
    add(myComponent);

    setVisible(false);
  }

  private void toggleContextToolbar(final boolean show) {
    if (show) {
      show();
    }
    else {
      hide();
    }
  }

  public void dispose() {
  }

  @Override
  public void show() {
    final Component toolbar = myComponent.getComponent(0);
    final int count = ((Container)toolbar).getComponentCount();
    if (count == 0) {
      return;
    }

    if (!myShowing) {
      myCurrentOpacity = 0;

      if (myHideTimer != null && myHideTimer.isRunning()) myHideTimer.stop();
      super.show();
      setOpaque(false);

      if (myShowTimer == null || !myShowTimer.isRunning()) {
        myShowing = true;

        myShowTimer = new Timer(50, new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            myCurrentOpacity += 20;
            if (myCurrentOpacity > 100) {
              myCurrentOpacity = 100;
              myShowTimer.stop();
            }

            repaint();
          }
        });

        myShowTimer.setRepeats(true);
        myShowTimer.start();
      }
    }
    else {
      super.show();
    }
  }

  @Override
  public void hide() {
    if (myShowing) {
      if (myShowTimer != null && myShowTimer.isRunning()) myShowTimer.stop();

      if (myHideTimer == null || !myHideTimer.isRunning()) {
        myShowing = false;

        myHideTimer = new Timer(700, new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            myHideTimer.stop();

            myHideTimer = new Timer(50, new ActionListener() {
              public void actionPerformed(final ActionEvent e) {
                myCurrentOpacity -= 20;
                if (myCurrentOpacity < 0) {
                  myCurrentOpacity = 0;
                  myHideTimer.stop();
                  //ContextMenuImpl.super.hide();
                }

                repaint();
              }
            });

            myHideTimer.setRepeats(true);
            myHideTimer.start();
          }
        });

        myHideTimer.setRepeats(false);
        myHideTimer.start();
      }
    }
    else {
      //super.hide();
    }
  }

  private static ActionToolbar createToolbar(final ActionGroup group) {
    return new ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, group, true, DataManager.getInstance(), ActionManagerEx.getInstanceEx(),
                                 KeymapManagerEx.getInstanceEx()) {

      @Override
      public void paint(final Graphics g) {
        paintChildren(g);
      }

      @Override
      public ActionButton createToolbarButton(final AnAction action,
                                              final ActionButtonLook look,
                                              final String place,
                                              final Presentation presentation,
                                              final Dimension minimumSize) {
        final ActionButton result = new ActionButton(action, presentation, place, minimumSize) {
          @Override
          protected DataContext getDataContext() {
            return getToolbarDataContext();
          }

          @Override
          public void paintComponent(final Graphics g) {
            final ActionButtonLook look = getButtonLook();
            look.paintIcon(g, this, getIcon());
          }

          @Override
          public void paint(final Graphics g) {
            final Graphics2D g2 = (Graphics2D)g;
            paintComponent(g2);
          }
        };

        result.setLook(look);
        return result;
      }
    };
  }

  private JComponent createComponent() {
    final ActionToolbar toolbar = createToolbar(myActionGroup);
    toolbar.setMinimumButtonSize(new Dimension(20, 20));
    toolbar.setReservePlaceAutoPopupIcon(false);

    final JPanel inner = new ContextMenuPanel(this);
    inner.setLayout(new BorderLayout(0, 0));
    inner.add(toolbar.getComponent());

    return inner;
  }

  private static class ContextMenuPanel extends JPanel {
    private ContextMenuImpl myContextMenu;
    private BufferedImage myBufferedImage;

    private ContextMenuPanel(final ContextMenuImpl contextMenu) {
      myContextMenu = contextMenu;
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    @Override
    public void invalidate() {
      super.invalidate();

      myBufferedImage = null;
    }

    @Override
    public void revalidate() {
      super.revalidate();

      myBufferedImage = null;
    }

    @Override
    public void paint(final Graphics g) {
      final Rectangle r = getBounds();
      if (myBufferedImage == null) {
        myBufferedImage = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);

        final Graphics graphics = myBufferedImage.getGraphics();
        final Graphics2D g2d2 = (Graphics2D)graphics;
        final Composite old = g2d2.getComposite();

        g2d2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

        g2d2.setColor(Color.GRAY);
        g2d2.fillRoundRect(0, 0, r.width - 1, r.height - 1, 6, 6);

        g2d2.setComposite(old);

        paintChildren(g2d2);
      }

      final Graphics2D g2 = (Graphics2D)g;
      final Composite old = g2.getComposite();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, myContextMenu.myCurrentOpacity / 100.0f));
      g2.drawImage(myBufferedImage, 0, 0, myBufferedImage.getWidth(null), myBufferedImage.getHeight(null), null);

      g2.setComposite(old);
    }
  }
}