/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.xmlb.XmlSerializer;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.util.List;

/**
 * @author egor
 */
public class CaptureConfigurable implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance(CaptureConfigurable.class);

  private MyTableModel myTableModel;

  @NotNull
  @Override
  public String getId() {
    return "reference.idesettings.debugger.capture";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myTableModel = new MyTableModel();

    JBTable table = new JBTable(myTableModel);
    table.setColumnSelectionAllowed(false);

    TableColumnModel columnModel = table.getColumnModel();
    TableUtil.setupCheckboxColumn(columnModel.getColumn(MyTableModel.ENABLED_COLUMN));

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        myTableModel.addRow();
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.removeSelectedItems(table);
      }
    });
    decorator.setMoveUpAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.moveSelectedItemsUp(table);
      }
    });
    decorator.setMoveDownAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.moveSelectedItemsDown(table);
      }
    });

    decorator.addExtraAction(new DumbAwareActionButton("Duplicate", "Duplicate", PlatformIcons.COPY_ICON) {
      @Override
      public boolean isEnabled() {
        return table.getSelectedRowCount() == 1;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> {
          try {
            myTableModel.add(c.clone());
            table.getSelectionModel().setSelectionInterval(table.getRowCount() - 1, table.getRowCount() - 1);
          }
          catch (CloneNotSupportedException ex) {
            LOG.error(ex);
          }
        });
      }
    });

    decorator.addExtraAction(new DumbAwareActionButton("Import", "Import", AllIcons.Actions.Install) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, false, true, false) {
          @Override
          public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
            return super.isFileVisible(file, showHiddenFiles) &&
                   (file.isDirectory() || "xml".equals(file.getExtension()) || file.getFileType() == FileTypes.ARCHIVE);
          }

          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return file.getFileType() == StdFileTypes.XML;
          }
        };
        descriptor.setDescription("Please select a file to import.");
        descriptor.setTitle("Import Capture Points");

        VirtualFile file = FileChooser.chooseFile(descriptor, e.getProject(), null);
        if (file == null) return;
        try {
          Document document = JDOMUtil.loadDocument(file.getInputStream());
          table.getSelectionModel().clearSelection();
          int start = table.getRowCount();
          List<Element> children = document.getRootElement().getChildren();
          children.forEach(element -> myTableModel.add(XmlSerializer.deserialize(element, CapturePoint.class)));
          table.getSelectionModel().addSelectionInterval(start, table.getRowCount() - 1);
        }
        catch (Exception ex) {
          final String msg = ex.getLocalizedMessage();
          Messages.showErrorDialog(e.getProject(), msg != null && msg.length() > 0 ? msg : ex.toString(), "Export Failed");
        }
      }
    });
    decorator.addExtraAction(new DumbAwareActionButton("Export", "Export", AllIcons.Actions.Export) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        VirtualFileWrapper wrapper = FileChooserFactory.getInstance()
          .createSaveFileDialog(new FileSaverDescriptor("Export Selected Capture Points to File...", "", "xml"), e.getProject())
          .save(null, null);
        if (wrapper == null) return;

        Element rootElement = new Element("capture-points");
        selectedCapturePoints(table).forEach(c -> {
          try {
            CapturePoint clone = c.clone();
            clone.myEnabled = false;
            rootElement.addContent(XmlSerializer.serialize(clone));
          }
          catch (CloneNotSupportedException ex) {
            LOG.error(ex);
          }
        });
        try {
          JDOMUtil.write(rootElement, wrapper.getFile());
        }
        catch (Exception ex) {
          final String msg = ex.getLocalizedMessage();
          Messages.showErrorDialog(e.getProject(), msg != null && msg.length() > 0 ? msg : ex.toString(), "Export Failed");
        }
      }

      @Override
      public boolean isEnabled() {
        return table.getSelectedRowCount() > 0;
      }
    });

    return decorator.createPanel();
  }

  private StreamEx<CapturePoint> selectedCapturePoints(JBTable table) {
    return IntStreamEx.of(table.getSelectedRows()).map(table::convertRowIndexToModel).mapToObj(myTableModel::get);
  }

  private static class MyTableModel extends AbstractTableModel implements ItemRemovable {
    public static final int ENABLED_COLUMN = 0;
    public static final int CLASS_COLUMN = 1;
    public static final int METHOD_COLUMN = 2;
    public static final int PARAM_COLUMN = 3;
    public static final int INSERT_CLASS_COLUMN = 4;
    public static final int INSERT_METHOD_COLUMN = 5;
    public static final int INSERT_KEY_EXPR = 6;

    static final String[] COLUMN_NAMES =
      new String[]{"", "Capture class name", "Capture method name", "Capture key expression", "Insert class name", "Insert method name", "Insert key expression"};
    List<CapturePoint> myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();

    public String getColumnName(int column) {
      return COLUMN_NAMES[column];
    }

    public int getRowCount() {
      return myCapturePoints.size();
    }

    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    public Object getValueAt(int row, int col) {
      CapturePoint point = myCapturePoints.get(row);
      switch (col) {
        case ENABLED_COLUMN:
          return point.myEnabled;
        case CLASS_COLUMN:
          return point.myClassName;
        case METHOD_COLUMN:
          return point.myMethodName;
        case PARAM_COLUMN:
          return point.myCaptureKeyExpression;
        case INSERT_CLASS_COLUMN:
          return point.myInsertClassName;
        case INSERT_METHOD_COLUMN:
          return point.myInsertMethodName;
        case INSERT_KEY_EXPR:
          return point.myInsertKeyExpression;
      }
      return null;
    }

    public boolean isCellEditable(int row, int column) {
      return true;
    }

    public void setValueAt(Object value, int row, int col) {
      CapturePoint point = myCapturePoints.get(row);
      switch (col) {
        case ENABLED_COLUMN:
          point.myEnabled = (boolean)value;
          break;
        case CLASS_COLUMN:
          point.myClassName = (String)value;
          break;
        case METHOD_COLUMN:
          point.myMethodName = (String)value;
          break;
        case PARAM_COLUMN:
          point.myCaptureKeyExpression = (String)value;
          break;
        case INSERT_CLASS_COLUMN:
          point.myInsertClassName = (String)value;
          break;
        case INSERT_METHOD_COLUMN:
          point.myInsertMethodName = (String)value;
          break;
        case INSERT_KEY_EXPR:
          point.myInsertKeyExpression = (String)value;
          break;
      }
      fireTableCellUpdated(row, col);
    }

    public Class getColumnClass(int columnIndex) {
      switch (columnIndex) {
        case ENABLED_COLUMN:
          return Boolean.class;
      }
      return String.class;
    }

    CapturePoint get(int idx) {
      return myCapturePoints.get(idx);
    }

    public void add(CapturePoint p) {
      myCapturePoints.add(p);
      int lastRow = getRowCount() - 1;
      fireTableRowsInserted(lastRow, lastRow);
    }

    public void addRow() {
      add(new CapturePoint());
    }

    public void removeRow(final int row) {
      myCapturePoints.remove(row);
      fireTableRowsDeleted(row, row);
    }
  }

  @Override
  public boolean isModified() {
    return !DebuggerSettings.getInstance().getCapturePoints().equals(myTableModel.myCapturePoints);
  }

  @Override
  public void apply() throws ConfigurationException {
    DebuggerSettings.getInstance().setCapturePoints(myTableModel.myCapturePoints);
  }

  @Override
  public void reset() {
    myTableModel.myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
    myTableModel.fireTableDataChanged();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return DebuggerBundle.message("async.stacktraces.configurable.display.name");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }
}
