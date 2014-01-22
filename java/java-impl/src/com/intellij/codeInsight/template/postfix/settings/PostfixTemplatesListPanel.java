package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PostfixTemplatesListPanel {
  private static final NotNullFunction<PostfixTemplate, String> GET_SHORTCUT_FUNCTION =
    new NotNullFunction<PostfixTemplate, String>() {
      @NotNull
      @Override
      public String fun(@NotNull PostfixTemplate template) {
        return template.getKey();
      }
    };

  private static final NotNullFunction<PostfixTemplate, String> GET_DESCRIPTION_FUNCTION =
    new NotNullFunction<PostfixTemplate, String>() {
      @NotNull
      @Override
      public String fun(@NotNull PostfixTemplate template) {
        return template.getDescription();
      }
    };

  private static final NotNullFunction<PostfixTemplate, String> GET_EXAMPLE_FUNCTION =
    new NotNullFunction<PostfixTemplate, String>() {
      @NotNull
      @Override
      public String fun(@NotNull PostfixTemplate template) {
        return template.getExample();
      }
    };

  @NotNull private final Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();
  @NotNull private final JPanel myPanelWithTableView;
  private final TableView<PostfixTemplate> myTemplatesTableView;

  public PostfixTemplatesListPanel(@NotNull List<PostfixTemplate> templates) {
    ColumnInfo[] columns = generateColumns(templates);
    ListTableModel<PostfixTemplate> templatesTableModel = new ListTableModel<PostfixTemplate>(columns, templates, 0);
    myTemplatesTableView = new TableView<PostfixTemplate>();
    myTemplatesTableView.setModelAndUpdateColumns(templatesTableModel);
    myTemplatesTableView.setShowGrid(false);
    myTemplatesTableView.setStriped(true);
    myTemplatesTableView.setBorder(null);

    new TableViewSpeedSearch<PostfixTemplate>(myTemplatesTableView) {
      @Override
      protected String getItemText(@NotNull PostfixTemplate template) {
        return template.getPresentableName();
      }
    };

    myPanelWithTableView = ToolbarDecorator.createDecorator(myTemplatesTableView)
      .setAsUsualTopToolbar()
      .disableAddAction()
      .disableRemoveAction()
      .disableUpDownActions().createPanel();
  }

  @NotNull
  private ColumnInfo[] generateColumns(@NotNull List<PostfixTemplate> templates) {
    String longestTemplateName = "";
    String longestDescription = "";
    String longestExample = "";
    for (PostfixTemplate template : templates) {
      longestTemplateName = longestString(longestTemplateName, GET_SHORTCUT_FUNCTION.fun(template));
      longestDescription = longestString(longestDescription, GET_DESCRIPTION_FUNCTION.fun(template));
      longestExample = longestString(longestExample, GET_EXAMPLE_FUNCTION.fun(template));
    }
    return new ColumnInfo[]{
      new BooleanColumnInfo(),
      new StringColumnInfo("Shortcut", GET_SHORTCUT_FUNCTION, longestTemplateName),
      new StringColumnInfo("Description", GET_DESCRIPTION_FUNCTION, longestDescription),
      new StringColumnInfo("Example", GET_EXAMPLE_FUNCTION, longestExample),
    };
  }

  public void selectTemplate(@NotNull PostfixTemplate template) {
    myTemplatesTableView.setSelection(Arrays.asList(template));
  }

  @NotNull
  private static String longestString(@NotNull String firstString, @NotNull String secondString) {
    return secondString.length() > firstString.length() ? secondString : firstString;
  }

  @NotNull
  public JPanel getComponent() {
    return myPanelWithTableView;
  }

  public void setState(@NotNull Map<String, Boolean> templatesState) {
    myTemplatesState.clear();
    for (Map.Entry<String, Boolean> entry : templatesState.entrySet()) {
      myTemplatesState.put(entry.getKey(), entry.getValue());
    }
  }

  @NotNull
  public Map<String, Boolean> getState() {
    return myTemplatesState;
  }

  public void setEnabled(boolean enabled) {
    myTemplatesTableView.setEnabled(enabled);
  }

  private class BooleanColumnInfo extends ColumnInfo<PostfixTemplate, Boolean> {
    private final BooleanTableCellRenderer CELL_RENDERER = new BooleanTableCellRenderer();
    private final BooleanTableCellEditor CELL_EDITOR = new BooleanTableCellEditor();
    private final int WIDTH = new JBCheckBox().getPreferredSize().width + 4;

    public BooleanColumnInfo() {
      super("");
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(PostfixTemplate template) {
      return CELL_RENDERER;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(PostfixTemplate template) {
      return CELL_EDITOR;
    }

    @Override
    public int getWidth(JTable table) {
      return WIDTH;
    }

    @NotNull
    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public boolean isCellEditable(PostfixTemplate template) {
      return myTemplatesTableView.isEnabled();
    }

    @Nullable
    @Override
    public Boolean valueOf(@NotNull PostfixTemplate template) {
      return ContainerUtil.getOrElse(myTemplatesState, template.getKey(), true);
    }

    @Override
    public void setValue(@NotNull PostfixTemplate template, Boolean value) {
      myTemplatesState.put(template.getKey(), value);
    }
  }

  private static class StringColumnInfo extends ColumnInfo<PostfixTemplate, String> {
    @NotNull private final Function<PostfixTemplate, String> myValueOfFunction;
    @Nullable private final String myPreferredStringValue;

    public StringColumnInfo(@NotNull String name,
                            @NotNull Function<PostfixTemplate, String> valueOfFunction,
                            @Nullable String preferredStringValue) {
      super(name);
      myValueOfFunction = valueOfFunction;

      boolean hasValue = preferredStringValue != null && !preferredStringValue.isEmpty();
      myPreferredStringValue = hasValue ? preferredStringValue : null;
    }

    @Override
    public int getAdditionalWidth() {
      return UIUtil.DEFAULT_HGAP;
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return myPreferredStringValue;
    }

    public String valueOf(final PostfixTemplate template) {
      return myValueOfFunction.fun(template);
    }
  }
}
