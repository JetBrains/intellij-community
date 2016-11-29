/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.propertyTable;

import com.intellij.designer.model.ErrorInfo;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PairFunction;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.TableUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class PropertyTable extends JBTable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.propertyTable.PropertyTable");
  private static final Comparator<String> GROUP_COMPARATOR = (o1, o2) -> StringUtil.compare(o1, o2, true);
  private static final Comparator<Property> PROPERTY_COMPARATOR = (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true);

  private boolean mySorted;
  private boolean myShowGroups;
  private boolean myShowExpertProperties;

  private String[] myColumnNames = new String[]{"Property", "Value"};

  private final TableSpeedSearch mySpeedSearch;

  private final AbstractTableModel myModel = new PropertyTableModel();
  protected List<PropertiesContainer> myContainers = Collections.emptyList();
  protected List<Property> myProperties = Collections.emptyList();
  protected final Set<String> myExpandedProperties = new HashSet<>();

  private boolean mySkipUpdate;
  private boolean myStoppingEditing;

  private final TableCellRenderer myCellRenderer = new PropertyCellRenderer();
  private final PropertyCellEditor myCellEditor = new PropertyCellEditor();

  private final PropertyEditorListener myPropertyEditorListener = new PropertyCellEditorListener();

  public PropertyTable() {
    setModel(myModel);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setShowColumns(false);
    setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

    setShowVerticalLines(false);
    setIntercellSpacing(new Dimension(0, 1));
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));

    setColumnSelectionAllowed(false);
    setCellSelectionEnabled(false);
    setRowSelectionAllowed(true);

    addMouseListener(new MouseTableListener());

    mySpeedSearch = new TableSpeedSearch(this, (object, cell) -> {
      if (cell.column != 0) return null;
      if (object instanceof GroupProperty) return null;
      return ((Property)object).getName();
    }) {
      @Override
      protected void selectElement(Object element, String selectedText) {
        super.selectElement(element, selectedText);
        repaint(PropertyTable.this.getVisibleRect());
      }
    };
    mySpeedSearch.setComparator(new SpeedSearchComparator(false, false));

    // TODO: Updates UI after LAF updated
  }

  public void setColumnNames(String... columnNames) {
    if (columnNames.length != 2) throw new IllegalArgumentException("Invalid number of columns. Expected 2, got " + columnNames.length);
    myColumnNames = columnNames;

    TableColumnModel mmodel = getColumnModel();
    for (int i = 0; i < columnNames.length; i++) {
      mmodel.getColumn(i).setHeaderValue(columnNames[i]);
    }
  }

  public void setSorted(boolean sorted) {
    mySorted = sorted;
    update();
  }

  public boolean isSorted() {
    return mySorted;
  }

  public void setShowGroups(boolean showGroups) {
    myShowGroups = showGroups;
    update();
  }

  public boolean isShowGroups() {
    return myShowGroups;
  }

  public void showExpert(boolean showExpert) {
    myShowExpertProperties = showExpert;
    update();
  }

  public boolean isShowExpertProperties() {
    return myShowExpertProperties;
  }

  public void setUI(TableUI ui) {
    super.setUI(ui);

    // Customize action and input maps
    ActionMap actionMap = getActionMap();

    setFocusTraversalKeys(
      KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
      KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
    setFocusTraversalKeys(
      KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
      KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));

    InputMap focusedInputMap = getInputMap(JComponent.WHEN_FOCUSED);
    InputMap ancestorInputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    actionMap.put("selectPreviousRow", new MySelectNextPreviousRowAction(false));
    actionMap.put("selectNextRow", new MySelectNextPreviousRowAction(true));

    actionMap.put("startEditing", new MyStartEditingAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "startEditing");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));

    actionMap.put("smartEnter", new MyEnterAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "smartEnter");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");

    actionMap.put("restoreDefault", new MyRestoreDefaultAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "restoreDefault");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "restoreDefault");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "restoreDefault");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "restoreDefault");

    actionMap.put("expandCurrent", new MyExpandCurrentAction(true, false));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "expandCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0));

    actionMap.put("expandCurrentRight", new MyExpandCurrentAction(true, true));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "expandCurrentRight");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), "expandCurrentRight");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0));

    actionMap.put("collapseCurrent", new MyExpandCurrentAction(false, false));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "collapseCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0));

    actionMap.put("collapseCurrentLeft", new MyExpandCurrentAction(false, true));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "collapseCurrentLeft");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), "collapseCurrentLeft");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0));
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    return myCellRenderer;
  }

  public void restoreDefaultValue() {
    final Property property = getSelectionProperty();
    if (property != null) {
      if (isEditing()) {
        cellEditor.stopCellEditing();
      }

      doRestoreDefault(() -> {
        for (PropertiesContainer component : myContainers) {
          if (!property.isDefaultRecursively(component)) {
            property.setDefaultValue(component);
          }
        }
      });

      repaint();
    }
  }

  protected abstract boolean doRestoreDefault(ThrowableRunnable<Exception> runnable);

  @Nullable
  public ErrorInfo getErrorInfoForRow(int row) {
    if (myContainers.size() != 1) {
      return null;
    }

    Property property = myProperties.get(row);
    if (property.getParent() != null) {
      return null;
    }

    for (ErrorInfo errorInfo : getErrors(myContainers.get(0))) {
      if (property.getName().equals(errorInfo.getPropertyName())) {
        return errorInfo;
      }
    }
    return null;
  }

  protected abstract List<ErrorInfo> getErrors(@NotNull PropertiesContainer container);

  @Override
  public String getToolTipText(MouseEvent event) {
    int row = rowAtPoint(event.getPoint());
    if (row != -1 && !myProperties.isEmpty()) {
      ErrorInfo errorInfo = getErrorInfoForRow(row);
      if (errorInfo != null) {
        return errorInfo.getName();
      }
      if (columnAtPoint(event.getPoint()) == 0) {
        String tooltip = myProperties.get(row).getTooltip();
        if (tooltip != null) {
          return tooltip;
        }
      }
    }
    return super.getToolTipText(event);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  protected PropertyContext getPropertyContext() {
    return null;
  }

  public void update() {
    update(myContainers, null);
  }

  public void update(@NotNull List<? extends PropertiesContainer> containers, @Nullable Property initialSelection) {
    update(containers, initialSelection, true);
  }

  private void update(@NotNull List<? extends PropertiesContainer> containers, @Nullable Property initialSelection, boolean finishEditing) {
    if (finishEditing) {
      finishEditing();
    }

    if (mySkipUpdate) {
      return;
    }
    mySkipUpdate = true;

    try {
      if (finishEditing && isEditing()) {
        cellEditor.stopCellEditing();
      }

      Property selection = initialSelection != null ? initialSelection : getSelectionProperty();
      myContainers = new ArrayList<>(containers);
      fillProperties();
      myModel.fireTableDataChanged();

      restoreSelection(selection);
    }
    finally {
      mySkipUpdate = false;
    }
  }

  private void sortPropertiesAndCreateGroups(List<Property> rootProperties) {
    if (!mySorted && !myShowGroups) return;

    Collections.sort(rootProperties, (o1, o2) -> {
      if (o1.getParent() != null || o2.getParent() != null) {
        if (o1.getParent() == o2) return -1;
        if (o2.getParent() == o1) return 1;
        return 0;
      }

      if (myShowGroups) {
        int result = getGroupComparator().compare(o1.getGroup(), o2.getGroup());
        if (result != 0) return result;
      }
      return mySorted ? getPropertyComparator().compare(o1, o2) : 0;
    });

    if (myShowGroups) {
      for (int i = 0; i < rootProperties.size() - 1; i++) {
        Property prev = i == 0 ? null : rootProperties.get(i - 1);
        Property each = rootProperties.get(i);

        String eachGroup = each.getGroup();
        String prevGroup = prev == null ? null : prev.getGroup();

        if (prevGroup != null || eachGroup != null) {
          if (!StringUtil.equalsIgnoreCase(eachGroup, prevGroup)) {
            rootProperties.add(i, new GroupProperty(each.getGroup()));
            i++;
          }
        }
      }
    }
  }

  @NotNull
  protected Comparator<String> getGroupComparator() {
    return GROUP_COMPARATOR;
  }

  @NotNull
  protected Comparator<Property> getPropertyComparator() {
    return PROPERTY_COMPARATOR;
  }

  protected List<Property> getProperties(PropertiesContainer component) {
    return component.getProperties();
  }

  private void restoreSelection(Property selection) {
    List<Property> propertyPath = new ArrayList<>(2);
    while (selection != null) {
      propertyPath.add(0, selection);
      selection = selection.getParent();
    }

    int indexToSelect = -1;
    int size = propertyPath.size();
    for (int i = 0; i < size; i++) {
      int index = findFullPathProperty(myProperties, propertyPath.get(i));
      if (index == -1) {
        break;
      }
      if (i == size - 1) {
        indexToSelect = index;
      }
      else {
        expand(index);
      }
    }

    if (indexToSelect != -1) {
      getSelectionModel().setSelectionInterval(indexToSelect, indexToSelect);
    }
    else if (getRowCount() > 0) {
      indexToSelect = 0;
      for (int i = 0; i < myProperties.size(); i++) {
        if (!(myProperties.get(i) instanceof GroupProperty)) {
          indexToSelect = i;
          break;
        }
      }
      getSelectionModel().setSelectionInterval(indexToSelect, indexToSelect);
    }
    TableUtil.scrollSelectionToVisible(this);
  }

  private void fillProperties() {
    myProperties = new ArrayList<>();
    int size = myContainers.size();

    if (size > 0) {
      List<Property> rootProperties = new ArrayList<>();
      for (Property each : (Iterable<? extends Property>)getProperties(myContainers.get(0))) {
        addIfNeeded(getCurrentComponent(), each, rootProperties);
      }
      sortPropertiesAndCreateGroups(rootProperties);

      for (Property property : rootProperties) {
        myProperties.add(property);
        addExpandedChildren(getCurrentComponent(), property, myProperties);
      }

      if (size > 1) {
        for (Iterator<Property> I = myProperties.iterator(); I.hasNext(); ) {
          if (!I.next().availableFor(myContainers)) {
            I.remove();
          }
        }

        for (int i = 1; i < size; i++) {
          List<Property> otherProperties = new ArrayList<>();
          fillProperties(myContainers.get(i), otherProperties);

          for (Iterator<Property> I = myProperties.iterator(); I.hasNext(); ) {
            Property addedProperty = I.next();

            int index = findFullPathProperty(otherProperties, addedProperty);
            if (index == -1) {
              I.remove();
              continue;
            }

            Property testProperty = otherProperties.get(index);
            if (!addedProperty.getClass().equals(testProperty.getClass())) {
              I.remove();
              continue;
            }

            List<Property> addedChildren = getChildren(addedProperty);
            List<Property> testChildren = getChildren(testProperty);
            int addedChildrenSize = addedChildren.size();

            if (addedChildrenSize != testChildren.size()) {
              I.remove();
              continue;
            }

            for (int j = 0; j < addedChildrenSize; j++) {
              if (!addedChildren.get(j).getName().equals(testChildren.get(j).getName())) {
                I.remove();
                break;
              }
            }
          }
        }
      }
    }
  }

  private void fillProperties(PropertiesContainer<?> component, List<Property> properties) {
    for (Property each : getProperties(component)) {
      if (addIfNeeded(component, each, properties)) {
        addExpandedChildren(component, each, properties);
      }
    }
  }

  private void addExpandedChildren(PropertiesContainer<?> component, Property property, List<Property> properties) {
    if (isExpanded(property)) {
      for (Property child : getChildren(property)) {
        if (addIfNeeded(component, child, properties)) {
          addExpandedChildren(component, child, properties);
        }
      }
    }
  }

  private boolean addIfNeeded(PropertiesContainer<?> component, Property property, List<Property> properties) {
    if (property.isExpert() && !myShowExpertProperties) {
      try {
        if (property.isDefaultRecursively(component)) {
          return false;
        }
      }
      catch (Throwable ignore) {
      }
    }
    properties.add(property);
    return true;
  }

  @Nullable
  public static Property findProperty(List<Property> properties, String name) {
    for (Property property : properties) {
      if (name.equals(property.getName())) {
        return property;
      }
    }
    return null;
  }

  public static int findProperty(List<Property> properties, Property property) {
    String name = property.getName();
    int size = properties.size();

    for (int i = 0; i < size; i++) {
      Property nextProperty = properties.get(i);
      if (Comparing.equal(nextProperty.getGroup(), property.getGroup()) && name.equals(nextProperty.getName())) {
        return i;
      }
    }

    return -1;
  }

  private static int findFullPathProperty(List<Property> properties, Property property) {
    Property parent = property.getParent();
    if (parent == null) {
      return findProperty(properties, property);
    }

    String name = getFullPathName(property);
    int size = properties.size();

    for (int i = 0; i < size; i++) {
      if (name.equals(getFullPathName(properties.get(i)))) {
        return i;
      }
    }

    return -1;
  }

  private static String getFullPathName(Property property) {
    StringBuilder builder = new StringBuilder();
    for (; property != null; property = property.getParent()) {
      builder.insert(0, ".").insert(0, property.getName());
    }
    return builder.toString();
  }

  public static void moveProperty(List<Property> source, String name, List<Property> destination, int index) {
    Property property = extractProperty(source, name);
    if (property != null) {
      if (index == -1) {
        destination.add(property);
      }
      else {
        destination.add(index, property);
      }
    }
  }

  @Nullable
  public static Property extractProperty(List<Property> properties, String name) {
    int size = properties.size();
    for (int i = 0; i < size; i++) {
      if (name.equals(properties.get(i).getName())) {
        return properties.remove(i);
      }
    }
    return null;
  }

  @Nullable
  public Property getSelectionProperty() {
    int selectedRow = getSelectedRow();
    if (selectedRow >= 0 && selectedRow < myProperties.size()) {
      return myProperties.get(selectedRow);
    }
    return null;
  }

  @Nullable
  private PropertiesContainer getCurrentComponent() {
    return myContainers.size() == 1 ? myContainers.get(0) : null;
  }

  private List<Property> getChildren(Property property) {
    return property.getChildren(getCurrentComponent());
  }

  private List<Property> getFilterChildren(Property property) {
    List<Property> properties = new ArrayList<>(getChildren(property));
    for (Iterator<Property> I = properties.iterator(); I.hasNext(); ) {
      Property child = I.next();
      if (child.isExpert() && !myShowExpertProperties) {
        I.remove();
      }
    }
    return properties;
  }

  public boolean isDefault(Property property) throws Exception {
    for (PropertiesContainer component : myContainers) {
      if (!property.isDefaultRecursively(component)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  protected final Object getValue(Property property) throws Exception {
    int size = myContainers.size();
    if (size == 0) {
      return null;
    }

    Object value = property.getValue(myContainers.get(0));
    for (int i = 1; i < size; i++) {
      if (!Comparing.equal(value, property.getValue(myContainers.get(i)))) {
        return null;
      }
    }

    return value;
  }

  private boolean isExpanded(Property property) {
    return myExpandedProperties.contains(property.getPath());
  }

  private void collapse(int rowIndex) {
    int selectedRow = getSelectedRow();
    Property property = myProperties.get(rowIndex);

    int size = collapse(property, rowIndex + 1);
    LOG.assertTrue(size > 0);
    myModel.fireTableDataChanged();

    if (selectedRow != -1) {
      if (selectedRow > rowIndex) {
        selectedRow -= size;
      }

      getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
    }
  }

  private int collapse(Property property, int startIndex) {
    int totalSize = 0;
    if (myExpandedProperties.remove(property.getPath())) {
      int size = getFilterChildren(property).size();
      totalSize += size;
      for (int i = 0; i < size; i++) {
        totalSize += collapse(myProperties.remove(startIndex), startIndex);
      }
    }
    return totalSize;
  }

  private void expand(int rowIndex) {
    int selectedRow = getSelectedRow();
    Property property = myProperties.get(rowIndex);
    String path = property.getPath();

    if (myExpandedProperties.contains(path)) {
      return;
    }
    myExpandedProperties.add(path);

    List<Property> properties = getFilterChildren(property);
    myProperties.addAll(rowIndex + 1, properties);

    myModel.fireTableDataChanged();

    if (selectedRow != -1) {
      if (selectedRow > rowIndex) {
        selectedRow += properties.size();
      }

      getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
    }

    Rectangle rectStart = getCellRect(selectedRow, 0, true);
    Rectangle rectEnd = getCellRect(selectedRow + properties.size(), 0, true);
    scrollRectToVisible(
      new Rectangle(rectStart.x, rectStart.y, rectEnd.x + rectEnd.width - rectStart.x, rectEnd.y + rectEnd.height - rectStart.y));
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void setValueAt(Object aValue, int row, int column) {
    Property property = myProperties.get(row);
    super.setValueAt(aValue, row, column);

    if (property.needRefreshPropertyList()) {
      update();
    }

    repaint();
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    PropertyEditor editor = myProperties.get(row).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener); // reorder listener (first)
    editor.addPropertyEditorListener(myPropertyEditorListener);
    myCellEditor.setEditor(editor);
    return myCellEditor;
  }

  /*
  * This method is overriden due to bug in the JTree. The problem is that
  * JTree does not properly repaint edited cell if the editor is opaque or
  * has opaque child components.
  */
  public boolean editCellAt(int row, int column, EventObject e) {
    boolean result = super.editCellAt(row, column, e);
    repaint(getCellRect(row, column, true));
    return result;
  }

  private void startEditing(int index) {
    startEditing(index, false);
  }

  private void startEditing(int index, boolean startedWithKeyboard) {
    final PropertyEditor editor = myProperties.get(index).getEditor();
    if (editor == null) {
      return;
    }

    editCellAt(index, convertColumnIndexToView(1));
    LOG.assertTrue(editorComp != null);

    JComponent preferredComponent = editor.getPreferredFocusedComponent();
    if (preferredComponent == null) {
      preferredComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)editorComp);
    }
    if (preferredComponent != null) {
      preferredComponent.requestFocusInWindow();
    }

    if (startedWithKeyboard) {
      // waiting for focus is necessary in case, if 'activate' opens dialog. If we don't wait for focus, after the dialog is shown we'll
      // end up with the table focused instead of the dialog
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> editor.activate());
    }
  }

  private void finishEditing() {
    if (editingRow != -1) {
      editingStopped(null);
    }
  }

  public void editingStopped(@Nullable ChangeEvent event) {
    if (myStoppingEditing) {
      return;
    }
    myStoppingEditing = true;

    LOG.assertTrue(isEditing());
    LOG.assertTrue(editingRow != -1);

    PropertyEditor editor = myProperties.get(editingRow).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener);

    try {
      setValueAt(editor.getValue(), editingRow, editingColumn);
    }
    catch (Exception e) {
      showInvalidInput(e);
    }
    finally {
      removeEditor();
      myStoppingEditing = false;
    }
  }

  @Override
  public void removeEditor() {
    super.removeEditor();
    updateEditActions();
  }

  protected void updateEditActions() {
  }

  private boolean setValueAtRow(int row, final Object newValue) {
    final Property property = myProperties.get(row);

    final Object[] oldValue = new Object[1];
    boolean isNewValue;
    try {
      oldValue[0] = getValue(property);
      isNewValue = !Comparing.equal(oldValue[0], newValue);
      if (newValue == null && oldValue[0] instanceof String && ((String)oldValue[0]).length() == 0) {
        isNewValue = false;
      }
    }
    catch (Throwable e) {
      isNewValue = true;
    }

    boolean isSetValue = true;
    final boolean[] needRefresh = new boolean[1];

    if (isNewValue) {
      isSetValue = doSetValue(() -> {
        for (PropertiesContainer component : myContainers) {
          property.setValue(component, newValue);
          needRefresh[0] |= property.needRefreshPropertyList(component, oldValue[0], newValue);
        }
      });
    }

    if (isSetValue) {
      if (property.needRefreshPropertyList() || needRefresh[0]) {
        update(myContainers, null, property.closeEditorDuringRefresh());
      }
      else {
        myModel.fireTableRowsUpdated(row, row);
      }
    }

    return isSetValue;
  }

  protected abstract boolean doSetValue(ThrowableRunnable<Exception> runnable);

  private static void showInvalidInput(Exception e) {
    Throwable cause = e.getCause();
    String message = cause == null ? e.getMessage() : cause.getMessage();

    if (message == null || message.length() == 0) {
      message = "No message";
    }

    Messages.showMessageDialog(MessageFormat.format("Error setting value: {0}", message),
                               "Invalid Input",
                               Messages.getErrorIcon());
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reimplementation of LookAndFeel's SelectNextRowAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MySelectNextPreviousRowAction extends AbstractAction {
    private boolean selectNext;

    private MySelectNextPreviousRowAction(boolean selectNext) {
      this.selectNext = selectNext;
    }

    public void actionPerformed(ActionEvent e) {
      int rowCount = getRowCount();
      LOG.assertTrue(rowCount > 0);

      int selectedRow = getSelectedRow();
      if (selectedRow == -1) {
        selectedRow = 0;
      }
      else {
        if (selectNext) {
          selectedRow = Math.min(rowCount - 1, getSelectedRow() + 1);
        }
        else {
          selectedRow = Math.max(0, selectedRow - 1);
        }
      }

      if (isEditing()) {
        finishEditing();
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
        startEditing(selectedRow);
      }
      else {
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's StartEditingAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MyStartEditingAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (selectedRow == -1 || isEditing()) {
        return;
      }

      startEditing(selectedRow, true);
    }
  }

  private class MyEnterAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      Property property = myProperties.get(selectedRow);
      if (!getChildren(property).isEmpty()) {
        if (isExpanded(property)) {
          collapse(selectedRow);
        }
        else {
          expand(selectedRow);
        }
      }
      else {
        startEditing(selectedRow, true);
      }
    }
  }

  private class MyExpandCurrentAction extends AbstractAction {
    private final boolean myExpand;
    private final boolean mySelect;

    public MyExpandCurrentAction(boolean expand, boolean select) {
      myExpand = expand;
      mySelect = select;
    }

    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      Property property = myProperties.get(selectedRow);
      List<Property> children = getChildren(property);
      if (!children.isEmpty()) {
        if (myExpand) {
          if (!isExpanded(property)) {
            expand(selectedRow);
          }
          else if (mySelect) {
            restoreSelection(children.get(0));
          }
        }
        else if (isExpanded(property)) {
          collapse(selectedRow);
        }
        else if (mySelect) {
          Property parent = property.getParent();
          if (parent != null) {
            restoreSelection(parent);
          }
        }
      }
      else if (!myExpand && mySelect) {
        Property parent = property.getParent();
        if (parent != null) {
          restoreSelection(parent);
        }
      }
    }
  }

  private class MyRestoreDefaultAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      restoreDefaultValue();
    }
  }

  private class MouseTableListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      int row = rowAtPoint(e.getPoint());
      if (row == -1) {
        return;
      }

      Property property = myProperties.get(row);
      if (getChildren(property).isEmpty()) return;

      Icon icon = UIUtil.getTreeNodeIcon(false, true, true);

      Rectangle rect = getCellRect(row, convertColumnIndexToView(0), false);
      int indent = getBeforeIconAndAfterIndents(property, icon).first;
      if (e.getX() < rect.x + indent ||
          e.getX() > rect.x + indent + icon.getIconWidth() ||
          e.getY() < rect.y ||
          e.getY() > rect.y + rect.height) {
        return;
      }

      // TODO: disallow selection for this row
      if (isExpanded(property)) {
        collapse(row);
      }
      else {
        expand(row);
      }
    }
  }

  private class PropertyTableModel extends AbstractTableModel {
    @Override
    public int getColumnCount() {
      return myColumnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return myColumnNames[column];
    }

    public boolean isCellEditable(int row, int column) {
      return column == 1 && myProperties.get(row).isEditable(getCurrentComponent());
    }

    @Override
    public int getRowCount() {
      return myProperties.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myProperties.get(rowIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      setValueAtRow(rowIndex, aValue);
    }
  }

  private static int getDepth(@NotNull Property property) {
    int result = 0;
    for (Property each = property.getParent(); each != null; each = each.getParent(), result++) {
      // empty
    }
    return result;
  }

  @NotNull
  private static Couple<Integer> getBeforeIconAndAfterIndents(@NotNull Property property, @NotNull Icon icon) {
    int nodeIndent = UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent();
    int beforeIcon = nodeIndent * getDepth(property);

    int leftIconOffset = Math.max(0, UIUtil.getTreeLeftChildIndent() - (icon.getIconWidth() / 2));
    beforeIcon += leftIconOffset;

    int afterIcon = Math.max(0, nodeIndent - leftIconOffset - icon.getIconWidth());

    return Couple.of(beforeIcon, afterIcon);
  }

  private class PropertyCellEditorListener implements PropertyEditorListener {
    @Override
    public void valueCommitted(PropertyEditor source, boolean continueEditing, boolean closeEditorOnError) {
      if (isEditing()) {
        Object value;
        TableCellEditor tableCellEditor = cellEditor;

        try {
          value = tableCellEditor.getCellEditorValue();
        }
        catch (Exception e) {
          showInvalidInput(e);
          return;
        }

        if (setValueAtRow(editingRow, value)) {
          if (!continueEditing && editingRow != -1) {
            PropertyEditor editor = myProperties.get(editingRow).getEditor();
            editor.removePropertyEditorListener(myPropertyEditorListener);
            removeEditor();
          }
        }
        else if (closeEditorOnError) {
          tableCellEditor.cancelCellEditing();
        }
      }
    }

    @Override
    public void editingCanceled(PropertyEditor source) {
      if (isEditing()) {
        cellEditor.cancelCellEditing();
      }
    }

    @Override
    public void preferredSizeChanged(PropertyEditor source) {
    }
  }

  private class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
    private PropertyEditor myEditor;

    public void setEditor(PropertyEditor editor) {
      myEditor = editor;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      try {
        JComponent component = myEditor.getComponent(getCurrentComponent(), getPropertyContext(), getValue((Property)value), null);

        if (component instanceof JComboBox) {
          ComboBox.registerTableCellEditor((JComboBox)component, this);
        }
        else if (component instanceof JCheckBox) {
          component.putClientProperty("JComponent.sizeVariant", UIUtil.isUnderAquaLookAndFeel() ? "small" : null);
        }

        return component;
      }
      catch (Throwable e) {
        LOG.debug(e);
        SimpleColoredComponent errComponent = new SimpleColoredComponent();
        errComponent
          .append(MessageFormat.format("Error getting value: {0}", e.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
        return errComponent;
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> updateEditActions());
      }
    }

    @Override
    public Object getCellEditorValue() {
      try {
        return myEditor.getValue();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void updateRenderer(JComponent component, boolean selected) {
    if (selected) {
      component.setForeground(UIUtil.getTableSelectionForeground());
      component.setBackground(UIUtil.getTableSelectionBackground());
    }
    else {
      component.setForeground(UIUtil.getTableForeground());
      component.setBackground(UIUtil.getTableBackground());
    }
  }

  @NotNull
  protected abstract TextAttributesKey getErrorAttributes(@NotNull HighlightSeverity severity);

  private class PropertyCellRenderer implements TableCellRenderer {
    private final ColoredTableCellRenderer myCellRenderer;
    private final ColoredTableCellRenderer myGroupRenderer;

    private PropertyCellRenderer() {
      myCellRenderer = new MyCellRenderer();
      myGroupRenderer = new MyCellRenderer() {
        private boolean mySelected;
        public boolean myDrawTopLine;


        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          super.customizeCellRenderer(table, value, selected, hasFocus, row, column);
          mySelected = selected;
          myDrawTopLine = row > 0;
        }

        @Override
        protected void paintBackground(Graphics2D g, int x, int width, int height) {
          if (mySelected) {
            super.paintBackground(g, x, width, height);
          }
          else {
            UIUtil.drawHeader(g, x, width, height, true, myDrawTopLine);
          }
        }
      };
    }

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean selected,
                                                   boolean cellHasFocus,
                                                   int row,
                                                   int column) {
      column = table.convertColumnIndexToModel(column);
      Property property = (Property)value;
      Color background = table.getBackground();

      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      boolean tableHasFocus = focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, table);

      ColoredTableCellRenderer renderer = property instanceof GroupProperty ? myGroupRenderer : myCellRenderer;

      renderer.getTableCellRendererComponent(table, value, selected, cellHasFocus, row, column);
      renderer.setBackground(selected ? UIUtil.getTreeSelectionBackground(tableHasFocus) : background);

      if (property instanceof GroupProperty) {
        renderer.setIpad(new Insets(0, 5, 0, 0));
        if (column == 0) {
          renderer.append(property.getName());
        }
        return renderer;
      }

      boolean isDefault = true;
      try {
        for (PropertiesContainer container : myContainers) {
          if (!property.showAsDefault(container)) {
            isDefault = false;
            break;
          }
        }
      }
      catch (Exception e) {
        LOG.debug(e);
      }

      renderer.clear();

      if (column == 0) {
        SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;

        if (!selected && !isDefault) {
          attr = attr.derive(-1, FileStatus.MODIFIED.getColor(), null, null);
        }
        if (property.isImportant()) {
          attr = attr.derive(attr.getStyle() | SimpleTextAttributes.STYLE_BOLD, null, null, null);
        }
        if (property.isExpert()) {
          attr = attr.derive(attr.getStyle() | SimpleTextAttributes.STYLE_ITALIC, null, null, null);
        }
        if (property.isDeprecated()) {
          attr = attr.derive(attr.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null);
        }

        ErrorInfo errorInfo = getErrorInfoForRow(row);
        if (errorInfo != null) {
          SimpleTextAttributes template = SimpleTextAttributes.fromTextAttributes(
            EditorColorsManager.getInstance().getGlobalScheme().getAttributes(getErrorAttributes(errorInfo.getLevel().getSeverity())));

          int style = ((template.getStyle() & SimpleTextAttributes.STYLE_WAVED) != 0 ? SimpleTextAttributes.STYLE_WAVED : 0)
                      | ((template.getStyle() & SimpleTextAttributes.STYLE_UNDERLINE) != 0 ? SimpleTextAttributes.STYLE_UNDERLINE : 0);
          attr = attr.derive(attr.getStyle() | style, template.getFgColor(), null, template.getWaveColor());
        }

        SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), property.getName(),
                                   attr.getStyle(), attr.getFgColor(), attr.getBgColor(), renderer);

        Icon icon = UIUtil.getTreeNodeIcon(isExpanded(property), selected, tableHasFocus);
        boolean hasChildren = !getChildren(property).isEmpty();

        renderer.setIcon(hasChildren ? icon : null);

        Couple<Integer> indents = getBeforeIconAndAfterIndents(property, icon);
        int indent = indents.first;

        if (hasChildren) {
          renderer.setIconTextGap(indents.second);
        }
        else {
          indent += icon.getIconWidth() + indents.second;
        }
        renderer.setIpad(new Insets(0, indent, 0, 0));

        return renderer;
      }
      else {
        try {
          PropertyRenderer valueRenderer = property.getRenderer();
          JComponent component =
            valueRenderer.getComponent(getCurrentComponent(), getPropertyContext(), getValue(property), selected, tableHasFocus);

          component.setBackground(selected ? UIUtil.getTreeSelectionBackground(tableHasFocus) : background);
          component.setFont(table.getFont());

          if (component instanceof JCheckBox) {
            component.putClientProperty("JComponent.sizeVariant", UIUtil.isUnderAquaLookAndFeel() ? "small" : null);
          }

          return component;
        }
        catch (Exception e) {
          LOG.debug(e);
          renderer.append(MessageFormat.format("Error getting value: {0}", e.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return renderer;
        }
      }
    }

    private class MyCellRenderer extends ColoredTableCellRenderer {
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        setPaintFocusBorder(false);
        setFocusBorderAroundIcon(true);
      }
    }
  }

  private static class GroupProperty extends Property {
    public GroupProperty(@Nullable String name) {
      super(null, StringUtil.notNullize(name));
    }

    @NotNull
    @Override
    public PropertyRenderer getRenderer() {
      return new LabelPropertyRenderer(null);
    }

    @Override
    public PropertyEditor getEditor() {
      return null;
    }
  }
}