// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.extractor.ui;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.presentation.CodeStyleSettingPresentation;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeTableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableCellRenderer;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public final class ExtractedSettingsDialog extends DialogWrapper {
  private final CodeStyleSettingsNameProvider myNameProvider;
  private final List<Value> myValues;
  private DefaultMutableTreeNode myRoot;

  public ExtractedSettingsDialog(@Nullable Project project,
                                 @NotNull CodeStyleSettingsNameProvider nameProvider,
                                 @NotNull List<Value> values) {
    super(project, false);
    myNameProvider = nameProvider;
    myValues = values;
    setModal(true);
    init();
    setTitle(LangBundle.message("dialog.title.extracted.code.style.settings"));
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return buildExtractedSettingsTree();
  }

  public boolean valueIsSelectedInTree(@NotNull Value value) {
    if (myRoot == null) return false;
    return valueIsSelectedInTree(myRoot, value);
  }

  private static boolean valueIsSelectedInTree(@NotNull TreeNode startNode, @NotNull Value value) {
    for (Enumeration children = startNode.children(); children.hasMoreElements();) {
      Object child = children.nextElement();
      if (child instanceof SettingsTreeNode settingsChild) {
        if (settingsChild.accepted && value.equals(settingsChild.myValue)) {
          return true;
        }
        if (valueIsSelectedInTree(settingsChild, value)) return true;
      } else if (child instanceof TreeNode) {
        if (valueIsSelectedInTree((TreeNode) child, value)) return true;
      }
    }
    return false;
  }

  public static final class SettingsTreeNode extends DefaultMutableTreeNode {
    private final CodeStyleSettingPresentation myRepresentation;
    private boolean accepted = true;
    private final @Nls String valueString;
    private final boolean isGroupNode;
    private final @Nls String customTitle;
    private final Value myValue;

    public SettingsTreeNode(@Nls String valueString, CodeStyleSettingPresentation representation, boolean isGroupNode, Value value) {
      this(valueString, representation, isGroupNode, null, value);
    }

    public SettingsTreeNode(@Nls String valueString, CodeStyleSettingPresentation representation, boolean isGroupNode,
                            @Nls String customTitle,
                            Value value) {
      this.valueString = valueString;
      this.myRepresentation = representation;
      this.isGroupNode = isGroupNode;
      this.customTitle = customTitle;
      this.myValue = value;
    }

    public SettingsTreeNode(@Nls String title) {
      this(title, null, true, null);
    }

    public boolean isGroupOrTypeNode() {
      return isGroupNode;
    }

    public @NotNull @Nls String getTitle() {
      return customTitle != null ? customTitle : (myRepresentation == null ? valueString : myRepresentation.getUiName());
    }

    public @Nullable @Nls String getValueString() {
      return myRepresentation == null ? null : valueString;
    }
  }

  private static ColumnInfo getTitleColumnInfo() {
    return new ColumnInfo("TITLE") {
      @Override
      public @Nullable Object valueOf(Object o) {
        if (o instanceof SettingsTreeNode) {
          return ((SettingsTreeNode) o).getTitle();
        } else {
          return o.toString();
        }
      }

      @Override
      public Class<?> getColumnClass() {
        return TreeTableModel.class;
      }
    };
  }

  protected static final class ValueRenderer implements TableCellRenderer {
    private final JLabel myLabel = new JLabel();
    private final JCheckBox myCheckBox = new JCheckBox();
    private final JPanel myPanel = new JPanel(new HorizontalLayout(0));
    {
      myPanel.add(myLabel);
      myPanel.add(myCheckBox);
    }

    @Override
    public @NotNull Component getTableCellRendererComponent(JTable table,
                                                            Object value,
                                                            boolean isSelected,
                                                            boolean hasFocus,
                                                            int row,
                                                            int column) {
      if (table instanceof TreeTable) {
        table.setEnabled(true);
        DefaultMutableTreeNode valueNode = (DefaultMutableTreeNode)((TreeTable) table).getTree().getPathForRow(row).getLastPathComponent();
        if (valueNode instanceof SettingsTreeNode settingsNode) {
          myLabel.setText(settingsNode.getValueString());
          myCheckBox.setEnabled(true);
          myCheckBox.setSelected(settingsNode.accepted);
        } else {
          myLabel.setBackground(table.getBackground());
          myCheckBox.setEnabled(false);
        }
      }
      return myPanel;
    }
  }

  protected static final class ValueEditor extends AbstractTableCellEditor {

    private final JLabel myLabel = new JLabel();
    private final JCheckBox myCheckBox = new JCheckBox();
    private final JPanel myPanel = new JPanel(new HorizontalLayout(0));
    {
      myPanel.add(myLabel);
      myPanel.add(myCheckBox);
    }

    private SettingsTreeNode myCurrentNode;
    private TreeTable myCurrentTree;
    final ActionListener itemChoiceListener = new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (myCurrentNode != null) {
          boolean wasChanged = myCurrentNode.accepted != myCheckBox.isSelected();
          myCurrentNode.accepted = myCheckBox.isSelected();
          if (wasChanged) {
            updateAncestorsUi(myCurrentNode.accepted, myCurrentNode);
            updateChildrenUi(myCurrentNode);
          }
          if (myCurrentTree != null) {
            myCurrentTree.repaint();
          }
        }
      }
    };

    private static void updateAncestorsUi(boolean accepted, SettingsTreeNode node) {
      TreeNode parent = node.getParent();
      if (parent instanceof SettingsTreeNode settingsParent) {
        settingsParent.accepted = false;
        if (!accepted) {
          //propagate disabled settings upwards
          updateAncestorsUi(false, settingsParent);
        } else {
          for (Enumeration<? extends TreeNode> children = parent.children(); children.hasMoreElements(); ) {
            TreeNode child = children.nextElement();
            if ((child instanceof SettingsTreeNode settingsTreeNode) && !settingsTreeNode.accepted) return;
          }
          settingsParent.accepted = true;
          updateAncestorsUi(true, settingsParent);
        }
      }
    }

    private static void updateChildrenUi(SettingsTreeNode node) {
      for (Enumeration<TreeNode> children = node.children(); children.hasMoreElements(); ) {
        TreeNode child = children.nextElement();
        if (child instanceof SettingsTreeNode settingsChild) {
          settingsChild.accepted = node.accepted;
          updateChildrenUi(settingsChild);
        }
      }
    }

    public ValueEditor() {
      myCheckBox.addActionListener(itemChoiceListener);
    }

    @Override
    public Object getCellEditorValue() {
      return myCheckBox.isSelected();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) ((TreeTable) table).getTree().getPathForRow(row).getLastPathComponent();
      if (treeNode instanceof SettingsTreeNode) {
        myCurrentTree = (TreeTable) table;
        myCurrentNode = (SettingsTreeNode) treeNode;
        myLabel.setText(myCurrentNode.getValueString());
        myCheckBox.setSelected(myCurrentNode.accepted);
      }
      return myPanel;
    }
  }

  private static final ValueRenderer myValueRenderer = new ValueRenderer();
  private static final ValueEditor myValueEditor = new ValueEditor();

  private static ColumnInfo getValueColumnInfo() {
    return new ColumnInfo("VALUE") {
      @Override
      public @Nullable Object valueOf(Object o) {
        if (o instanceof SettingsTreeNode) {
          return ((SettingsTreeNode) o).getValueString();
        } else {
          return null;
        }
      }

      @Override
      public TableCellRenderer getRenderer(Object o) {
        return myValueRenderer;
      }

      @Override
      public TableCellEditor getEditor(Object o) {
        return myValueEditor;
      }

      @Override
      public boolean isCellEditable(Object o) {
        return o instanceof SettingsTreeNode;
      }
    };
  }

  private JComponent buildExtractedSettingsTree() {
    myRoot = new DefaultMutableTreeNode();
    for (Map.Entry<LanguageCodeStyleSettingsProvider.SettingsType,
      Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>>> typeEntry : myNameProvider.mySettings.entrySet()) {
      DefaultMutableTreeNode settingsNode = null;
      for (Map.Entry<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> groupEntry: typeEntry.getValue().entrySet()) {
        CodeStyleSettingPresentation.SettingsGroup group = groupEntry.getKey();
        List<CodeStyleSettingPresentation> representations = groupEntry.getValue();
        List<CodeStyleSettingPresentation> children = ContainerUtil.emptyList();
        DefaultMutableTreeNode groupNode = null;
        if (group.name == null && !representations.isEmpty()) {
          //there is a setting with name coinciding with group name
          if (representations.size() > 1) {
            children = representations.subList(1, representations.size());
          }
          CodeStyleSettingPresentation headRep = representations.get(0);
          Value myValue = CodeStyleSettingsNameProvider.getValue(headRep, myValues);
          groupNode = new SettingsTreeNode(headRep.getUiName());
          if (myValue != null) {
            groupNode.add(new SettingsTreeNode(headRep.getValueUiName(myValue.value), headRep, true, myValue));
          }
        } else {
          children = representations;
        }
        for (CodeStyleSettingPresentation representation: children) {
          Value myValue = CodeStyleSettingsNameProvider.getValue(representation, myValues);
          if (myValue != null) {
            if (groupNode == null) {
              groupNode = new SettingsTreeNode(group.name);
            }
            groupNode.add(new SettingsTreeNode(representation.getValueUiName(myValue.value), representation, false, myValue));
          }
        }
        if (groupNode != null && !groupNode.isLeaf()) {
          if (settingsNode == null) {
            settingsNode = new SettingsTreeNode(CodeStyleSettingsNameProvider.getSettingsTypeName(typeEntry.getKey()));
          }
          settingsNode.add(groupNode);
        }
      }
      if (settingsNode != null) {
        myRoot.add(settingsNode);
      }
    }

    final TreeTable treeTable = createTreeName();
    TreeTableSpeedSearch.installOn(treeTable).setComparator(new SpeedSearchComparator(false));

    treeTable.setRootVisible(false);

    final JTree tree = treeTable.getTree();
    tree.setCellRenderer(myTitleRenderer);
    tree.setShowsRootHandles(true);
    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    treeTable.setTableHeader(null);

    TreeUtil.expandAll(tree);

    treeTable.getColumnModel().getSelectionModel().setAnchorSelectionIndex(1);
    treeTable.getColumnModel().getSelectionModel().setLeadSelectionIndex(1);

    int maxWidth = tree.getPreferredScrollableViewportSize().width + 10;
    final TableColumn titleColumn = treeTable.getColumnModel().getColumn(0);
    titleColumn.setPreferredWidth(maxWidth);
    titleColumn.setMinWidth(maxWidth);
    titleColumn.setMaxWidth(maxWidth);
    titleColumn.setResizable(false);

    final Dimension valueSize = new JLabel(ApplicationBundle.message("option.table.sizing.text")).getPreferredSize();
    treeTable.setPreferredScrollableViewportSize(JBUI.size(maxWidth + valueSize.width + 10, 20));
    treeTable.setBackground(UIUtil.getPanelBackground());
    treeTable.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

    final Dimension screenSize = treeTable.getToolkit().getScreenSize();
    JBScrollPane scroller = new JBScrollPane(treeTable) {
      @Override
      public Dimension getMinimumSize() {
        return super.getPreferredSize();
      }
    };
    final Dimension preferredSize = new Dimension(Math.min(screenSize.width / 2, treeTable.getPreferredSize().width),
                                                  Math.min(screenSize.height / 2, treeTable.getPreferredSize().height));
    getRootPane().setPreferredSize(preferredSize);
    return scroller;
  }

  private @NotNull TreeTable createTreeName() {
    final ColumnInfo[] COLUMNS = new ColumnInfo[]{getTitleColumnInfo(), getValueColumnInfo()};

    ListTreeTableModel model = new ListTreeTableModel(myRoot, COLUMNS);
    return new TreeTable(model) {
      @Override
      public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
        TreeTableCellRenderer tableRenderer = super.createTableRenderer(treeTableModel);
        tableRenderer.setRootVisible(false);
        tableRenderer.setShowsRootHandles(true);
        return tableRenderer;
      }

      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        TreePath treePath = getTree().getPathForRow(row);
        if (treePath == null) return super.getCellRenderer(row, column);

        Object node = treePath.getLastPathComponent();

        TableCellRenderer renderer = COLUMNS[column].getRenderer(node);
        return renderer == null ? super.getCellRenderer(row, column) : renderer;
      }

      @Override
      public TableCellEditor getCellEditor(int row, int column) {
        TreePath treePath = getTree().getPathForRow(row);
        if (treePath == null) return super.getCellEditor(row, column);

        Object node = treePath.getLastPathComponent();
        TableCellEditor editor = COLUMNS[column].getEditor(node);
        return editor == null ? super.getCellEditor(row, column) : editor;
      }
    };
  }

  final TreeCellRenderer myTitleRenderer = new CellRenderer();

  public static final class CellRenderer implements TreeCellRenderer {

    private final JLabel myLabel = new JLabel();

    @Override
    public @NotNull Component getTreeCellRendererComponent(JTree tree,
                                                           Object value,
                                                           boolean selected,
                                                           boolean expanded,
                                                           boolean leaf,
                                                           int row,
                                                           boolean hasFocus) {
      if (value instanceof SettingsTreeNode node) {
        myLabel.setText(node.getTitle());
        myLabel.setFont(node.isGroupOrTypeNode() ? myLabel.getFont().deriveFont(Font.BOLD) : myLabel.getFont().deriveFont(Font.PLAIN));
      } else {
        //noinspection HardCodedStringLiteral
        myLabel.setText(value.toString());
        myLabel.setFont(myLabel.getFont().deriveFont(Font.BOLD));
      }

      Color foreground = selected ? UIUtil.getTableSelectionForeground(true) : UIUtil.getTableForeground();
      myLabel.setForeground(foreground);

      return myLabel;
    }
  }
}
