package com.intellij.database.view.editors;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.JBTableRowEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

public class DataGridEditorUtil {
  public static void setSmallFontRecursive(@Nullable JComponent component) {
    for (Component c : UIUtil.uiTraverser(component)) {
      if (c instanceof JLabel || c instanceof AbstractButton) {
        // com.intellij.util.ui.table.JBTableRowEditor#createLabeledPanel()
        UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, c);
      }
    }
  }

  public static JComponent labeledDecorator(@NotNull JComponent label, @NotNull JBTable table) {
    return labeledDecorator(label, ToolbarDecorator.createDecorator(table));
  }

  public static @NotNull JComponent labeledDecorator(@NotNull JComponent label, ToolbarDecorator decorator) {
    decorator.setToolbarPosition(ActionToolbarPosition.TOP);
    JPanel p = decorator.createPanel();
    decorator.getActionsPanel().setToolbarLabel(label, ActionToolbarPosition.LEFT);
    decorator.getActionsPanel().getToolbar().setReservePlaceAutoPopupIcon(false);
    return p;
  }

  public interface RowStringProvider {

    @NotNull
    Iterable<Pair<String, TextAttributesKey>> getRowText();

    boolean isObjectValid();
  }

  public interface EmbeddableEditor extends RowStringProvider {
    boolean canDoAnything();

    @NotNull
    JComponent getComponent();

    @NotNull
    JComponent getPreferredFocusedComponent();

    @NotNull
    JComponent @NotNull [] getFocusableComponents();
  }

  public abstract static class JBTableRowEditorWrapper<T extends EmbeddableEditor> extends JBTableRowEditor {
    protected final T myComponent;

    protected JBTableRowEditorWrapper(T component) {
      myComponent = component;
    }

    @Override
    public void prepareEditor(JTable table, int row) {
      setLayout(new BorderLayout());
      setBorder(JBUI.Borders.empty(4, 8));
      JComponent component = myComponent.getComponent();
      setSmallFontRecursive(component);
      add(component, BorderLayout.CENTER);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComponent.getPreferredFocusedComponent();
    }

    @Override
    public JComponent[] getFocusableComponents() {
      return myComponent.getFocusableComponents();
    }
  }

  public abstract static class EmbeddableEditorAdapter implements EmbeddableEditor {

    @Override
    public boolean canDoAnything() {
      return true;
    }

    @Override
    public boolean isObjectValid() {
      return true;
    }

    @Override
    public @NotNull Iterable<Pair<String, TextAttributesKey>> getRowText() {
      return Collections.emptyList();
    }
  }
}
