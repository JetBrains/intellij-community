// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.newItemPopup;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class NewItemPopupPanel<T> extends JBPanel implements Disposable {

  protected final ExtendableTextField myTextField;
  protected final JList<T> myTemplatesList;

  private final MyListModel myTemplatesListModel;
  private JBPopup myErrorPopup;
  private RelativePoint myErrorShowPoint;
  private Consumer<? super InputEvent> myApplyAction;
  private final Box templatesListHolder;

  private final Collection<TemplatesListVisibilityListener> myVisibilityListeners = new ArrayList<>();

  protected NewItemPopupPanel(List<T> templatesList, ListCellRenderer<T> renderer) {
    super(new BorderLayout());
    setBackground(JBUI.CurrentTheme.NewClassDialog.panelBackground());

    myTextField = createNameField();
    myTemplatesListModel = new MyListModel(templatesList);
    myTemplatesList = createTemplatesList(myTemplatesListModel, renderer);
    myErrorShowPoint = new RelativePoint(myTextField, new Point(0, myTextField.getHeight()));

    ScrollingUtil.installMoveUpAction(myTemplatesList, myTextField);
    ScrollingUtil.installMoveDownAction(myTemplatesList, myTextField);

    JBScrollPane scrollPane = new JBScrollPane(myTemplatesList);
    scrollPane.setBorder(JBUI.Borders.empty());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    templatesListHolder = new Box(BoxLayout.Y_AXIS);
    templatesListHolder.setBorder(JBUI.Borders.emptyTop(JBUI.CurrentTheme.NewClassDialog.fieldsSeparatorWidth()));
    templatesListHolder.add(scrollPane);

    add(myTextField, BorderLayout.NORTH);
    add(templatesListHolder, BorderLayout.CENTER);
  }

  public void setApplyAction(@NotNull Consumer<? super InputEvent> applyAction) {
    myApplyAction = applyAction;
  }

  public void setError(String error) {
    myTextField.putClientProperty("JComponent.outline", error != null ? "error" : null);

    if (myErrorPopup != null && !myErrorPopup.isDisposed()) Disposer.dispose(myErrorPopup);
    if (error == null) return;

    ComponentPopupBuilder popupBuilder = ComponentValidator.createPopupBuilder(new ValidationInfo(error, myTextField), errorHint -> {
      Insets insets = myTextField.getInsets();
      Dimension hintSize = errorHint.getPreferredSize();
      Point point = new Point(0, insets.top - JBUIScale.scale(6) - hintSize.height);
      myErrorShowPoint = new RelativePoint(myTextField, point);
    }).setCancelOnWindowDeactivation(false)
      .setCancelOnClickOutside(true)
      .addUserData("SIMPLE_WINDOW");

    myErrorPopup = popupBuilder.createPopup();
    myErrorPopup.show(myErrorShowPoint);
  }

  @Override
  public void dispose() {
    if (myErrorPopup != null && !myErrorPopup.isDisposed()) Disposer.dispose(myErrorPopup);
  }

  public void addTemplatesVisibilityListener(TemplatesListVisibilityListener listener) {
    myVisibilityListeners.add(listener);
  }

  public void removeTemplatesVisibilityListener(TemplatesListVisibilityListener listener) {
    myVisibilityListeners.remove(listener);
  }

  protected void setTemplatesListVisible(boolean visible) {
    if (templatesListHolder.isVisible() != visible) {
      templatesListHolder.setVisible(visible);
      myVisibilityListeners.forEach(l -> l.visibilityChanged(visible));
    }
  }

  protected void updateTemplatesList(List<T> templatesList) {
    myTemplatesListModel.update(templatesList);
  }

  @NotNull
  private ExtendableTextField createNameField() {
    ExtendableTextField res = new ExtendableTextField();

    Dimension minSize = res.getMinimumSize();
    Dimension prefSize = res.getPreferredSize();
    minSize.height = JBUIScale.scale(28);
    prefSize.height = JBUIScale.scale(28);
    res.setMinimumSize(minSize);
    res.setPreferredSize(prefSize);
    res.setColumns(30);

    Border border = JBUI.Borders.customLine(JBUI.CurrentTheme.NewClassDialog.bordersColor(), 1, 0, 1, 0);
    Border errorBorder = new ErrorBorder(res.getBorder());
    res.setBorder(JBUI.Borders.merge(border, errorBorder, false));
    res.setBackground(JBUI.CurrentTheme.NewClassDialog.searchFieldBackground());

    res.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    res.getEmptyText().setText(IdeBundle.message("action.create.new.class.name.field"));
    res.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          if (myApplyAction != null) myApplyAction.consume(e);
        }
      }
    });

    res.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        setError(null);
      }
    });

    return res;
  }

  @NotNull
  private JBList<T> createTemplatesList(@NotNull ListModel<T> model, ListCellRenderer<T> renderer) {
    JBList<T> list = new JBList<>(model);
    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myApplyAction != null && e.getClickCount() > 1) myApplyAction.consume(e);
      }
    };

    list.addMouseListener(mouseListener);
    list.setCellRenderer(renderer);
    list.setFocusable(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    Border border = JBUI.Borders.merge(
      JBUI.Borders.emptyLeft(JBUIScale.scale(5)),
      JBUI.Borders.customLine(JBUI.CurrentTheme.NewClassDialog.bordersColor(), 1, 0, 0, 0),
      true
    );
    list.setBorder(border);
    return list;
  }

  private static class ErrorBorder implements Border {
    private final Border errorDelegateBorder;

    private ErrorBorder(Border delegate) {errorDelegateBorder = delegate;}

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      if (checkError(c)) {
        errorDelegateBorder.paintBorder(c, g, x, y, width, height);
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return checkError(c) ? errorDelegateBorder.getBorderInsets(c) : JBUI.emptyInsets();
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }

    private static boolean checkError(Component c) {
      Object outlineObj = ((JComponent)c).getClientProperty("JComponent.outline");
      if (outlineObj == null) return false;

      DarculaUIUtil.Outline outline = outlineObj instanceof DarculaUIUtil.Outline
                                      ? (DarculaUIUtil.Outline) outlineObj : DarculaUIUtil.Outline.valueOf(outlineObj.toString());
      return outline == DarculaUIUtil.Outline.error || outline == DarculaUIUtil.Outline.warning;
    }
  }

  protected class MyListModel extends AbstractListModel<T> {

    private final List<T> myItems = new ArrayList<>();

    private MyListModel(List<T> items) {
      myItems.addAll(items);
    }

    public void update(List<T> newItems) {
      if (!myItems.isEmpty()) {
        int end = myItems.size() - 1;
        myItems.clear();
        fireIntervalRemoved(this, 0, end);
      }
      if (!newItems.isEmpty()) {
        myItems.addAll(newItems);
        fireIntervalAdded(this, 0, myItems.size() - 1);
      }
    }

    @Override
    public int getSize() {
      return myItems.size();
    }

    @Override
    public T getElementAt(int index) {
      return myItems.get(index);
    }
  }
}
