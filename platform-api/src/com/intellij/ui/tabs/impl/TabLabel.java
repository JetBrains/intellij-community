package com.intellij.ui.tabs.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pass;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.util.ui.Centerizer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TabLabel extends JPanel {
  private SimpleColoredComponent myLabel = new SimpleColoredComponent();
  private LayeredIcon myIcon;
  private Icon myOverlayedIcon;

  private TabInfo myInfo;
  private ActionPanel myActionPanel;
  private boolean myCentered;

  private Wrapper myLabelPlaceholder = new Wrapper();
  private JBTabsImpl myTabs;

  private JPanel myContent = new NonOpaquePanel(new BorderLayout());

  public TabLabel(JBTabsImpl tabs, final TabInfo info) {
    myTabs = tabs;
    myInfo = info;
    myLabel.setOpaque(false);
    myLabel.setBorder(null);
    myLabel.setIconTextGap(new JLabel().getIconTextGap());
    myLabel.setIconOpaque(false);
    myLabel.setIpad(new Insets(0, 0, 0, 0));
    setOpaque(false);
    setLayout(new GridBagLayout());
    add(myContent);


    myLabelPlaceholder.setOpaque(false);
    myContent.add(myLabelPlaceholder, BorderLayout.CENTER);

    setAligmentToCenter(true);

    myIcon = new LayeredIcon(2);
    myLabel.setIcon(myIcon);

    addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (myTabs.isSelectionClick(e, false)) {
          myTabs.select(info, true);
        }
        else {
          handlePopup(e);
        }
      }

      public void mouseClicked(final MouseEvent e) {
        handlePopup(e);
      }

      public void mouseReleased(final MouseEvent e) {
        handlePopup(e);
      }
    });
  }

  public void setAligmentToCenter(boolean toCenter) {
    if (myCentered == toCenter && myLabel.getParent() != null) return;

    myLabelPlaceholder.removeAll();

    if (toCenter) {
      final Centerizer center = new Centerizer(myLabel);
      myLabelPlaceholder.setContent(center);
    } else {
      myLabelPlaceholder.setContent(myLabel);
    }

    myCentered = toCenter;
  }

  public void paint(final Graphics g) {
    if (myTabs.getSelectedInfo() != myInfo) {
      g.translate(0, 2);
    }

    super.paint(g);

    if (myTabs.getSelectedInfo() != myInfo) {
      g.translate(0, -2);
    }
  }

  private void handlePopup(final MouseEvent e) {
    if (e.getClickCount() != 1 || !e.isPopupTrigger()) return;

    if (e.getX() < 0 || e.getX() >= e.getComponent().getWidth() || e.getY() < 0 || e.getY() >= e.getComponent().getHeight()) return;

    String place = myTabs.getPopupPlace();
    place = place != null ? place : ActionPlaces.UNKNOWN;
    myTabs.myPopupInfo = myInfo;

    final DefaultActionGroup toShow = new DefaultActionGroup();
    if (myTabs.getPopupGroup() != null) {
      toShow.addAll(myTabs.getPopupGroup());
      toShow.addSeparator();
    }

    Object tabs =
      DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()).getData(JBTabsImpl.NAVIGATION_ACTIONS_KEY.getName());
    if (tabs == myTabs && myTabs.myAddNavigationGroup) {
      toShow.addAll(myTabs.myNavigationActions);
    }

    if (toShow.getChildrenCount() == 0) return;

    myTabs.myActivePopup = myTabs.myActionManager.createActionPopupMenu(place, toShow).getComponent();
    myTabs.myActivePopup.addPopupMenuListener(myTabs.myPopupListener);

    myTabs.myActivePopup.addPopupMenuListener(myTabs);
    myTabs.myActivePopup.show(e.getComponent(), e.getX(), e.getY());
    myTabs.onPopup(myTabs.myPopupInfo);
  }


  public void setText(final SimpleColoredText text) {
    clear(false);
    if (text != null) {
      text.appendToComponent(myLabel);
    }
    invalidateIfNeeded();
  }

  private void clear(final boolean invalidate) {
    myLabel.clear();
    myLabel.setIcon(myIcon);

    if (invalidate) {
      invalidateIfNeeded();
    }
  }

  private void invalidateIfNeeded() {
    if (myLabel.getSize() != null && myLabel.getSize().equals(myLabel.getPreferredSize())) return;
    myLabel.getParent().invalidate();

    myTabs.revalidateAndRepaint(false);
  }

  public void setIcon(final Icon icon) {
    getLayeredIcon().setIcon(icon, 0);
    invalidateIfNeeded();
  }

  private LayeredIcon getLayeredIcon() {
    return myIcon;
  }

  public void setAttraction(boolean enabled) {
    getLayeredIcon().setLayerEnabled(1, enabled);
  }

  public boolean isAttractionEnabled() {
    return getLayeredIcon().isLayerEnabled(1);
  }

  public TabInfo getInfo() {
    return myInfo;
  }

  public void apply(UiDecorator.UiDecoration decoration) {
    if (decoration.getLabelFont() != null) {
      setFont(decoration.getLabelFont());
    }

    Insets insets = decoration.getLabelInsets();
    if (insets != null) {
      Insets current = JBTabsImpl.ourDefaultDecorator.getDecoration().getLabelInsets();
      setBorder(new EmptyBorder(getValue(current.top, insets.top), getValue(current.left, insets.left),
                                getValue(current.bottom, insets.bottom), getValue(current.right, insets.right)));
    }
  }

  private int getValue(int curentValue, int newValue) {
    return newValue != -1 ? newValue : curentValue;
  }

  public void setTabActions(ActionGroup group) {
    removeOldActionPanel();

    if (group == null) return;

    myActionPanel = new ActionPanel(myTabs, myInfo, new Pass<MouseEvent>() {
      public void pass(final MouseEvent event) {
        final MouseEvent me = SwingUtilities.convertMouseEvent(event.getComponent(), event, TabLabel.this);
        processMouseEvent(me);
      }
    });

    toggleShowActions(false);

    NonOpaquePanel wrapper = new NonOpaquePanel(new GridBagLayout());
    wrapper.add(myActionPanel);

    myContent.add(wrapper, BorderLayout.EAST);

    myTabs.revalidateAndRepaint(false);
  }

  @Override
  protected void processMouseEvent(final MouseEvent e) {
    super.processMouseEvent(e);
  }

  private void removeOldActionPanel() {
    if (myActionPanel != null) {
      myActionPanel.getParent().remove(myActionPanel);
      myActionPanel = null;
    }
  }

  public boolean updateTabActions() {
    return myActionPanel != null && myActionPanel.update();

  }

  private void setAttractionIcon(Icon icon) {
    if (myIcon.getIcon(0) == null) {
      getLayeredIcon().setIcon(null, 1);
      myOverlayedIcon = icon;
    } else {
      getLayeredIcon().setIcon(icon, 1);
      myOverlayedIcon = null;
    }
  }

  public boolean repaintAttraction() {
    if (!myTabs.myAttractions.contains(myInfo)) {
      if (getLayeredIcon().isLayerEnabled(1)) {
        getLayeredIcon().setLayerEnabled(1, false);
        setAttractionIcon(null);
        invalidateIfNeeded();
        return true;
      }
      return false;
    }

    boolean needsUpdate = false;

    if (getLayeredIcon().getIcon(1) != myInfo.getAlertIcon()) {
      setAttractionIcon(myInfo.getAlertIcon());
      needsUpdate = true;
    }

    int maxInitialBlinkCount = 5;
    int maxRefireBlinkCount = maxInitialBlinkCount + 2;
    if (myInfo.getBlinkCount() < maxInitialBlinkCount && myInfo.isAlertRequested()) {
      getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
      if (myInfo.getBlinkCount() == 0) {
        needsUpdate = true;
      }
      myInfo.setBlinkCount(myInfo.getBlinkCount() + 1);

      if (myInfo.getBlinkCount() == maxInitialBlinkCount) {
        myInfo.resetAlertRequest();
      }

      repaint();
    }
    else {
      if (myInfo.getBlinkCount() < maxRefireBlinkCount && myInfo.isAlertRequested()) {
        getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
        myInfo.setBlinkCount(myInfo.getBlinkCount() + 1);

        if (myInfo.getBlinkCount() == maxRefireBlinkCount) {
          myInfo.setBlinkCount(maxInitialBlinkCount);
          myInfo.resetAlertRequest();
        }

        repaint();
      }
      else {
        needsUpdate = !getLayeredIcon().isLayerEnabled(1);
        getLayeredIcon().setLayerEnabled(1, true);
      }
    }

    invalidateIfNeeded();

    return needsUpdate;
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);

    if (myOverlayedIcon == null || myLabel.getParent() == null) return;

    final Rectangle textBounds = SwingUtilities.convertRectangle(myLabel.getParent(), myLabel.getBounds(), this);
    if (getLayeredIcon().isLayerEnabled(1)) {

      final int top = (getSize().height - myOverlayedIcon.getIconHeight()) / 2;

      myOverlayedIcon.paintIcon(this, g, textBounds.x - myOverlayedIcon.getIconWidth() / 2, top);
    }

  }

  public void setTabActionsAutoHide(final boolean autoHide) {
    if (myActionPanel.isAutoHide() == autoHide) return;

    myActionPanel.setAutoHide(autoHide);
  }

  public void toggleShowActions(boolean show) {
    if (myActionPanel != null) {
      myActionPanel.toggleShowActtions(show);
    }
  }
}
