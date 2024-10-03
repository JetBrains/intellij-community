// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

final class JBTabsDemoAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout(0, 0));
    final int[] count = new int[1];
    final JBTabsImpl tabs = new JBTabsImpl(null, ApplicationManager.getApplication());

    //final JPanel flow = new JPanel(new FlowLayout(FlowLayout.CENTER));
    //frame.getContentPane().add(flow);
    //flow.add(tabs.getComponent());

    frame.getContentPane().add(tabs.getComponent(), BorderLayout.CENTER);

    JPanel south = new JPanel(new GridLayout(2, 6, 5, 5));
    south.setOpaque(true);
    south.setBackground(Color.white);
    south.setBorder(JBUI.Borders.empty(5));

    final JComboBox pos = new JComboBox<>(JBTabsPosition.values());
    pos.setSelectedIndex(0);
    south.add(pos);
    pos.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final JBTabsPosition p = (JBTabsPosition)pos.getSelectedItem();
        if (p != null) {
          tabs.getPresentation().setTabsPosition(p);
        }
      }
    });
    final JTree component = new JTree();
    final TabInfo toAnimate1 = new TabInfo(component);
    toAnimate1.setIcon(AllIcons.Debugger.Console);

    final JCheckBox bb = new JCheckBox("Icon", true);
    bb.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        if (bb.isSelected()) {
          toAnimate1.setIcon(AllIcons.Debugger.Console);
        } else {
          toAnimate1.setIcon(null);
        }
      }
    });
    south.add(bb);

    final JCheckBox f = new JCheckBox("Focused");
    f.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        tabs.setFocused(f.isSelected());
      }
    });
    south.add(f);


    final JCheckBox v = new JCheckBox("Vertical");
    v.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSideComponentVertical(v.isSelected());
      }
    });
    south.add(v);

    final JCheckBox before = new JCheckBox("Before", true);
    before.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSideComponentBefore(before.isSelected());
      }
    });
    south.add(before);

    final JCheckBox row = new JCheckBox("Single row", true);
    row.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        tabs.setSingleRow(row.isSelected());
      }
    });
    south.add(row);

    final JCheckBox hide = new JCheckBox("Hide tabs", tabs.isHideTabs());
    hide.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        tabs.setHideTabs(hide.isSelected());
      }
    });
    south.add(hide);

    frame.getContentPane().add(south, BorderLayout.SOUTH);

    tabs.addListener(new TabsListener() {
      @Override
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        System.out.println("TabsWithActions.selectionChanged old=" + oldSelection + " new=" + newSelection);
      }
    });

    final JTree someTree = new Tree() {
      @Override
      public void addNotify() {
        super.addNotify();
        System.out.println("JBTabs.addNotify");
      }

      @Override
      public void removeNotify() {
        System.out.println("JBTabs.removeNotify");
        super.removeNotify();
      }
    };
    //someTree.setBorder(new LineBorder(Color.cyan));
    tabs.addTab(new TabInfo(someTree)).setText("Tree1").setActions(new DefaultActionGroup(), null)
        .setIcon(AllIcons.Debugger.Frame);

    final JCheckBox attract1 = new JCheckBox("Attract 1");
    attract1.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        //toAnimate1.setText("Should be animated");

        if (attract1.isSelected()) {
          toAnimate1.fireAlert();
        }
        else {
          toAnimate1.stopAlerting();
        }
      }
    });
    south.add(attract1);

    final JCheckBox hide1 = new JCheckBox("Hide 1", toAnimate1.isHidden());
    hide1.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        toAnimate1.setHidden(!toAnimate1.isHidden());
      }
    });
    south.add(hide1);


    final JCheckBox block = new JCheckBox("Block", false);
    block.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        tabs.setPaintBlocked(!block.isSelected(), true);
      }
    });
    south.add(block);

    final JButton refire = new JButton("Re-fire attraction");
    refire.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        toAnimate1.fireAlert();
      }
    });

    south.add(refire);

    for (Component c : south.getComponents()) {
      if (c instanceof JComponent box) {
        box.setOpaque(false);
      }
    }



    final JEditorPane text = new JEditorPane();
    text.setEditorKit(new HTMLEditorKit());
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < 40; i ++) {
      buffer.append("1234567890abcdefghijklmnopqrstv1234567890abcdefghijklmnopqrstv1234567890abcdefghijklmnopqrstv<br>");
    }
    text.setText(buffer.toString());

    final JLabel tb = new JLabel("Side comp");
    tb.setBorder(new LineBorder(Color.red));
    tabs.addTab(new TabInfo(ScrollPaneFactory.createScrollPane(text)).setSideComponent(tb)).setText("Text text text");
    tabs.addTab(toAnimate1).append("Tree2", new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.black, Color.red));
    tabs.addTab(new TabInfo(new JTable())).setText("Table 1").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 2").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 3").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 4").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 5").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 6").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 7").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 8").setActions(new DefaultActionGroup(), null);
    tabs.addTab(new TabInfo(new JTable())).setText("Table 9").setActions(new DefaultActionGroup(), null);

    //tabs.getComponent().setBorder(new EmptyBorder(5, 5, 5, 5));

    //tabs.setBorder(new LineBorder(Color.blue, 5));
    tabs.setBorder(new EmptyBorder(30, 30, 30, 30));

    tabs.setUiDecorator(new UiDecorator() {
      @Override
      public @NotNull UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(0, -1, 0, -1));
      }
    });

    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}
