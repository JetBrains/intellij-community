package com.intellij.debugger.ui.impl;

import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 11:26:44 PM
 */
public class UIUtil {
  public static void enableEditorOnCheck(final JCheckBox checkbox, final JComponent textfield) {
    checkbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean selected = checkbox.isSelected();
        textfield.setEnabled(selected);
      }
    });
    textfield.setEnabled(checkbox.isSelected());
  }

  public static void focusEditorOnCheck(final JCheckBox checkbox, final JComponent component) {
    final Runnable runnable = new Runnable() {
      public void run() {
        component.requestFocus();
      }
    };
    checkbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (checkbox.isSelected()) {
          SwingUtilities.invokeLater(runnable);
        }
      }
    });
  }

  public static class BorderedPanel extends JPanel {
    private final TitledBorder myTitleBorder;

    public BorderedPanel(String title) {
      super(new BorderLayout());
      myTitleBorder = IdeBorderFactory.createTitledBorder(title);
      setBorder(myTitleBorder);
    }

    public void setEnabled(boolean isEnabled) {
      super.setEnabled(isEnabled);

      if (!isEnabled) {
        Color halftone = com.intellij.util.ui.UIUtil.getTextInactiveTextColor();
        myTitleBorder.setTitleColor(halftone);
      } else {
        Color foregrnd = getForeground();
        myTitleBorder.setTitleColor(foregrnd);
      }
    }
  }

  public static class RadioTabbedPaneManager {
    public static class Tab {
      public String    name;
      public Component contents;
      private JRadioButton button;

      public Tab(String _name, Component _contents) {
        name     = _name;
        contents = _contents;
      }
    }

    ArrayList<Tab> myTabs = new ArrayList<Tab>();
    Tab            myActiveTab;

    public RadioTabbedPaneManager() {
    }

    public void addTab(String name, Component tab) {
      myTabs.add(new Tab(name, tab));
    }

    public Component createComponent() {
      Dimension preferredSize = null;

      JPanel result = new JPanel(new BorderLayout());
      final JPanel tabPlace = new JPanel();;
      ButtonGroup buttonGroup = new ButtonGroup();
      Box buttonBox = Box.createHorizontalBox();
      for (Iterator iterator = myTabs.iterator(); iterator.hasNext();) {
        final Tab tab = (Tab)iterator.next();
        tab.button = new JRadioButton(tab.name);
        tab.button.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            tabPlace.removeAll();
            tabPlace.add(tab.contents);
            tabPlace.validate();
            tabPlace.repaint();
            myActiveTab = tab;
          }
        });
        buttonGroup.add(tab.button);
        buttonBox.add(tab.button);
        if(preferredSize == null) {
          preferredSize = tab.contents.getPreferredSize();
        } else {
          Dimension contentsSize = tab.contents.getPreferredSize();
          if(preferredSize .height < contentsSize.height)
            preferredSize.height = contentsSize.height;
          if(preferredSize.width < contentsSize.width)
            preferredSize.width = contentsSize.width;
        }
      }

      result.add(buttonBox, BorderLayout.NORTH);
      result.add(tabPlace, BorderLayout.WEST);
      tabPlace.setPreferredSize(preferredSize);

      return result;
    }

    public Tab getSelectedTab() {
      return myActiveTab;
    }

    public void setActiveTab(String s) {
      getSelectedTab().button.setSelected(false);
      for (Iterator iterator = myTabs.iterator(); iterator.hasNext();) {
        Tab tab = (Tab)iterator.next();
        if(tab.name.equals(s)) {
          tab.button.setSelected(true);
        }
      }
    }
  }

  public static Dimension max(Dimension d1, Dimension d2) {
    return new Dimension(d1.width > d2.width ? d1.width : d2.width, d1.height > d2.height ? d1.height : d2.height);
  }
}
