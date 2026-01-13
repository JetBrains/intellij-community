// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.cef.network.CefRequest.CefUrlRequestFlags;
import org.jetbrains.annotations.ApiStatus;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

@SuppressWarnings("ALL")
@ApiStatus.Internal
public class  UrlRequestDialog extends JDialog {
  private final List<JRadioButton> requestMethods = new ArrayList<>();
  private final Map<JCheckBox, Integer> requestFlags = new HashMap<>();
  private final TableModel headerTblModel = new TableModel(true);
  private final TableModel postDataModel = new TableModel(false);
  private final JTextField urlField;
  private final JTextField cookieUrl;
  private final Frame owner_;

  private CefRequest createRequest() {
    String url = urlField.getText();
    if (url.isEmpty() || url.trim().equalsIgnoreCase("http://")) {
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(owner_,
                                                                     "Please specify at least an URL. Otherwise the CefRequest is invalid"));
      return null;
    }

    CefRequest request = CefRequest.create();
    if (request == null) return null;

    String firstPartyForCookie = cookieUrl.getText();
    if (firstPartyForCookie.isEmpty() || firstPartyForCookie.trim().equalsIgnoreCase("http://")) {
      firstPartyForCookie = url;
    }

    String method = "GET";
    for (int i = 0; i < requestMethods.size(); i++) {
      JRadioButton button = requestMethods.get(i);
      if (button.isSelected()) {
        method = button.getText();
        break;
      }
    }

    CefPostData postData = null;
    int postDataRows = postDataModel.getRowCount();
    if (postDataRows > 0) {
      postData = CefPostData.create();
    }
    else if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
        owner_, "The methods POST and PUT require at least one row of data."));
      return null;
    }

    if (postData != null) {
      for (int i = 0; i < postDataRows; i++) {
        String value = (String)postDataModel.getValueAt(i, 0);
        if (value.trim().isEmpty()) continue;

        CefPostDataElement elem = CefPostDataElement.create();
        if (elem != null) {
          File f = new File(value);
          if (f.isFile()) {
            elem.setToFile(value);
          }
          else {
            byte[] byteStr = value.getBytes();
            elem.setToBytes(byteStr.length, byteStr);
          }
          postData.addElement(elem);
        }
      }
    }

    Map<String, String> headerMap = null;
    int headerRows = headerTblModel.getRowCount();
    if (headerRows > 0) {
      headerMap = new HashMap<>();
      for (int i = 0; i < headerRows; i++) {
        String key = (String)headerTblModel.getValueAt(i, 0);
        String value = (String)headerTblModel.getValueAt(i, 1);
        if (key.trim().isEmpty()) continue;

        headerMap.put(key, value);
      }
    }

    int flags = 0;
    Set<Entry<JCheckBox, Integer>> entrySet = requestFlags.entrySet();
    for (Entry<JCheckBox, Integer> entry : entrySet) {
      if (entry.getKey().isSelected()) {
        flags |= entry.getValue();
      }
    }

    request.set(url, method, postData, headerMap);
    request.setFirstPartyForCookies(firstPartyForCookie);
    request.setFlags(flags);
    return request;
  }

  public UrlRequestDialog(Frame owner, String title) {
    super(owner, title, false);
    setSize(1200, 600);
    setLayout(new BorderLayout());

    owner_ = owner;

    // URL for the request
    urlField = new JTextField("http://");
    JPanel urlPanel = createPanelWithTitle("Request URL", 1, 0);
    urlPanel.add(urlField);

    // URL for the cookies
    cookieUrl = new JTextField("http://");
    JPanel cookiePanel = createPanelWithTitle("Cookie-URL", 1, 0);
    cookiePanel.add(cookieUrl);

    // Radio buttons for the request method
    ButtonGroup requestModeGrp = new ButtonGroup();
    JPanel requestModePanel = createPanelWithTitle("HTTP Request Mode", 1, 0);
    addRequestMode(requestModePanel, requestModeGrp, "GET", true);
    addRequestMode(requestModePanel, requestModeGrp, "HEAD", false);
    addRequestMode(requestModePanel, requestModeGrp, "POST", false);
    addRequestMode(requestModePanel, requestModeGrp, "PUT", false);
    addRequestMode(requestModePanel, requestModeGrp, "DELETE", false);

    // Checkboxes for the flags
    JPanel flagsPanel = createPanelWithTitle("Flags", 0, 1);
    addRequestFlag(flagsPanel, "Skip cache", CefUrlRequestFlags.UR_FLAG_SKIP_CACHE,
                   "If set the cache will be skipped when handling the request", false);
    addRequestFlag(flagsPanel, "Allow cached credentials",
                   CefUrlRequestFlags.UR_FLAG_ALLOW_CACHED_CREDENTIALS,
                   "If set user name, password, and cookies may be sent with the request, "
                   + "and cookies may be saved from the response.",
                   false);
    addRequestFlag(flagsPanel, "Report Upload Progress",
                   CefUrlRequestFlags.UR_FLAG_REPORT_UPLOAD_PROGRESS,
                   "If set upload progress events will be generated when a request has a body", false);
    addRequestFlag(flagsPanel, "Report RawHeaders",
                   CefUrlRequestFlags.UR_FLAG_REPORT_RAW_HEADERS,
                   "If set the headers sent and received for the request will be recorded", false);
    addRequestFlag(flagsPanel, "No download data", CefUrlRequestFlags.UR_FLAG_NO_DOWNLOAD_DATA,
                   "If set the CefURLRequestClient.onDownloadData method will not be called", false);
    addRequestFlag(flagsPanel, "No retry on 5xx", CefUrlRequestFlags.UR_FLAG_NO_RETRY_ON_5XX,
                   "If set 5XX redirect errors will be propagated to the observer instead of automatically re-tried.",
                   false);

    // Table for header values
    JPanel headerValues = createPanelWithTable("Header Values", headerTblModel);
    headerTblModel.addEntry("User-Agent", "Mozilla/5.0 JCEF Example Agent");
    headerTblModel.addEntry("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");

    // Table for post-data
    JPanel postData = createPanelWithTable("Post Data", postDataModel);

    // Create view
    JPanel headerPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0.5;
    c.gridx = 0;
    c.gridy = 0;
    headerPanel.add(urlPanel, c);
    c.gridx = 1;
    headerPanel.add(cookiePanel, c);
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 2;
    c.weightx = 0.0;
    headerPanel.add(requestModePanel, c);

    JPanel centerPanel = new JPanel(new GridLayout(2, 0));
    centerPanel.add(headerValues);
    centerPanel.add(postData);

    JButton abortButton = new JButton("Abort");
    abortButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
        dispose();
      }
    });

    JButton sendButton = new JButton("Send");
    sendButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CefRequest request = createRequest();
        if (request == null) return;

        UrlRequestDialogReply handleRequest =
          new UrlRequestDialogReply(owner_, getTitle() + " - Result");
        handleRequest.send(request);
        handleRequest.setVisible(true);
        setVisible(false);
        dispose();
      }
    });

    JPanel bottomPanel = new JPanel(new GridLayout(0, 2));
    bottomPanel.add(abortButton);
    bottomPanel.add(sendButton);

    add(headerPanel, BorderLayout.PAGE_START);
    add(flagsPanel, BorderLayout.LINE_START);
    add(centerPanel, BorderLayout.CENTER);
    add(bottomPanel, BorderLayout.PAGE_END);
  }

  private void addRequestMode(
    JPanel panel, ButtonGroup buttonGrp, String requestMode, boolean selected) {
    JRadioButton button = new JRadioButton(requestMode, selected);
    buttonGrp.add(button);
    panel.add(button);
    requestMethods.add(button);
  }

  private void addRequestFlag(
    JPanel panel, String flag, int value, String tooltip, boolean selected) {
    JCheckBox checkBox = new JCheckBox(flag, selected);
    checkBox.setToolTipText(tooltip);
    panel.add(checkBox);
    requestFlags.put(checkBox, value);
  }

  private static JPanel createPanelWithTitle(String title, int rows, int cols) {
    JPanel result = new JPanel(new GridLayout(rows, cols));
    result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                                                        BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    return result;
  }

  private static JPanel createPanelWithTable(String title, TableModel tblModel) {
    final TableModel localTblModel = tblModel;
    JPanel result = new JPanel(new BorderLayout());
    result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                                                        BorderFactory.createEmptyBorder(10, 10, 10, 10)));

    JTable table = new JTable(tblModel);
    table.setFillsViewportHeight(true);
    JScrollPane scrollPane = new JScrollPane(table);

    JPanel buttonPane = new JPanel(new GridLayout(0, 2));
    JButton addButton = new JButton("Add entry");
    addButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        localTblModel.newDefaultEntry();
      }
    });
    buttonPane.add(addButton);

    JButton delButton = new JButton("Remove entry");
    delButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        localTblModel.removeSelected();
      }
    });
    buttonPane.add(delButton);

    result.add(scrollPane, BorderLayout.CENTER);
    result.add(buttonPane, BorderLayout.PAGE_END);

    return result;
  }

  private static class TableModel extends AbstractTableModel {
    private final String[] columnNames;
    private final List<Object[]> rowData = new ArrayList<>();
    private final boolean hasKeyColumn_;

    TableModel(boolean hasKeyColumn) {
      super();
      hasKeyColumn_ = hasKeyColumn;
      if (hasKeyColumn) {
        columnNames = new String[]{"Key", "Value", ""};
      }
      else {
        columnNames = new String[]{"Value", ""};
      }
    }

    public void newDefaultEntry() {
      int row = rowData.size();
      Object[] rowEntry;
      if (hasKeyColumn_) {
        rowEntry = new Object[]{"key", "value", Boolean.valueOf(false)};
      }
      else {
        rowEntry = new Object[]{"value", Boolean.valueOf(false)};
      }
      rowData.add(rowEntry);
      fireTableRowsInserted(row, row);
    }

    public void removeSelected() {
      int idx = hasKeyColumn_ ? 2 : 1;
      for (int i = 0; i < rowData.size(); ++i) {
        if ((Boolean)rowData.get(i)[idx]) {
          rowData.remove(i);
          fireTableRowsDeleted(i, i);
          i--;
        }
      }
    }

    public void addEntry(String key, String value) {
      int row = rowData.size();
      Object[] rowEntry;
      if (hasKeyColumn_) {
        rowEntry = new Object[]{key, value, Boolean.valueOf(false)};
      }
      else {
        rowEntry = new Object[]{value, Boolean.valueOf(false)};
      }
      rowData.add(rowEntry);
      fireTableRowsInserted(row, row);
    }

    @Override
    public int getRowCount() {
      return rowData.size();
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (!rowData.isEmpty()) return rowData.getFirst()[columnIndex].getClass();
      return Object.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return rowData.get(rowIndex)[columnIndex];
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      rowData.get(rowIndex)[columnIndex] = aValue;
      fireTableCellUpdated(rowIndex, columnIndex);
    }
  }
}
