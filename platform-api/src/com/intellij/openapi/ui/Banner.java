package com.intellij.openapi.ui;

import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Banner extends NonOpaquePanel implements PropertyChangeListener{

  private int myBannerMinHeight;
  private JLabel myText;

  private NonOpaquePanel myActionsPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));

  private Map<Action, LinkLabel> myActions = new HashMap<Action, LinkLabel>();

  public Banner() {
    setLayout(new BorderLayout());

    myText = new JLabel("", JLabel.LEFT);
    myText.setFont(myText.getFont().deriveFont(Font.BOLD));
    setBorder(new EmptyBorder(2, 6, 2, 4));

    add(myText, BorderLayout.CENTER);
    add(myActionsPanel, BorderLayout.EAST);
  }

  public void addAction(final Action action) {
    action.addPropertyChangeListener(this);
    final LinkLabel label = new LinkLabel(null, null, new LinkListener() {
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        action.actionPerformed(new ActionEvent(Banner.this, ActionEvent.ACTION_PERFORMED, Action.ACTION_COMMAND_KEY));
      }
    });
    myActions.put(action, label);
    myActionsPanel.add(label);
    updateAction(action);
  }

  void updateAction(Action action) {
    final LinkLabel label = myActions.get(action);
    label.setVisible(action.isEnabled());
    label.setText((String)action.getValue(Action.NAME));
    label.setToolTipText((String)action.getValue(Action.SHORT_DESCRIPTION));
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    final Object source = evt.getSource();
    if (source instanceof Action) {
      updateAction(((Action)source));
    }
  }

  public void clearActions() {
    final Set<Action> actions = myActions.keySet();
    for (Iterator<Action> iterator = actions.iterator(); iterator.hasNext();) {
      Action each = iterator.next();
      each.removePropertyChangeListener(this);
    }
    myActions.clear();
    myActionsPanel.removeAll();
  }

  public Dimension getMinimumSize() {
    final Dimension size = super.getMinimumSize();
    size.height = myBannerMinHeight > 0 ? myBannerMinHeight : size.height;
    return size;
  }

  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.height = getMinimumSize().height;
    return size;
  }

  public void setMinHeight(final int height) {
    myBannerMinHeight = height;
    revalidate();
    repaint();
  }

  public void setText(final String text) {
    myText.setText(text);
  }

  public void updateActions() {
    final Set<Action> actions = myActions.keySet();
    for (Iterator<Action> iterator = actions.iterator(); iterator.hasNext();) {
      updateAction(iterator.next());
    }
  }
}