package com.intellij.database.csv.ui;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.ui.preview.CsvFormatPreview;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.view.editors.DataGridEditorUtil;
import com.intellij.openapi.Disposable;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class CsvFormatsUI implements Disposable {

  private CsvFormatsListComponent myFormatsList;

  private JPanel myPanel;
  private JPanel myFormatListPanel;

  private JBLabel myFormatsLabel;
  private CsvFormatForm myFormatForm;

  private JBScrollPane myFormatFormScrollPane;

  private CsvFormatPreview myPreview;
  private final CsvFormatUISettings mySettings;

  public CsvFormatsUI(boolean allowNameEditing, @NotNull CsvFormatUISettings settings) {
    mySettings = settings;
    new FormatsListToFormatEditorBond().setListeners();

    //noinspection AbstractMethodCallInConstructor
    ToolbarDecorator decorator = createFormatListDecorator();
    myFormatListPanel.add(DataGridEditorUtil.labeledDecorator(myFormatsLabel, decorator), BorderLayout.CENTER);
    myFormatListPanel.setBorder(
      new EmptyBorder(0, 0, JBUIScale.scale(8), UIUtil.getScrollBarWidth())); //getScrollBarWidth is already scaled

    AnActionButton button = ToolbarDecorator.findEditButton(decorator.getActionsPanel());
    if (button != null) button.setVisible(false);

    myFormatsList.setNameEditingAllowed(allowNameEditing);
    myFormatsList.addChangeListener(new CsvFormatsListComponent.ChangeListener() {
      @Override
      public void formatsChanged(@NotNull CsvFormatsListComponent formatsListComponent) {
        CsvFormat selectedFormat = getSelectedFormat();
        if (selectedFormat == null) return;

        if (myPreview != null) {
          myPreview.setFormat(selectedFormat, new GridRequestSource(null));
        }
      }
    });

    myFormatFormScrollPane.setPreferredSize(myFormatFormScrollPane.getViewport().getView().getPreferredSize());
  }

  public @NotNull CsvFormatForm getFormatForm() {
    return myFormatForm;
  }

  private void createUIComponents() {
    myFormatForm = new CsvFormatForm(this, mySettings);
    myFormatsList = new CsvFormatsListComponent(this);
    myFormatFormScrollPane = (JBScrollPane)ScrollPaneFactory.createScrollPane();
    myFormatFormScrollPane.setBorder(JBUI.Borders.empty());
  }

  public void reset(@NotNull List<CsvFormat> formats, @Nullable String nameToSelect) {
    myFormatsList.reset(formats, nameToSelect);
  }

  public @Nullable CsvFormat select(@Nullable CsvFormat format) {
    List<CsvFormat> formats = new ArrayList<>(getFormats());
    CsvFormat existing = findFormat(format);
    if (format != null && existing == null) {
      return myFormatsList.newFormat(format);
    }
    myFormatsList.reset(formats, existing == null ? null : existing.name);
    return existing;
  }

  public @NotNull List<CsvFormat> getFormats() {
    return myFormatsList.getFormats();
  }

  public @Nullable CsvFormat getSelectedFormat() {
    return myFormatsList.getSelected();
  }

  public void attachPreview(@NotNull CsvFormatPreview preview) {
    myPreview = preview;
  }

  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }

  public @NotNull CsvFormatsListComponent getFormatsListComponent() {
    return myFormatsList;
  }

  public void addFormatChangeListener(@NotNull CsvFormatForm.ChangeListener listener, @NotNull Disposable parent) {
    myFormatForm.addChangeListener(listener, parent);
  }

  protected abstract @NotNull ToolbarDecorator createFormatListDecorator();

  private @Nullable CsvFormat findFormat(@Nullable CsvFormat format) {
    if (format == null) return null;
    for (CsvFormat csvFormat : getFormats()) {
      CsvFormat toCompare = new CsvFormat(csvFormat.name, format.dataRecord, format.headerRecord, csvFormat.id, format.rowNumbers);
      if (csvFormat.equals(toCompare)) return csvFormat;
    }
    return null;
  }

  private class FormatsListToFormatEditorBond implements CsvFormatsListComponent.ChangeListener, CsvFormatForm.ChangeListener {
    private boolean myUpdating;

    void setListeners() {
      myFormatsList.addChangeListener(this);
      myFormatForm.addChangeListener(this);
    }

    @Override
    public void formatsChanged(@NotNull CsvFormatsListComponent formatsListComponent) {
      update(() -> {
        CsvFormat selectedFormat = myFormatsList.getSelected();
        if (selectedFormat == null) {
          myFormatForm.getComponent().setVisible(false);
        }
        else {
          myFormatForm.getComponent().setVisible(true);
          myFormatForm.reset(selectedFormat);
        }
      });
    }

    @Override
    public void formatChanged(@NotNull CsvFormatForm source) {
      update(() -> {
        CsvFormat format = myFormatForm.getFormat();
        myFormatsList.updateSelectedFormat(format);
      });
    }

    private void update(Runnable updater) {
      if (myUpdating) return;

      myUpdating = true;
      try {
        updater.run();
      }
      finally {
        myUpdating = false;
      }
    }
  }
}
