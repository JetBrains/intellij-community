// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AnnotationsPanel;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable.NoScroll;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.xmlb.XmlSerializer;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class CaptureConfigurable implements SearchableConfigurable, NoScroll {
  private static final Logger LOG = Logger.getInstance(CaptureConfigurable.class);
  private final Project myProject;

  private JCheckBox myDebuggerAgent;
  private JButton myConfigureAnnotationsButton;
  private JPanel myCapturePanel;
  private MyTableModel myTableModel;
  private JCheckBox myCaptureVariables;
  private JPanel myPanel;

  public CaptureConfigurable(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger.capture";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myTableModel = new MyTableModel();

    JBTable table = new JBTable(myTableModel);
    table.setColumnSelectionAllowed(false);
    table.setShowGrid(false);

    JTextField stringCellEditor = new JTextField();
    stringCellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, Boolean.TRUE);
    table.setDefaultEditor(String.class, new DefaultCellEditor(stringCellEditor));
    table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        Dimension editorSize = stringCellEditor.getPreferredSize();
        size.height = Math.max(size.height, editorSize.height);
        return size;
      }
    });

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

    decorator.addExtraAction(new DumbAwareAction(JavaDebuggerBundle.messagePointer("action.AnActionButton.text.duplicate"),
                                                 JavaDebuggerBundle.messagePointer("action.AnActionButton.description.duplicate"),
                                                 PlatformIcons.COPY_ICON) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(table.getSelectedRowCount() == 1);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> {
          try {
            int idx = myTableModel.add(c.clone());
            table.getSelectionModel().setSelectionInterval(idx, idx);
          }
          catch (CloneNotSupportedException ex) {
            LOG.error(ex);
          }
        });
      }
    });

    decorator.addExtraAction(new DumbAwareAction(JavaDebuggerBundle.messagePointer("action.AnActionButton.text.enable.selected"),
                                                 JavaDebuggerBundle.messagePointer("action.AnActionButton.description.enable.selected"),
                                                 PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(table.getSelectedRowCount() > 0);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> c.myEnabled = true);
        table.repaint();
      }
    });
    decorator.addExtraAction(new DumbAwareAction(JavaDebuggerBundle.messagePointer("action.AnActionButton.text.disable.selected"),
                                                 JavaDebuggerBundle.messagePointer("action.AnActionButton.description.disable.selected"),
                                                 PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(table.getSelectedRowCount() > 0);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> c.myEnabled = false);
        table.repaint();
      }
    });

    new DumbAwareAction(CommonBundle.message("action.text.toggle")) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(table.getSelectedRowCount() == 1 && !table.isEditing());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> c.myEnabled = !c.myEnabled);
        table.repaint();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), table);

    decorator.addExtraAction(new DumbAwareAction(JavaDebuggerBundle.messagePointer("action.AnActionButton.text.import"),
                                                 JavaDebuggerBundle.messagePointer("action.AnActionButton.description.import"),
                                                 AllIcons.Actions.Install) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        var descriptor = new FileChooserDescriptor(true, false, true, false, true, true)
          .withExtensionFilter(FileTypeManager.getInstance().getStdFileType("XML"))
          .withTitle(JavaDebuggerBundle.message("import.capture.points"))
          .withDescription(JavaDebuggerBundle.message("please.select.a.file.to.import"));

        VirtualFile[] files = FileChooser.chooseFiles(descriptor, e.getProject(), null);
        if (ArrayUtil.isEmpty(files)) return;

        table.getSelectionModel().clearSelection();

        for (VirtualFile file : files) {
          try {
            for (Element element : JDOMUtil.load(file.getInputStream()).getChildren()) {
              int idx = myTableModel.addIfNeeded(XmlSerializer.deserialize(element, CapturePoint.class));
              table.getSelectionModel().addSelectionInterval(idx, idx);
            }
          }
          catch (Exception ex) {
            final String msg = ex.getLocalizedMessage();
            Messages.showErrorDialog(e.getProject(), msg != null && !msg.isEmpty() ? msg : ex.toString(),
                                     JavaDebuggerBundle.message("export.failed"));
          }
        }
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    });
    decorator.addExtraAction(new DumbAwareAction(JavaDebuggerBundle.messagePointer("action.AnActionButton.text.export"),
                                                 JavaDebuggerBundle.messagePointer("action.AnActionButton.description.export"),
                                                 AllIcons.ToolbarDecorator.Export) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        VirtualFileWrapper wrapper = FileChooserFactory.getInstance()
          .createSaveFileDialog(new FileSaverDescriptor(JavaDebuggerBundle.message("export.selected.capture.points.to.file"), "", "xml"), e.getProject())
          .save((Path)null, null);
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
          JDOMUtil.write(rootElement, wrapper.getFile().toPath());
        }
        catch (Exception ex) {
          final String msg = ex.getLocalizedMessage();
          Messages.showErrorDialog(e.getProject(), msg != null && !msg.isEmpty() ? msg : ex.toString(),
                                   JavaDebuggerBundle.message("export.failed"));
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(table.getSelectedRowCount() > 0);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    });

    myConfigureAnnotationsButton.addActionListener(e -> new AsyncAnnotationsDialog(myProject).show());

    myCapturePanel.setBorder(
      IdeBorderFactory.createTitledBorder(JavaDebuggerBundle.message("settings.breakpoints.based"), false, JBUI.insetsTop(8))
        .setShowLine(false));
    myCapturePanel.add(decorator.createPanel(), BorderLayout.CENTER);

    return myPanel;
  }

  private StreamEx<CapturePoint> selectedCapturePoints(JBTable table) {
    return IntStreamEx.of(table.getSelectedRows()).map(table::convertRowIndexToModel).mapToObj(myTableModel::get);
  }

  private static final class MyTableModel extends AbstractTableModel implements ItemRemovable {
    public static final int ENABLED_COLUMN = 0;
    public static final int CLASS_COLUMN = 1;
    public static final int METHOD_COLUMN = 2;
    public static final int PARAM_COLUMN = 3;
    public static final int INSERT_CLASS_COLUMN = 4;
    public static final int INSERT_METHOD_COLUMN = 5;
    public static final int INSERT_KEY_EXPR = 6;

    static final String[] COLUMN_NAMES = getColumns();

    private static @NotNull String @NotNull [] getColumns() {
      return new String[]{"",
        JavaDebuggerBundle.message("settings.capture.column.capture.class.name"),
        JavaDebuggerBundle.message("settings.capture.column.capture.method.name"),
        JavaDebuggerBundle.message("settings.capture.column.capture.key.expression"),
        JavaDebuggerBundle.message("settings.capture.column.insert.class.name"),
        JavaDebuggerBundle.message("settings.capture.column.insert.method.name"),
        JavaDebuggerBundle.message("settings.capture.column.insert.key.expression")};
    }

    List<CapturePoint> myCapturePoints;

    private MyTableModel() {
      myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
    }

    @Override
    public String getColumnName(int column) {
      return COLUMN_NAMES[column];
    }

    @Override
    public int getRowCount() {
      return myCapturePoints.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    @Override
    public Object getValueAt(int row, int col) {
      CapturePoint point = myCapturePoints.get(row);
      return switch (col) {
        case ENABLED_COLUMN -> point.myEnabled;
        case CLASS_COLUMN -> point.myClassName;
        case METHOD_COLUMN -> point.myMethodName;
        case PARAM_COLUMN -> point.myCaptureKeyExpression;
        case INSERT_CLASS_COLUMN -> point.myInsertClassName;
        case INSERT_METHOD_COLUMN -> point.myInsertMethodName;
        case INSERT_KEY_EXPR -> point.myInsertKeyExpression;
        default -> null;
      };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      CapturePoint point = myCapturePoints.get(row);
      switch (col) {
        case ENABLED_COLUMN -> point.myEnabled = (boolean)value;
        case CLASS_COLUMN -> point.myClassName = (String)value;
        case METHOD_COLUMN -> point.myMethodName = (String)value;
        case PARAM_COLUMN -> point.myCaptureKeyExpression = (String)value;
        case INSERT_CLASS_COLUMN -> point.myInsertClassName = (String)value;
        case INSERT_METHOD_COLUMN -> point.myInsertMethodName = (String)value;
        case INSERT_KEY_EXPR -> point.myInsertKeyExpression = (String)value;
      }
      fireTableCellUpdated(row, col);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == ENABLED_COLUMN ? Boolean.class : String.class;
    }

    CapturePoint get(int idx) {
      return myCapturePoints.get(idx);
    }

    int add(CapturePoint p) {
      myCapturePoints.add(p);
      int lastRow = getRowCount() - 1;
      fireTableRowsInserted(lastRow, lastRow);
      return lastRow;
    }

    int addIfNeeded(CapturePoint p) {
      CapturePoint clone = p;
      try {
        clone = p.clone();
        clone.myEnabled = !clone.myEnabled;
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
      }
      int idx = myCapturePoints.indexOf(p);
      if (idx < 0) {
        idx = myCapturePoints.indexOf(clone);
      }
      if (idx < 0) {
        idx = add(p);
      }
      return idx;
    }

    public void addRow() {
      add(new CapturePoint());
    }

    @Override
    public void removeRow(final int row) {
      myCapturePoints.remove(row);
      fireTableRowsDeleted(row, row);
    }
  }

  @Override
  public boolean isModified() {
    return DebuggerSettings.getInstance().CAPTURE_VARIABLES != myCaptureVariables.isSelected() ||
           DebuggerSettings.getInstance().INSTRUMENTING_AGENT != myDebuggerAgent.isSelected() ||
           !DebuggerSettings.getInstance().getCapturePoints().equals(myTableModel.myCapturePoints);
  }

  @Override
  public void apply() throws ConfigurationException {
    DebuggerSettings.getInstance().setCapturePoints(myTableModel.myCapturePoints);
    DebuggerSettings.getInstance().CAPTURE_VARIABLES = myCaptureVariables.isSelected();
    DebuggerSettings.getInstance().INSTRUMENTING_AGENT = myDebuggerAgent.isSelected();
  }

  @Override
  public void reset() {
    myCaptureVariables.setSelected(DebuggerSettings.getInstance().CAPTURE_VARIABLES);
    myDebuggerAgent.setSelected(DebuggerSettings.getInstance().INSTRUMENTING_AGENT);
    myTableModel.myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
    myTableModel.fireTableDataChanged();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("async.stacktraces.configurable.display.name");
  }

  interface CapturePointConsumer {
    void accept(boolean capture, PsiModifierListOwner e, PsiAnnotation annotation);
  }

  static void processCaptureAnnotations(@Nullable Project project, CapturePointConsumer consumer) {
    if (project == null) { // fallback
      project = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
    }
    if (!project.isDefault()) {
      DebuggerProjectSettings debuggerProjectSettings = DebuggerProjectSettings.getInstance(project);
      scanPointsInt(project, debuggerProjectSettings, true, consumer);
      scanPointsInt(project, debuggerProjectSettings, false, consumer);
    }
  }

  private static void scanPointsInt(Project project,
                                    DebuggerProjectSettings debuggerProjectSettings,
                                    boolean capture,
                                    CapturePointConsumer consumer) {
    try {
      getAsyncAnnotations(debuggerProjectSettings, capture)
        .forEach(annotationName -> NodeRendererSettings.visitAnnotatedElements(annotationName, project,
                                                                               (e, annotation) -> consumer.accept(capture, e, annotation),
                                                                               PsiMethod.class, PsiParameter.class));
    }
    catch (IndexNotReadyException | ProcessCanceledException | AlreadyDisposedException ignore) {
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  static String getAnnotationName(boolean capture) {
    return (capture ? Async.Schedule.class : Async.Execute.class).getName().replace("$", ".");
  }

  private static List<String> getAsyncAnnotations(DebuggerProjectSettings debuggerProjectSettings, boolean capture) {
    return StreamEx.of(capture ? debuggerProjectSettings.myAsyncScheduleAnnotations : debuggerProjectSettings.myAsyncExecuteAnnotations)
      .prepend(getAnnotationName(capture))
      .toList();
  }

  private final class AsyncAnnotationsDialog extends DialogWrapper {
    private final AnnotationsPanel myAsyncSchedulePanel;
    private final AnnotationsPanel myAsyncExecutePanel;
    private final DebuggerProjectSettings mySettings;

    private AsyncAnnotationsDialog(@NotNull Project project) {
      super(project, true);
      mySettings = DebuggerProjectSettings.getInstance(myProject);
      myAsyncSchedulePanel = new AnnotationsPanel(project,
                                                  JavaDebuggerBundle.message("settings.async.schedule"),
                                                  "",
                                                  getAsyncAnnotations(mySettings, true),
                                                  Collections.singletonList(getAnnotationName(true)),
                                                  Collections.emptySet(), false, false);
      myAsyncExecutePanel = new AnnotationsPanel(project,
                                                 JavaDebuggerBundle.message("settings.async.execute"),
                                                 "",
                                                 getAsyncAnnotations(mySettings, false),
                                                 Collections.singletonList(getAnnotationName(false)),
                                                 Collections.emptySet(), false, false);
      init();
      setTitle(JavaDebuggerBundle.message("settings.async.annotations.configuration"));
    }

    @Override
    protected JComponent createCenterPanel() {
      final Splitter splitter = new Splitter(true);
      splitter.setFirstComponent(myAsyncSchedulePanel.getComponent());
      splitter.setSecondComponent(myAsyncExecutePanel.getComponent());
      splitter.setHonorComponentsMinimumSize(true);
      splitter.setPreferredSize(JBUI.size(300, 400));
      return splitter;
    }

    @Override
    protected void doOKAction() {
      mySettings.myAsyncScheduleAnnotations = StreamEx.of(myAsyncSchedulePanel.getAnnotations())
        .filter(e -> !e.equals(getAnnotationName(true)))
        .toArray(ArrayUtilRt.EMPTY_STRING_ARRAY);
      mySettings.myAsyncExecuteAnnotations = StreamEx.of(myAsyncExecutePanel.getAnnotations())
        .filter(e -> !e.equals(getAnnotationName(false)))
        .toArray(ArrayUtilRt.EMPTY_STRING_ARRAY);
      super.doOKAction();
    }

    @Override
    protected @NotNull String getHelpId() {
      return "reference.idesettings.debugger.customAsyncAnnotations";
    }
  }
}
