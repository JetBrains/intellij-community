/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.memory.component.InstancesTracker;
import com.intellij.debugger.memory.utils.AbstractTableColumnDescriptor;
import com.intellij.debugger.memory.utils.AbstractTableModelWithColumns;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.debugger.memory.tracking.TrackerForNewInstances;
import com.intellij.debugger.memory.tracking.TrackingType;
import com.intellij.debugger.memory.utils.InstancesProvider;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClassesTable extends JBTable implements DataProvider, Disposable {
  public static final DataKey<ReferenceType> SELECTED_CLASS_KEY = DataKey.create("ClassesTable.SelectedClass");
  public static final DataKey<XDebugSession> DEBUG_SESSION_KEY = DataKey.create("ClassesTable.DebugSession");
  public static final DataKey<InstancesProvider> NEW_INSTANCES_PROVIDER_KEY =
    DataKey.create("ClassesTable.NewInstances");
  public static final DataKey<ReferenceCountProvider> REF_COUNT_PROVIDER_KEY =
    DataKey.create("ClassesTable.ReferenceCountProvider");

  private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder();

  private static final int CLASSES_COLUMN_PREFERRED_WIDTH = 250;
  private static final int COUNT_COLUMN_MIN_WIDTH = 80;
  private static final int DIFF_COLUMN_MIN_WIDTH = 80;

  private final DiffViewTableModel myModel = new DiffViewTableModel();
  private final UnknownDiffValue myUnknownValue = new UnknownDiffValue();
  private final XDebugSession myDebugSession;
  private final Map<ReferenceType, DiffValue> myCounts = new ConcurrentHashMap<>();
  private final InstancesTracker myInstancesTracker;
  private final ClassesFilteredView myParent;
  private final ReferenceCountProvider myCountProvider;

  private boolean myOnlyWithDiff;
  private boolean myOnlyTracked;
  private boolean myOnlyWithInstances;
  private MinusculeMatcher myMatcher = NameUtil.buildMatcher("*").build();
  private String myFilteringPattern = "";

  private volatile List<ReferenceType> myItems = Collections.unmodifiableList(new ArrayList<>());

  ClassesTable(@NotNull XDebugSession session, boolean onlyWithDiff, boolean onlyWithInstances,
               boolean onlyTracked, @NotNull ClassesFilteredView parent) {
    setModel(myModel);

    myDebugSession = session;
    myOnlyWithDiff = onlyWithDiff;
    myOnlyWithInstances = onlyWithInstances;
    myOnlyTracked = onlyTracked;
    myInstancesTracker = InstancesTracker.getInstance(myDebugSession.getProject());
    myParent = parent;

    TableColumn classesColumn = getColumnModel().getColumn(DiffViewTableModel.CLASSNAME_COLUMN_INDEX);
    TableColumn countColumn = getColumnModel().getColumn(DiffViewTableModel.COUNT_COLUMN_INDEX);
    TableColumn diffColumn = getColumnModel().getColumn(DiffViewTableModel.DIFF_COLUMN_INDEX);

    setAutoResizeMode(AUTO_RESIZE_NEXT_COLUMN);
    classesColumn.setPreferredWidth(JBUI.scale(CLASSES_COLUMN_PREFERRED_WIDTH));

    countColumn.setMinWidth(JBUI.scale(COUNT_COLUMN_MIN_WIDTH));

    diffColumn.setMinWidth(JBUI.scale(DIFF_COLUMN_MIN_WIDTH));

    setShowGrid(false);
    setIntercellSpacing(new JBDimension(0, 0));

    setDefaultRenderer(ReferenceType.class, new MyClassColumnRenderer());
    setDefaultRenderer(Long.class, new MyCountColumnRenderer());
    setDefaultRenderer(DiffValue.class, new MyDiffColumnRenderer());

    TableRowSorter<DiffViewTableModel> sorter = new TableRowSorter<>(myModel);
    sorter.setRowFilter(new RowFilter<DiffViewTableModel, Integer>() {
      @Override
      public boolean include(Entry<? extends DiffViewTableModel, ? extends Integer> entry) {
        int ix = entry.getIdentifier();
        ReferenceType ref = myItems.get(ix);
        DiffValue diff = myCounts.getOrDefault(ref, myUnknownValue);

        boolean isFilteringOptionsRefused = myOnlyWithDiff && diff.diff() == 0
                                            || myOnlyWithInstances && !diff.hasInstance()
                                            || myOnlyTracked && myParent.getStrategy(ref) == null;
        return !(isFilteringOptionsRefused) && myMatcher.matches(ref.name());
      }
    });

    List<RowSorter.SortKey> myDefaultSortingKeys = Arrays.asList(
      new RowSorter.SortKey(DiffViewTableModel.DIFF_COLUMN_INDEX, SortOrder.DESCENDING),
      new RowSorter.SortKey(DiffViewTableModel.COUNT_COLUMN_INDEX, SortOrder.DESCENDING),
      new RowSorter.SortKey(DiffViewTableModel.CLASSNAME_COLUMN_INDEX, SortOrder.ASCENDING)
    );
    sorter.setSortKeys(myDefaultSortingKeys);
    setRowSorter(sorter);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myCountProvider = new ReferenceCountProvider() {
      @Override
      public int getTotalCount(@NotNull ReferenceType ref) {
        return (int)myCounts.get(ref).myCurrentCount;
      }

      @Override
      public int getDiffCount(@NotNull ReferenceType ref) {
        return (int)myCounts.get(ref).diff();
      }

      @Override
      public int getNewInstancesCount(@NotNull ReferenceType ref) {
        TrackerForNewInstances strategy = myParent.getStrategy(ref);
        return strategy == null || !strategy.isReady() ? -1 : strategy.getCount();
      }
    };
  }

  public interface ReferenceCountProvider {
    int getTotalCount(@NotNull ReferenceType ref);

    int getDiffCount(@NotNull ReferenceType ref);

    int getNewInstancesCount(@NotNull ReferenceType ref);
  }

  @Nullable
  ReferenceType getSelectedClass() {
    int selectedRow = getSelectedRow();
    if (selectedRow != -1) {
      int ix = convertRowIndexToModel(selectedRow);
      return myItems.get(ix);
    }

    return null;
  }

  @Nullable
  ReferenceType getClassByName(@NotNull String name) {
    for (ReferenceType ref : myItems) {
      if (name.equals(ref.name())) {
        return ref;
      }
    }

    return null;
  }

  void setBusy(boolean value) {
    setPaintBusy(value);
  }

  void setFilterPattern(String pattern) {
    if (!myFilteringPattern.equals(pattern)) {
      myFilteringPattern = pattern;
      myMatcher = NameUtil.buildMatcher("*" + pattern).build();
      getRowSorter().allRowsChanged();
      if (getSelectedClass() == null && getRowCount() > 0) {
        getSelectionModel().setSelectionInterval(0, 0);
      }
    }
  }

  void setFilteringByInstanceExists(boolean value) {
    if (value != myOnlyWithInstances) {
      myOnlyWithInstances = value;
      getRowSorter().allRowsChanged();
    }
  }

  void setFilteringByDiffNonZero(boolean value) {
    if (myOnlyWithDiff != value) {
      myOnlyWithDiff = value;
      getRowSorter().allRowsChanged();
    }
  }

  void setFilteringByTrackingState(boolean value) {
    if (myOnlyTracked != value) {
      myOnlyTracked = value;
      getRowSorter().allRowsChanged();
    }
  }

  void setClassesAndUpdateCounts(@NotNull List<ReferenceType> classes, @NotNull long[] counts) {
    assert classes.size() == counts.length;
    ReferenceType selectedClass = myModel.getSelectedClassBeforeHided();
    int newSelectedIndex = classes.indexOf(selectedClass);
    boolean isInitialized = !myItems.isEmpty();
    myItems = Collections.unmodifiableList(new ArrayList<>(classes));

    for (int i = 0, size = classes.size(); i < size; i++) {
      ReferenceType ref = classes.get(i);
      DiffValue oldValue = isInitialized && !myCounts.containsKey(ref)
                           ? new DiffValue(0, 0)
                           : myCounts.getOrDefault(ref, myUnknownValue);
      myCounts.put(ref, oldValue.update(counts[i]));
    }

    showContent();

    if (newSelectedIndex != -1 && !myModel.isHided()) {
      int ix = convertRowIndexToView(newSelectedIndex);
      changeSelection(ix,
                      DiffViewTableModel.CLASSNAME_COLUMN_INDEX, false, false);
    }

    getRowSorter().allRowsChanged();
  }

  void hideContent() {
    myModel.hide();
  }

  private void showContent() {
    myModel.show();
  }


  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (SELECTED_CLASS_KEY.is(dataId)) {
      return getSelectedClass();
    }
    if (DEBUG_SESSION_KEY.is(dataId)) {
      return myDebugSession;
    }
    if (NEW_INSTANCES_PROVIDER_KEY.is(dataId)) {
      ReferenceType selectedClass = getSelectedClass();
      if (selectedClass != null) {
        TrackerForNewInstances strategy = myParent.getStrategy(selectedClass);
        if (strategy != null && strategy.isReady()) {
          List<ObjectReference> newInstances = strategy.getNewInstances();
          return (InstancesProvider)limit -> newInstances;
        }
      }
    }

    if (REF_COUNT_PROVIDER_KEY.is(dataId)) {
      return myCountProvider;
    }

    return null;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  private TrackingType getTrackingType(int row) {
    ReferenceType ref = (ReferenceType)getValueAt(row, convertColumnIndexToView(DiffViewTableModel.CLASSNAME_COLUMN_INDEX));
    return myInstancesTracker.getTrackingType(ref.name());
  }

  class DiffViewTableModel extends AbstractTableModelWithColumns {
    final static int CLASSNAME_COLUMN_INDEX = 0;
    final static int COUNT_COLUMN_INDEX = 1;
    final static int DIFF_COLUMN_INDEX = 2;

    // Workaround: save selection after content of classes table has been hided
    private ReferenceType mySelectedClassWhenHided = null;
    private boolean myIsWithContent = false;

    DiffViewTableModel() {
      super(new AbstractTableColumnDescriptor[]{
        new AbstractTableColumnDescriptor("Class", ReferenceType.class) {
          @Override
          public Object getValue(int ix) {
            return myItems.get(ix);
          }
        },
        new AbstractTableColumnDescriptor("Count", Long.class) {
          @Override
          public Object getValue(int ix) {
            return myCounts.getOrDefault(myItems.get(ix), myUnknownValue).myCurrentCount;
          }
        },
        new AbstractTableColumnDescriptor("Diff", DiffValue.class) {
          @Override
          public Object getValue(int ix) {
            return myCounts.getOrDefault(myItems.get(ix), myUnknownValue);
          }
        }
      });
    }

    ReferenceType getSelectedClassBeforeHided() {
      return mySelectedClassWhenHided;
    }

    void hide() {
      if (myIsWithContent) {
        mySelectedClassWhenHided = getSelectedClass();
        myIsWithContent = false;
        clearSelection();
        fireTableDataChanged();
      }
    }

    void show() {
      if (!myIsWithContent) {
        myIsWithContent = true;
        getRowSorter().allRowsChanged();
      }
    }

    boolean isHided() {
      return !myIsWithContent;
    }

    @Override
    public int getRowCount() {
      return myIsWithContent ? myItems.size() : 0;
    }
  }

  /**
   * State transmissions for DiffValue and UnknownDiffValue
   * unknown -> diff
   * diff -> diff
   * <p>
   * State descriptions:
   * Unknown - instances count never executed
   * Diff - actual value
   */
  private static class UnknownDiffValue extends DiffValue {
    UnknownDiffValue() {
      super(0);
    }

    @Override
    boolean hasInstance() {
      return true;
    }

    @Override
    DiffValue update(long count) {
      return new DiffValue(count);
    }
  }

  private static class DiffValue implements Comparable<DiffValue> {
    private long myOldCount;

    private long myCurrentCount;

    DiffValue(long count) {
      this(count, count);
    }

    DiffValue(long old, long current) {
      myCurrentCount = current;
      myOldCount = old;
    }

    DiffValue update(long count) {
      myOldCount = myCurrentCount;
      myCurrentCount = count;
      return this;
    }

    boolean hasInstance() {
      return myCurrentCount > 0;
    }

    long diff() {
      return myCurrentCount - myOldCount;
    }

    @Override
    public int compareTo(@NotNull DiffValue o) {
      return Long.compare(diff(), o.diff());
    }
  }

  private abstract class MyTableCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean isSelected,
                                         boolean hasFocus, int row, int column) {
      column = convertColumnIndexToModel(column);
      TrackingType trackingType = getTrackingType(row);

      if (hasFocus) {
        setBorder(EMPTY_BORDER);
      }

      if (trackingType != null && column == DiffViewTableModel.DIFF_COLUMN_INDEX) {
        setIcon(AllIcons.Debugger.MemoryView.ClassTracked);
        setTransparentIconBackground(true);
      }

      if (value != null) {
        addText(value, isSelected, row);
      }
    }

    protected abstract void addText(@NotNull Object value, boolean isSelected, int row);
  }

  private class MyClassColumnRenderer extends MyTableCellRenderer {
    @Override
    protected void addText(@NotNull Object value, boolean isSelected,
                           int row) {
      String presentation = ((ReferenceType)value).name();
      append(" ");
      if (isSelected) {
        FList<TextRange> textRanges = myMatcher.matchingFragments(presentation);
        if (textRanges != null) {
          SimpleTextAttributes attributes = new SimpleTextAttributes(getBackground(), getForeground(), null,
                                                                     SimpleTextAttributes.STYLE_SEARCH_MATCH);
          SpeedSearchUtil.appendColoredFragments(this, presentation, textRanges,
                                                 SimpleTextAttributes.REGULAR_ATTRIBUTES, attributes);
        }
      }
      else {
        append(String.format("%s", presentation), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private class MyCountColumnRenderer extends MyTableCellRenderer {
    @Override
    protected void addText(@NotNull Object value, boolean isSelected,
                           int row) {
      setTextAlign(SwingConstants.RIGHT);
      append(value.toString());
    }
  }

  private class MyDiffColumnRenderer extends MyTableCellRenderer {
    private final SimpleTextAttributes myClickableCellAttributes =
      new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, JBColor.BLUE);

    @Override
    protected void addText(@NotNull Object value, boolean isSelected,
                           int row) {
      setTextAlign(SwingConstants.RIGHT);

      ReferenceType ref = myItems.get(convertRowIndexToModel(row));

      long diff = myCountProvider.getDiffCount(ref);
      String text = String.format("%s%d", diff > 0 ? "+" : "", diff);

      int newInstancesCount = myCountProvider.getNewInstancesCount(ref);
      if (newInstancesCount >= 0) {
        if (newInstancesCount == diff) {
          append(text, diff == 0 ? SimpleTextAttributes.REGULAR_ATTRIBUTES : myClickableCellAttributes);
        }
        else {
          append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (newInstancesCount != 0) {
            append(String.format(" (%d)", newInstancesCount), myClickableCellAttributes);
          }
        }
      }
      else {
        append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
