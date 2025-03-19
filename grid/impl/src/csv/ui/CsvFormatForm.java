package com.intellij.database.csv.ui;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatEditor;
import com.intellij.database.csv.CsvRecordFormat;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.EventListener;

public class CsvFormatForm implements Disposable, CsvFormatEditor {
  private JPanel myPanel;
  private JBCheckBox myHeaderFormatCheckBox;

  @SuppressWarnings("unused") private JPanel myRecordFormatPanel;
  @SuppressWarnings("unused") private JPanel myHeaderFormatPanel;
  private JBCheckBox myRowNumbersCheckBox;
  private JPanel myHeaderFormatWithTitlePanel;

  private String myFormatName;
  private CsvRecordFormatForm myRecordFormatForm;
  private CsvRecordFormatForm myHeaderFormatForm;

  private boolean myResetting;
  private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);
  private CsvFormat myFormat;

  public CsvFormatForm(@NotNull Disposable parent, @NotNull CsvFormatUISettings settings) {
    Disposer.register(parent, this);

    myHeaderFormatCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        boolean headerEnabled = myHeaderFormatCheckBox.isSelected();
        myHeaderFormatWithTitlePanel.setVisible(headerEnabled && settings.isHeaderSettingsVisible());
        if (headerEnabled) {
          myHeaderFormatForm.reset(myRecordFormatForm.getFormat());
        }
        else {
          fireFormatChangedEvent();
        }
      }
    });
    myRowNumbersCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        fireFormatChangedEvent();
      }
    });

    CsvRecordFormatForm.ChangeListener changeEventPropagator = new CsvRecordFormatForm.ChangeListener() {
      @Override
      public void recordFormatChanged(@NotNull CsvRecordFormatForm source) {
        fireFormatChangedEvent();
      }
    };
    myHeaderFormatForm.addChangeListener(changeEventPropagator);
    myRecordFormatForm.addChangeListener(changeEventPropagator);

    myHeaderFormatWithTitlePanel.setVisible(false);
  }

  private void createUIComponents() {
    myRecordFormatForm = new CsvRecordFormatForm(this);
    myHeaderFormatForm = new CsvRecordFormatForm(this);
    myRecordFormatPanel = myRecordFormatForm.getMainPanel();
    myHeaderFormatPanel = myHeaderFormatForm.getMainPanel();
  }

  public void reset(@NotNull CsvFormat format) {
    myFormat = format;
    myResetting = true;
    try {
      myFormatName = format.name;
      myRecordFormatForm.reset(format.dataRecord);
      setHeaderFormat(format.headerRecord);
      myRowNumbersCheckBox.setSelected(format.rowNumbers);
    }
    finally {
      myResetting = false;
      fireFormatChangedEvent();
    }
  }

  public @NotNull CsvFormat getFormat() {
    CsvRecordFormat dataFormat = myRecordFormatForm.getFormat();
    CsvRecordFormat headerFormat = myHeaderFormatCheckBox.isSelected() ? myHeaderFormatForm.getFormat() : null;
    return new CsvFormat(myFormatName, dataFormat, headerFormat, myFormat.id, myRowNumbersCheckBox.isSelected());
  }

  public void addChangeListener(@NotNull ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    myEventDispatcher.addListener(listener, parent);
  }

  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }

  private void fireFormatChangedEvent() {
    if (!myResetting) {
      myEventDispatcher.getMulticaster().formatChanged(this);
    }
  }

  private void setHeaderFormat(@Nullable CsvRecordFormat headerFormat) {
    myHeaderFormatCheckBox.setSelected(headerFormat != null);
    if (headerFormat != null) {
      myHeaderFormatForm.reset(headerFormat);
    }
  }

  @Override
  public boolean firstRowIsHeader() {
    return myHeaderFormatCheckBox.isSelected();
  }

  @Override
  public void setFirstRowIsHeader(boolean value) {
    myHeaderFormatCheckBox.setSelected(value);
  }

  public interface ChangeListener extends EventListener {
    void formatChanged(@NotNull CsvFormatForm source);
  }
}
