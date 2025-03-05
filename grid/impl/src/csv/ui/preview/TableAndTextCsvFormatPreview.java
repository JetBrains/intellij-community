package com.intellij.database.csv.ui.preview;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormats;
import com.intellij.database.csv.CsvFormatter;
import com.intellij.database.datagrid.CsvDocumentDataHookUp;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class TableAndTextCsvFormatPreview implements CsvFormatPreview, Disposable {
  private static final String[] DUMMY_HEADER = {"customer_id", "first_name", "last_name", "active", "create_date"};
  private static final String[][] DUMMY_DATA = {
    {"1", "MARY", "SMITH", "true", "2006-02-14"},
    {"2", "PATRICIA", "JOHNSON", "true", "2006-02-14"},
    {"3", "LINDA", "WILLIAMS", "true", "2006-02-14"},
    {"4", "BARBARA", "JONES", "false", "2006-02-14"},
    {"5", "ELIZABETH", "BROWN", "false", "2006-02-14"},
    {"6", "JENNIFER", "DAVIS", "true", "2006-02-14"},
    {"7", "MARIA", "MILLER", "false", "2006-02-14"},
    {"8", "SUSAN", "WILSON", "true", "2006-02-14"},
    {"9", "MARGARET", "MOORE", "true", "2006-02-14"},
    {"10", "DOROTHY", "TAYLOR", "true", "2006-02-14"},
    {"11", "LISA", "ANDERSON", "true", "2006-02-14"},
    {"12", "NANCY", "THOMAS", "true", "2006-02-14"},
    {"13", "KAREN", "JACKSON", "false", "2006-02-14"},
    {"14", "BETTY", "WHITE", "true", "2006-02-14"},
    {"15", "HELEN", "HARRIS", "true", "2006-02-14"},
    {"16", "SANDRA", "MARTIN", "true", "2006-02-14"},
    {"17", "DONNA", "THOMPSON", "true", "2006-02-14"},
    {"18", "CAROL", "GARCIA", "true", "2006-02-14"},
    {"19", "RUTH", "MARTINEZ", "false", "2006-02-14"},
    {"20", "SHARON", "ROBINSON", "false", "2006-02-14"},
  };

  private final JPanel myPanel;
  private final CsvDocumentDataHookUp myHookUp;
  private final TextView myTextView;

  public TableAndTextCsvFormatPreview(@NotNull Project project, @NotNull Disposable parent) {
    Disposer.register(parent, this);

    Document document = EditorFactory.getInstance().createDocument("");
    myHookUp = new CsvDocumentDataHookUp(project, CsvFormats.TSV_FORMAT.getValue(), document, null
    );
    Disposer.register(this, myHookUp);

    DataGrid grid = GridUtil.createCsvPreviewDataGrid(project, myHookUp);
    Disposer.register(this, grid);

    myTextView = new TextView(document, this);

    myPanel = new JPanel(new GridLayout(2, 1, 0, 8));
    myPanel.add(myTextView.getComponent());
    myPanel.add(grid.getPanel().getComponent());
  }

  @Override
  public void setFormat(@NotNull CsvFormat format, @NotNull GridRequestSource source) {
    myTextView.setText("");
    myHookUp.setFormat(format, source);

    myTextView.setText(formatData(myHookUp.getFormat()));
  }

  public static @NotNull String formatData(@NotNull CsvFormat format) {
    CsvFormatter formatter = new CsvFormatter(format);
    StringBuilder sb = new StringBuilder();
    sb.append(formatter.formatHeader(Arrays.asList(DUMMY_HEADER))).append(formatter.recordSeparator());
    for (String[] row : DUMMY_DATA) {
      sb.append(formatter.formatRecord(Arrays.asList(row))).append(formatter.recordSeparator());
    }
    return sb.toString();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }
}
