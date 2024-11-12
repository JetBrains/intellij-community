// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.JBColor;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class TargetOptionsComponent extends JPanel {
  private static final String[] KNOWN_TARGETS;

  static {
    List<String> targets = new ArrayList<>();
    targets.add("1.1");
    targets.add("1.2");
    for (LanguageLevel level : LanguageLevel.values()) {
      if (level != LanguageLevel.JDK_X && !level.isPreview()) {
        targets.add(JpsJavaSdkType.complianceOption(level.toJavaVersion()));
      }
    }
    Collections.reverse(targets);
    KNOWN_TARGETS = ArrayUtilRt.toStringArray(targets);
  }

  private final ComboBox<String> myCbProjectTargetLevel;
  private final JBTable myTable;
  private final Project myProject;

  public TargetOptionsComponent(@NotNull Project project) {
    super(new GridBagLayout());
    myProject = project;
    myCbProjectTargetLevel = createTargetOptionsCombo();

    myTable = new JBTable(new ModuleOptionsTableModel());
    myTable.setShowGrid(false);
    myTable.setRowHeight(JBUIScale.scale(22));
    myTable.getEmptyText().setText(JavaCompilerBundle.message("settings.all.modules.will.be.compiled.with.project.bytecode.version"));

    TableColumn moduleColumn = myTable.getColumnModel().getColumn(0);
    moduleColumn.setHeaderValue(JavaCompilerBundle.message("settings.module.column"));
    moduleColumn.setCellRenderer(new ModuleTableCellRenderer());

    TableColumn targetLevelColumn = myTable.getColumnModel().getColumn(1);
    String columnTitle = JavaCompilerBundle.message("settings.target.bytecode.version");
    targetLevelColumn.setHeaderValue(columnTitle);
    targetLevelColumn.setCellEditor(new TargetLevelCellEditor());
    targetLevelColumn.setCellRenderer(new TargetLevelCellRenderer());
    int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(columnTitle) + 10;
    targetLevelColumn.setPreferredWidth(width);
    targetLevelColumn.setMinWidth(width);
    targetLevelColumn.setMaxWidth(width);

    TableSpeedSearch.installOn(myTable);

    GridBag constraints = new GridBag()
      .setDefaultAnchor(GridBagConstraints.WEST)
      .setDefaultWeightX(1.0).setDefaultWeightY(1.0)
      .setDefaultFill(GridBagConstraints.NONE)
      .setDefaultInsets(6, 0, 0, 0);

    JLabel label = new JLabel(JavaCompilerBundle.message("settings.project.bytecode.version"));
    label.setLabelFor(myCbProjectTargetLevel);
    add(label, constraints.nextLine().next().weightx(0.0));
    add(myCbProjectTargetLevel, constraints.next());
    add(new JLabel(JavaCompilerBundle.message("settings.per.module.bytecode.version")), constraints.nextLine().weightx(0.0));
    JPanel tableComp = ToolbarDecorator.createDecorator(myTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(b -> addModules())
      .setRemoveAction(b -> removeSelectedModules())
      .createPanel();
    tableComp.setPreferredSize(new Dimension(myTable.getWidth(), 150));
    add(tableComp, constraints.nextLine().fillCell().coverLine());
  }

  private static ComboBox<String> createTargetOptionsCombo() {
    ComboBox<String> combo = new ComboBox<>(KNOWN_TARGETS);
    combo.insertItemAt(null, 0);
    combo.setRenderer(BuilderKt.textListCellRenderer(JavaCompilerBundle.message("settings.same.as.language.level"), String::toString));
    return combo;
  }

  private void addModules() {
    int i = ((ModuleOptionsTableModel)myTable.getModel()).addModulesToModel(myProject, this);
    if (i != -1) {
      TableUtil.selectRows(myTable, new int[]{i});
      TableUtil.scrollSelectionToVisible(myTable);
    }
  }

  private void removeSelectedModules() {
    if (myTable.getSelectedRows().length > 0) {
      TableUtil.removeSelectedItems(myTable);
    }
  }

  public void setProjectBytecodeTargetLevel(@NlsSafe String level) {
    myCbProjectTargetLevel.setSelectedItem(level);
  }

  @Nullable
  public String getProjectBytecodeTarget() {
    String item = (String)myCbProjectTargetLevel.getSelectedItem();
    if (item == null) return item;
    return item.trim();
  }

  public Map<String, String> getModulesBytecodeTargetMap() {
    return ((ModuleOptionsTableModel)myTable.getModel()).getModuleOptions();
  }

  public void setModuleTargetLevels(Map<String, String> moduleLevels) {
    ((ModuleOptionsTableModel)myTable.getModel()).setModuleOptions(myProject, moduleLevels);
  }

  private static final class TargetLevelCellEditor extends DefaultCellEditor {
    private TargetLevelCellEditor() {
      super(createTargetOptionsCombo());
      setClickCountToStart(0);
    }
  }

  private static class TargetLevelCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel comp) {
        comp.setHorizontalAlignment(SwingConstants.CENTER);
        if ("".equals(value)) {
          comp.setForeground(JBColor.GRAY);
          comp.setText(JavaCompilerBundle.message("settings.same.as.language.level"));
        }
        else {
          comp.setForeground(table.getForeground());
        }
      }
      return component;
    }
  }
}