package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

/**
 * @author ksafonov
 */
public class AttachmentsTabForm {

  private static final Logger LOG = Logger.getInstance(AttachmentsTabForm.class);

  private JPanel myContentPane;
  private TableView<Attachment> myTable;
  private LabeledTextComponent myFileTextArea;
  private final EventDispatcher<ChangeListener> myInclusionEventDispatcher = EventDispatcher.create(ChangeListener.class);

  private final ColumnInfo<Attachment, Boolean> ENABLED_COLUMN =
    new ColumnInfo<Attachment, Boolean>(DiagnosticBundle.message("error.dialog.attachment.include.column.title")) {
      @Override
      public Boolean valueOf(Attachment attachment) {
        return attachment.isIncluded();
      }

      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public int getWidth(JTable table) {
        return 50;
      }

      @Override
      public boolean isCellEditable(Attachment attachment) {
        return true;
      }

      @Override
      public void setValue(Attachment attachment, Boolean value) {
        attachment.setIncluded(value);
        myInclusionEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(attachment));
      }
    };

  private static final ColumnInfo<Attachment, String> PATH_COLUMN =
    new ColumnInfo<Attachment, String>(DiagnosticBundle.message("error.dialog.attachment.path.column.title")) {
      @Override
      public String valueOf(Attachment attachment) {
        return attachment.getPath();
      }
    };

  public AttachmentsTabForm() {
    myFileTextArea.getTextComponent().setEditable(false);
    myFileTextArea.setTitle(DiagnosticBundle.message("error.dialog.filecontent.title"));
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        LabeledTextComponent.setText(myFileTextArea.getTextComponent(), null, true);
        Attachment attachment = myTable.getSelectedObject();
        if (attachment != null) {
          try {
            LabeledTextComponent.setText(myFileTextArea.getTextComponent(), attachment.getDisplayText(), true);
          }
          catch (Throwable th) {
            LOG.warn(th);
          }
        }
      }
    });
    myTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int[] selectedRows = myTable.getSelectedRows();
        boolean aggregateValue = true;
        for (final int selectedRow : selectedRows) {
          if (selectedRow < 0 || !myTable.isCellEditable(selectedRow, 0)) {
            return;
          }
          final Boolean value = (Boolean)myTable.getValueAt(selectedRow, 0);
          aggregateValue &= value == null || value.booleanValue();
        }
        for (int selectedRow : selectedRows) {
          myTable.setValueAt(aggregateValue ? Boolean.FALSE : Boolean.TRUE, selectedRow, 0);
        }
        myTable.repaint();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  public void setAttachments(List<Attachment> attachments) {
    myTable.setModelAndUpdateColumns(new ListTableModel<>(new ColumnInfo[]{ENABLED_COLUMN, PATH_COLUMN}, attachments, 1));
    myTable.setBorder(IdeBorderFactory.createBorder());
    myTable.setSelection(Collections.singletonList(attachments.get(0)));
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public void addInclusionListener(ChangeListener listener) {
    myInclusionEventDispatcher.addListener(listener);
  }

  public void selectFirstIncludedAttachment() {
    final List items = ((ListTableModel)myTable.getModel()).getItems();
    for (Object item : items) {
      if (((Attachment)item).isIncluded()) {
        myTable.setSelection(Collections.singleton((Attachment)item));
        break;
      }
    }
  }
}
