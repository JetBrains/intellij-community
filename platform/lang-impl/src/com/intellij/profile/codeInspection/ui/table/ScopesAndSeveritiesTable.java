// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorSettingsUtil;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.ui.ScopeOrderComparator;
import com.intellij.profile.codeInspection.ui.ScopesChooser;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.profile.codeInspection.ui.table.HighlightingRenderer.EDIT_HIGHLIGHTING;
import static com.intellij.profile.codeInspection.ui.table.SeverityRenderer.EDIT_SEVERITIES;

/**
 * @author Dmitry Batkovich
 */
public final class ScopesAndSeveritiesTable extends JBTable {
  private static final Logger LOG = Logger.getInstance(ScopesAndSeveritiesTable.class);

  public static final HighlightSeverity MIXED_FAKE_SEVERITY = new HighlightSeverity("Mixed", -1);
  @SuppressWarnings("UnusedDeclaration")
  public static final HighlightDisplayLevel MIXED_FAKE_LEVEL = new HighlightDisplayLevel(MIXED_FAKE_SEVERITY, AllIcons.General.InspectionsMixed);
  public static final TextAttributesKey MIXED_FAKE_KEY = TextAttributesKey.createTextAttributesKey("Mixed");
  public static final TextAttributesKey INFORMATION_FAKE_KEY = TextAttributesKey.createTextAttributesKey("");

  private static final int SCOPE_ENABLED_COLUMN = 0;
  private static final int SCOPE_NAME_COLUMN = 1;
  private static final int SEVERITY_COLUMN = 2;
  private static final int HIGHLIGHTING_COLUMN = 3;

  public ScopesAndSeveritiesTable(final TableSettings tableSettings) {
    super(new MyTableModel(tableSettings));

    final TableColumnModel columnModel = getColumnModel();

    final TableColumn scopeEnabledColumn = columnModel.getColumn(SCOPE_ENABLED_COLUMN);
    scopeEnabledColumn.setMaxWidth(30);
    scopeEnabledColumn.setCellRenderer(new ThreeStateCheckBoxRenderer());
    scopeEnabledColumn.setCellEditor(new ThreeStateCheckBoxRenderer());
    
    columnModel.getColumn(SCOPE_NAME_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        component.setForeground(RenderingUtil.getForeground(table, isSelected));
        component.setBackground(RenderingUtil.getBackground(table, isSelected));
        if (value instanceof String) {
          NamedScope namedScope = NamedScopesHolder.getScope(tableSettings.myProject, (String)value);
          if (namedScope != null) {
            setText(namedScope.getPresentableName());
          }
          else {
            if (LangBundle.message("scopes.table.everywhere.else").equals(value)) {
              setText((String) value);
            } else {
              setText(LangBundle.message("scopes.table.missing.scope", value));
              component.setForeground(NamedColorUtil.getErrorForeground());
            }
          }
        }
        return component;
      }
    });

    final TableColumn severityColumn = columnModel.getColumn(SEVERITY_COLUMN);
    SeverityRenderer renderer = new SeverityRenderer(tableSettings.getInspectionProfile(), tableSettings.getProject(), () -> tableSettings.onSettingsChanged(), this);
    severityColumn.setCellRenderer(renderer);
    severityColumn.setCellEditor(renderer);

    final TableColumn highlightingColumn = columnModel.getColumn(HIGHLIGHTING_COLUMN);
    final HighlightingRenderer highlightingRenderer = new HighlightingRenderer(getEditorAttributesKeysAndNames(tableSettings.getInspectionProfile())) {
      @Override
      void openColorSettings() {
        final var dataContext = DataManager.getInstance().getDataContext(this);
        ApplicationManager.getApplication().invokeLater(() -> {
          ColorAndFontOptions.selectOrEditColor(dataContext,
                                                OptionsBundle.message("options.java.attribute.descriptor.error").split("//")[0],
                                                OptionsBundle.message("options.general.display.name"));
        });
      }
    };
    highlightingColumn.setCellRenderer(highlightingRenderer);
    highlightingColumn.setCellEditor(highlightingRenderer);

    setColumnSelectionAllowed(false);
    setRowSelectionAllowed(true);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        final int idx = getSelectionModel().getMinSelectionIndex();
        if (idx >= 0) {
          final ExistedScopesStatesAndNonExistNames scopeToolState = ((MyTableModel)getModel()).getScopeToolState(idx);
          final List<ScopeToolState> existedStates = scopeToolState.getExistedStates();
          if (existedStates.size() == 1 && scopeToolState.getNonExistNames().isEmpty()) {
            tableSettings.onScopeChosen(existedStates.get(0));
          }
        }
      }
    });
    setRowSelectionInterval(0, 0);

    setShowGrid(false);

    ((MyTableModel)getModel()).setTable(this);
  }

  boolean isRowEnabled(int row) {
    return Boolean.TRUE.equals(((MyTableModel)getModel()).isEnabled(row));
  }

  void setSelectedSeverity(HighlightSeverity severity) {
    getModel().setValueAt(severity, getSelectedRow(), SEVERITY_COLUMN);
  }

  public List<ScopeToolState> getSelectedStates() {
    return ((MyTableModel)getModel()).getScopeToolState(getSelectedRow()).getExistedStates();
  }

  public abstract static class TableSettings {
    private final Collection<InspectionConfigTreeNode.Tool> myNodes;
    private final List<String> myKeyNames;
    private final List<HighlightDisplayKey> myKeys;
    private final InspectionProfileImpl myInspectionProfile;
    private final Project myProject;

    protected TableSettings(@NotNull Collection<InspectionConfigTreeNode.Tool> nodes,
                            @NotNull InspectionProfileImpl inspectionProfile,
                            @NotNull Project project) {
      myNodes = nodes;
      myKeys = new ArrayList<>(myNodes.size());
      myKeyNames = new ArrayList<>(myNodes.size());
      for(final InspectionConfigTreeNode.Tool node : nodes) {
        final HighlightDisplayKey key = node.getKey();
        myKeys.add(key);
        myKeyNames.add(key.getShortName());
      }

      myInspectionProfile = inspectionProfile;
      myProject = project;
    }

    public @NotNull List<HighlightDisplayKey> getKeys() {
      return myKeys;
    }

    public @NotNull List<String> getKeyNames() {
      return myKeyNames;
    }

    public @NotNull Collection<InspectionConfigTreeNode.Tool> getNodes() {
      return myNodes;
    }

    public @NotNull InspectionProfileImpl getInspectionProfile() {
      return myInspectionProfile;
    }

    public @NotNull Project getProject() {
      return myProject;
    }

    protected abstract void onScopeAdded();

    protected abstract void onScopesOrderChanged();

    protected abstract void onScopeRemoved(final int scopesCount);

    protected abstract void onScopeChosen(final @NotNull ScopeToolState scopeToolState);

    protected abstract void onSettingsChanged();
  }

  public static @NotNull HighlightSeverity getSeverity(final @NotNull List<? extends ScopeToolState> scopeToolStates) {
    HighlightSeverity previousValue = null;
    for (final ScopeToolState scopeToolState : scopeToolStates) {
      final HighlightSeverity currentValue = scopeToolState.getLevel().getSeverity();
      if (previousValue == null) {
        previousValue = currentValue;
      }
      else if (!previousValue.equals(currentValue)){
        return MIXED_FAKE_SEVERITY;
      }
    }
    return previousValue;
  }

  public static @NotNull TextAttributesKey getEditorAttributesKey(final @NotNull List<? extends ScopeToolState> scopeToolStates, @NotNull Project project) {
    TextAttributesKey previousValue = null;
    final SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(project);
    for (final ScopeToolState scopeToolState : scopeToolStates) {
      TextAttributesKey key = scopeToolState.getEditorAttributesKey();
      if (key == null) {
        final var severity = scopeToolState.getLevel().getSeverity();
        key = severity.equals(HighlightSeverity.INFORMATION) ? INFORMATION_FAKE_KEY
                                                             : registrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey();
      }
      if (previousValue == null) {
        previousValue = key;
      } else if (!previousValue.equals(key)){
        return MIXED_FAKE_KEY;
      }
    }
    return previousValue != null ? previousValue : MIXED_FAKE_KEY;
  }

  private static ArrayList<Pair<TextAttributesKey, @Nls String>> getEditorAttributesKeysAndNames(InspectionProfileImpl profile) {
    final var textAttributes = ColorSettingsUtil.getErrorTextAttributes();

    final Collection<HighlightInfoType> standardSeverities = SeverityRegistrar.standardSeverities();
    final var registrar = profile.getProfileManager().getSeverityRegistrar();
    for (HighlightSeverity severity : registrar.getAllSeverities()) {
      final var highlightInfoType = registrar.getHighlightInfoTypeBySeverity(severity);
      if (standardSeverities.contains(highlightInfoType)) continue;
      final TextAttributesKey attributes = registrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey();
      textAttributes.add(new Pair<>(attributes, severity.getDisplayName()));
    }

    textAttributes.add(new Pair<>(EDIT_HIGHLIGHTING, InspectionsBundle.message("inspection.edit.highlighting.action")));
    return textAttributes;
  }

  private static final class MyTableModel extends AbstractTableModel implements EditableModel {
    private final @NotNull InspectionProfileImpl myInspectionProfile;
    private final List<String> myKeyNames;
    private final @NotNull Project myProject;
    private final TableSettings myTableSettings;
    private final List<HighlightDisplayKey> myKeys;
    private final Comparator<String> myScopeComparator;

    private ScopesAndSeveritiesTable myTable;
    private String[] myScopeNames;

    MyTableModel(@NotNull TableSettings tableSettings) {
      myTableSettings = tableSettings;
      myProject = tableSettings.getProject();
      myInspectionProfile = tableSettings.getInspectionProfile();
      myKeys = tableSettings.getKeys();
      myKeyNames = tableSettings.getKeyNames();
      myScopeComparator = new ScopeOrderComparator(myInspectionProfile);
      refreshAggregatedScopes();
    }

    public void setTable(ScopesAndSeveritiesTable table) {
      myTable = table;
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      if (columnIndex == SCOPE_NAME_COLUMN) {
        return false;
      } else if (columnIndex == SCOPE_ENABLED_COLUMN) {
        return true;
      }
      assert (columnIndex == SEVERITY_COLUMN || columnIndex == HIGHLIGHTING_COLUMN);

      if (Boolean.FALSE.equals(isEnabled(rowIndex))) {
        return false;
      }

      final ExistedScopesStatesAndNonExistNames scopeToolState = getScopeToolState(rowIndex);
      return scopeToolState.getNonExistNames().isEmpty();
    }

    @Override
    public int getRowCount() {
      return lastRowIndex() + 1;
    }

    @Override
    public @Nullable String getColumnName(final int column) {
      return switch (column) {
        case SCOPE_NAME_COLUMN -> LangBundle.message("scopes.chooser.scope.column");
        case SEVERITY_COLUMN -> LangBundle.message("scopes.chooser.scope.severity");
        case HIGHLIGHTING_COLUMN -> LangBundle.message("scopes.chooser.scope.highlighting");
        default -> null;
      };
    }

    @Override
    public int getColumnCount() {
      return 4;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (SCOPE_ENABLED_COLUMN == columnIndex) {
        return Boolean.class;
      }
      if (SCOPE_NAME_COLUMN == columnIndex) {
        return String.class;
      }
      if (SEVERITY_COLUMN == columnIndex) {
        return HighlightSeverity.class;
      }
      if (HIGHLIGHTING_COLUMN == columnIndex) {
        return TextAttributesKey.class;
      }
      throw new IllegalArgumentException();
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
      if (rowIndex < 0) {
        return null;
      }
      return switch (columnIndex) {
        case SCOPE_ENABLED_COLUMN -> isEnabled(rowIndex);
        case SCOPE_NAME_COLUMN -> rowIndex == lastRowIndex() ? LangBundle.message("scopes.table.everywhere.else") : getScopeName(rowIndex);
        case SEVERITY_COLUMN -> getSeverityState(rowIndex);
        case HIGHLIGHTING_COLUMN -> getAttributesKey(rowIndex);
        default -> throw new IllegalArgumentException("Invalid column index " + columnIndex);
      };
    }

    private NamedScope getScope(final int rowIndex) {
      return getScopeToolState(rowIndex).getExistedStates().get(0).getScope(myProject);
    }

    private String getScopeName(final int rowIndex) {
      return getScopeToolState(rowIndex).getExistedStates().get(0).getScopeName();
    }

    private @NotNull HighlightSeverity getSeverityState(final int rowIndex) {
      final ExistedScopesStatesAndNonExistNames existedScopesStatesAndNonExistNames = getScopeToolState(rowIndex);
      if (!existedScopesStatesAndNonExistNames.getNonExistNames().isEmpty()) {
        return MIXED_FAKE_SEVERITY;
      }
      return getSeverity(existedScopesStatesAndNonExistNames.getExistedStates());
    }

    private TextAttributesKey getAttributesKey(final int rowIndex) {
      final ExistedScopesStatesAndNonExistNames existedScopesStatesAndNonExistNames = getScopeToolState(rowIndex);
      if (!existedScopesStatesAndNonExistNames.getNonExistNames().isEmpty()) {
        return MIXED_FAKE_KEY;
      }
      return getEditorAttributesKey(existedScopesStatesAndNonExistNames.myExistedStates, myProject);
    }

    private @Nullable Boolean isEnabled(final int rowIndex) {
      Boolean previousValue = null;
      final ExistedScopesStatesAndNonExistNames existedScopesStatesAndNonExistNames = getScopeToolState(rowIndex);
      for (final ScopeToolState scopeToolState : existedScopesStatesAndNonExistNames.getExistedStates()) {
        final boolean currentValue = scopeToolState.isEnabled();
        if (previousValue == null) {
          previousValue = currentValue;
        } else if (!previousValue.equals(currentValue)){
          return null;
        }
      }
      if (!existedScopesStatesAndNonExistNames.getNonExistNames().isEmpty() && !Boolean.FALSE.equals(previousValue)) {
        return null;
      }
      return previousValue;
    }

    private ExistedScopesStatesAndNonExistNames getScopeToolState(final int rowIndex) {
      final List<String> nonExistNames = new SmartList<>();
      final List<ScopeToolState> existedStates = new SmartList<>();
      for (final String keyName : myKeyNames) {
        final ScopeToolState scopeToolState = getScopeToolState(keyName, rowIndex);
        if (scopeToolState != null) {
          existedStates.add(scopeToolState);
        } else {
          nonExistNames.add(keyName);
        }
      }
      return new ExistedScopesStatesAndNonExistNames(existedStates, nonExistNames);
    }

    private @Nullable ScopeToolState getScopeToolState(final String keyName, final int rowIndex) {
      if (rowIndex == lastRowIndex()) {
        return myInspectionProfile.getToolDefaultState(keyName, myProject);
      }
      else {
        final String scopeName = myScopeNames[rowIndex];
        final List<ScopeToolState> nonDefaultTools = myInspectionProfile.getNonDefaultTools(keyName, myProject);
        for (final ScopeToolState nonDefaultTool : nonDefaultTools) {
          if (Objects.equals(scopeName, nonDefaultTool.getScopeName())) {
            return nonDefaultTool;
          }
        }
      }
      return null;
    }

    private void refreshAggregatedScopes() {
      myScopeNames = myKeyNames.stream()
          .map(keyName -> myInspectionProfile.getNonDefaultTools(keyName, myProject))
          .flatMap(Collection::stream)
          .map(state -> state.getScopeName())
          .distinct()
          .sorted(myScopeComparator)
          .toArray(String[]::new);
    }

    private int lastRowIndex() {
      return myScopeNames.length;
    }

    @Override
    public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
      if (value == null) {
        return;
      }
      if (columnIndex == SEVERITY_COLUMN) {
        if (value == EDIT_SEVERITIES) return;
        final HighlightSeverity severity = (HighlightSeverity)value;
        final HighlightDisplayLevel level = HighlightDisplayLevel.find(severity.getName());
        if (level == null) {
          LOG.error("no display level found for name " + severity.getName());
          return;
        }
        final String scopeName = rowIndex == lastRowIndex() ? null : getScopeName(rowIndex);
        myInspectionProfile.setErrorLevel(myKeys, level, scopeName, myProject);
        myInspectionProfile.setEditorAttributesKey(myKeys, null, scopeName, myProject);
      }
      else if (columnIndex == HIGHLIGHTING_COLUMN) {
        if (value == EDIT_HIGHLIGHTING) return;
        final TextAttributesKey key = (TextAttributesKey)value;
        final String scopeName = rowIndex == lastRowIndex() ? null : getScopeName(rowIndex);
        myInspectionProfile.setEditorAttributesKey(myKeys, key, scopeName, myProject);
      }
      else if (columnIndex == SCOPE_ENABLED_COLUMN) {
        final NamedScope scope = getScope(rowIndex);
        if (scope == null) {
          return;
        }
        if ((Boolean)value) {
          if (rowIndex == lastRowIndex()) {
            myInspectionProfile.enableToolsByDefault(myKeyNames, myProject);
          }
          else {
            //TODO create scopes states if not exist (need scope sorting)
            myInspectionProfile.enableTools(myKeyNames, scope, myProject);
          }
          for (final String keyName : myKeyNames) {
            myInspectionProfile.enableTool(keyName, myProject);
          }
        }
        else {
          if (rowIndex == lastRowIndex()) {
            myInspectionProfile.disableToolByDefault(myKeyNames, myProject);
          }
          else {
            myInspectionProfile.disableTools(myKeyNames, scope, myProject);
          }
        }
        if (myKeyNames.size() == 1) {
          final String keyName = ContainerUtil.getFirstItem(myKeyNames);
          final ScopeToolState state = getScopeToolState(keyName, rowIndex);
          myTableSettings.onScopeChosen(state);
        }
      }
      myTableSettings.onSettingsChanged();
    }

    @Override
    public void removeRow(final int idx) {
      if (idx != lastRowIndex()) {
        myInspectionProfile.removeScopes(myKeyNames, getScopeName(idx), myProject);
        refreshAggregatedScopes();
        myTableSettings.onScopeRemoved(getRowCount());
      }
    }

    @Override
    public void addRow() {
      final List<Descriptor> descriptors = ContainerUtil.map(myTableSettings.getNodes(), inspectionConfigTreeNode -> inspectionConfigTreeNode.getDefaultDescriptor());
      final ScopesChooser scopesChooser = new ScopesChooser(descriptors, myInspectionProfile, myProject, myScopeNames) {
        @Override
        protected void onScopeAdded(@NotNull String scopeName) {
          myTableSettings.onScopeAdded();
          refreshAggregatedScopes();
          for (int i = 0; i < getRowCount(); i++) {
            if (getScopeName(i).equals(scopeName)) {
              fireTableRowsInserted(i, i);
              myTable.clearSelection();
              myTable.setRowSelectionInterval(i, i);
            }
          }
        }

        @Override
        protected void onScopesOrderChanged() {
          myTableSettings.onScopesOrderChanged();
        }
      };
      DataContext dataContext = DataManager.getInstance().getDataContext(myTable);
      final ListPopup popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(LangBundle.message("scopes.chooser.popup.title.select.scope.to.change.its.settings"),
                                scopesChooser.createPopupActionGroup(myTable, dataContext), dataContext,
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
      final RelativePoint point = new RelativePoint(myTable, new Point(0, 0));
      popup.show(point);
    }

    @Override
    public void exchangeRows(final int oldIndex, final int newIndex) {
    }

    @Override
    public boolean canExchangeRows(final int oldIndex, final int newIndex) {
      return false;
    }
  }

  private static final class ExistedScopesStatesAndNonExistNames {

    private final List<ScopeToolState> myExistedStates;
    private final List<String> myNonExistNames;

    ExistedScopesStatesAndNonExistNames(final List<ScopeToolState> existedStates, final List<String> nonExistNames) {
      myExistedStates = existedStates;
      myNonExistNames = nonExistNames;
    }

    public List<ScopeToolState> getExistedStates() {
      return myExistedStates;
    }

    public List<String> getNonExistNames() {
      return myNonExistNames;
    }
  }
}