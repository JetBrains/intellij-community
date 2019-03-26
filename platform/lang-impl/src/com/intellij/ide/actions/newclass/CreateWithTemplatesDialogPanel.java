// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.newclass;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Trinity;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class CreateWithTemplatesDialogPanel extends JBPanel {

  private final JBTextField myNameField;
  private final JList<Trinity<String, Icon, String>> myTemplatesList;

  private Consumer<? super InputEvent> myApplyAction;

  public CreateWithTemplatesDialogPanel(@NotNull List<Trinity<String, Icon, String>> templates, @Nullable String selectedItem) {
    super(new BorderLayout());

    myNameField = createNameField();
    myTemplatesList = createTemplatesList(templates);

    updateBorder(false);

    ScrollingUtil.installMoveUpAction(myTemplatesList, myNameField);
    ScrollingUtil.installMoveDownAction(myTemplatesList, myNameField);

    selectTemplate(selectedItem);
    add(myNameField, BorderLayout.NORTH);
    add(myTemplatesList, BorderLayout.CENTER);
  }

  public JTextField getNameField() {
    return myNameField;
  }

  @NotNull
  public String getEnteredName() {
    return myNameField.getText().trim();
  }

  @NotNull
  public String getSelectedTemplate() {
    return myTemplatesList.getSelectedValue().first;
  }

  public void setError(boolean error) {
    updateBorder(error);
  }

  public void setApplyAction(@NotNull Consumer<? super InputEvent> applyAction) {
    myApplyAction = applyAction;
  }

  private void updateBorder(boolean error) {
    JBColor borderColor = JBColor.namedColor(
      "TextField.borderColor",
      JBColor.namedColor("Component.borderColor", new JBColor(0xbdbdbd, 0x646464))
    );
    Border border = JBUI.Borders.customLine(borderColor, 1, 0, 1, 0);

    if (error) {
      Color errorColor = JBUI.CurrentTheme.Validator.errorBorderColor();
      Border errorBorder = JBUI.Borders.customLine(errorColor, 1);
      border = JBUI.Borders.merge(border, errorBorder, false);
    }

    myNameField.setBorder(border);
  }

  @NotNull
  private JBTextField createNameField() {
    JBTextField res = new JBTextField();

    Dimension minSize = res.getMinimumSize();
    Dimension prefSize = res.getPreferredSize();
    minSize.height = JBUI.scale(28);
    prefSize.height = JBUI.scale(28);
    res.setMinimumSize(minSize);
    res.setPreferredSize(prefSize);
    res.setColumns(30);

    res.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>) field -> field.getText().isEmpty());
    res.getEmptyText().setText(IdeBundle.message("action.create.new.class.name.field"));
    res.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          if (myApplyAction != null) myApplyAction.consume(e);
        }
      }
    });

    return res;
  }

  @NotNull
  private JBList<Trinity<String, Icon, String>> createTemplatesList(@NotNull List<Trinity<String, Icon, String>> templates) {
    JBList<Trinity<String, Icon, String>> list = new JBList<>(templates);
    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        selectItem(e.getPoint());
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        selectItem(e.getPoint());
        if (myApplyAction != null) myApplyAction.consume(e);
      }

      private void selectItem(Point point) {
        int index = list.locationToIndex(point);
        if (index >= 0) {
          list.setSelectedIndex(index);
        }
      }
    };
    list.addMouseMotionListener(mouseListener);
    list.addMouseListener(mouseListener);
    list.setCellRenderer(LIST_RENDERER);
    return list;
  }

  private void selectTemplate(@Nullable String selectedItem) {
    if (selectedItem == null) {
      myTemplatesList.setSelectedIndex(0);
      return;
    }

    ListModel<Trinity<String, Icon, String>> model = myTemplatesList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      String templateID = model.getElementAt(i).getThird();
      if (selectedItem.equals(templateID)) {
        myTemplatesList.setSelectedIndex(i);
        return;
      }
    }
  }

  private static final ListCellRenderer<Trinity<String, Icon, String>> LIST_RENDERER =
    new ListCellRenderer<Trinity<String, Icon, String>>() {

      private final ListCellRenderer<Trinity<String, Icon, String>> delegateRenderer =
        new ListCellRendererWrapper<Trinity<String, Icon, String>>() {
          @Override
          public void customize(JList list, Trinity<String, Icon, String> value, int index, boolean selected, boolean hasFocus) {
            if (value != null) {
              setText(value.first);
              setIcon(value.second);
            }
          }
        };

      @Override
      public Component getListCellRendererComponent(JList<? extends Trinity<String, Icon, String>> list,
                                                    Trinity<String, Icon, String> value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        JComponent delegate = (JComponent) delegateRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        delegate.setBorder(JBUI.Borders.empty(JBUI.scale(3), JBUI.scale(1)));
        return delegate;
      }
    };

}
