/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.xmlb.XmlSerializer;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Debugger;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author egor
 */
public class CaptureConfigurable implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance(CaptureConfigurable.class);

  private JCheckBox myDebuggerAgent;
  private MyTableModel myTableModel;
  private JCheckBox myCaptureVariables;

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
            int idx = myTableModel.add(c.clone());
            table.getSelectionModel().setSelectionInterval(idx, idx);
          }
          catch (CloneNotSupportedException ex) {
            LOG.error(ex);
          }
        });
      }
    });

    decorator.addExtraAction(new DumbAwareActionButton("Enable Selected", "Enable Selected", PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public boolean isEnabled() {
        return table.getSelectedRowCount() > 0;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> c.myEnabled = true);
        table.repaint();
      }
    });
    decorator.addExtraAction(new DumbAwareActionButton("Disable Selected", "Disable Selected", PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public boolean isEnabled() {
        return table.getSelectedRowCount() > 0;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> c.myEnabled = false);
        table.repaint();
      }
    });

    new DumbAwareAction("Toggle") {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(table.getSelectedRowCount() == 1 && !table.isEditing());
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        selectedCapturePoints(table).forEach(c -> c.myEnabled = !c.myEnabled);
        table.repaint();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), table);

    decorator.addExtraAction(new DumbAwareActionButton("Import", "Import", AllIcons.Actions.Install) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, false, true, true) {
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

        VirtualFile[] files = FileChooser.chooseFiles(descriptor, e.getProject(), null);
        if (ArrayUtil.isEmpty(files)) return;

        table.getSelectionModel().clearSelection();

        for (VirtualFile file : files) {
          try {
            Document document = JDOMUtil.loadDocument(file.getInputStream());
            List<Element> children = document.getRootElement().getChildren();
            children.forEach(element -> {
              int idx = myTableModel.addIfNeeded(XmlSerializer.deserialize(element, CapturePoint.class));
              table.getSelectionModel().addSelectionInterval(idx, idx);
            });
          }
          catch (Exception ex) {
            final String msg = ex.getLocalizedMessage();
            Messages.showErrorDialog(e.getProject(), msg != null && msg.length() > 0 ? msg : ex.toString(), "Export Failed");
          }
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

    BorderLayoutPanel panel = JBUI.Panels.simplePanel();
    myDebuggerAgent = new JCheckBox(DebuggerBundle.message("label.capture.configurable.debugger.agent"));
    panel.addToTop(myDebuggerAgent);

    BorderLayoutPanel debuggerPanel = JBUI.Panels.simplePanel();
    debuggerPanel.setBorder(IdeBorderFactory.createTitledBorder("Breakpoints based", false));
    debuggerPanel.addToCenter(decorator.createPanel());

    myCaptureVariables = new JCheckBox(DebuggerBundle.message("label.capture.configurable.capture.variables"));
    debuggerPanel.addToBottom(myCaptureVariables);

    panel.addToCenter(debuggerPanel);
    return panel;
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
    List<CapturePoint> myCapturePoints;

    private MyTableModel() {
      myCapturePoints = DebuggerSettings.getInstance().cloneCapturePoints();
      scanPoints();
    }

    private void scanPoints() {
      if (Registry.is("debugger.capture.points.annotations")) {
        List<CapturePoint> capturePointsFromAnnotations = new ArrayList<>();
        processCaptureAnnotations((capture, e) -> {
          if (e instanceof PsiMethod) {
            addCapturePointIfNeeded(e, (PsiMethod)e, "this", capture, capturePointsFromAnnotations);
          }
          else if (e instanceof PsiParameter) {
            PsiParameter psiParameter = (PsiParameter)e;
            PsiMethod psiMethod = (PsiMethod)psiParameter.getDeclarationScope();
            addCapturePointIfNeeded(psiParameter, psiMethod,
                                    DecompiledLocalVariable.PARAM_PREFIX + psiMethod.getParameterList().getParameterIndex(psiParameter),
                                    capture, capturePointsFromAnnotations);
          }
        });

        capturePointsFromAnnotations.forEach(this::addIfNeeded);
      }
    }

    private static void addCapturePointIfNeeded(PsiModifierListOwner psiElement,
                                                PsiMethod psiMethod,
                                                String defaultExpression,
                                                boolean capture,
                                                List<CapturePoint> capturePointsFromAnnotations) {
      CapturePoint capturePoint = new CapturePoint();
      capturePoint.myEnabled = false;
      if (capture) {
        capturePoint.myClassName = JVMNameUtil.getNonAnonymousClassName(psiMethod.getContainingClass());
        capturePoint.myMethodName = JVMNameUtil.getJVMMethodName(psiMethod);
      }
      else {
        capturePoint.myInsertClassName = JVMNameUtil.getNonAnonymousClassName(psiMethod.getContainingClass());
        capturePoint.myInsertMethodName = JVMNameUtil.getJVMMethodName(psiMethod);
      }

      PsiModifierList modifierList = psiElement.getModifierList();
      if (modifierList != null) {
        PsiAnnotation annotation = modifierList.findAnnotation(getAnnotationName(capture));
        if (annotation != null) {
          PsiAnnotationMemberValue keyExpressionValue = annotation.findAttributeValue("keyExpression");
          String keyExpression = keyExpressionValue != null ? StringUtil.unquoteString(keyExpressionValue.getText()) : null;
          if (StringUtil.isEmpty(keyExpression)) {
            keyExpression = defaultExpression;
          }
          if (capture) {
            capturePoint.myCaptureKeyExpression = keyExpression;
          }
          else {
            capturePoint.myInsertKeyExpression = keyExpression;
          }

          PsiAnnotationMemberValue groupValue = annotation.findAttributeValue("group");
          String group = groupValue != null ? StringUtil.unquoteString(groupValue.getText()) : null;
          if (!StringUtil.isEmpty(group)) {
            for (CapturePoint capturePointsFromAnnotation : capturePointsFromAnnotations) {
              if (StringUtil.startsWith(group, capturePointsFromAnnotation.myClassName) &&
                  StringUtil.endsWith(group, capturePointsFromAnnotation.myMethodName)) {
                capturePointsFromAnnotation.myInsertClassName = capturePoint.myInsertClassName;
                capturePointsFromAnnotation.myInsertMethodName = capturePoint.myInsertMethodName;
                capturePointsFromAnnotation.myInsertKeyExpression = capturePoint.myInsertKeyExpression;
                return;
              }
            }
          }
        }
      }

      capturePointsFromAnnotations.add(capturePoint);
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

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    @Override
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

    @Override
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
    myTableModel.scanPoints();
    myTableModel.fireTableDataChanged();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return DebuggerBundle.message("async.stacktraces.configurable.display.name");
  }

  static void processCaptureAnnotations(BiConsumer<Boolean, PsiModifierListOwner> consumer) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    scanPointsInt(true, consumer);
    scanPointsInt(false, consumer);
  }

  private static void scanPointsInt(boolean capture, BiConsumer<Boolean, PsiModifierListOwner> consumer) {
    try {
      String annotationName = getAnnotationName(capture);
      Project project = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
      GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
      PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(annotationName, allScope);
      if (annotationClass != null) {
        AnnotatedElementsSearch.searchElements(annotationClass, allScope, PsiMethod.class, PsiParameter.class)
          .forEach(e -> {
            consumer.accept(capture, e);
          });
      }
    }
    catch (IndexNotReadyException ignore) {
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  static String getAnnotationName(boolean capture) {
    return (capture ? Debugger.Capture.class : Debugger.Insert.class).getName().replace("$", ".");
  }
}
