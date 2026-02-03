// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import org.cef.callback.CefCookieVisitor;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;

@ApiStatus.Internal
public class  CookieManagerDialog extends JDialog {
  private static int testCookieId = 1;
  private final CefCookieManager manager;
  private final CookieTableModel tblModel = new CookieTableModel();

  public CookieManagerDialog(Frame owner, String title, CefCookieManager cookieManager) {
    super(owner, title, false);
    setLayout(new BorderLayout());
    setSize(800, 600);
    manager = cookieManager;

    //noinspection UndesirableClassUsage
    JTable cookieTable = new JTable(tblModel);
    cookieTable.setFillsViewportHeight(true);

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
    JButton delButton = new JButton("Delete cookies");
    delButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        tblModel.removeCookies();
      }
    });
    controlPanel.add(delButton);

    JButton testCreateCookie = new JButton("Add test cookie");
    testCreateCookie.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Date now = new Date();
        Date expires = new Date(now.getTime() + 86400000);
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        String name = "testNo" + testCookieId++;
        CefCookie cookie = new CefCookie(name, "testCookie", ".test.cookie", "/", false,
                                         true, now, now, true, expires);
        if (manager.setCookie("http://my.test.cookie", cookie)) {
          tblModel.visit(cookie, 1, 1, new BoolRef());
        }
      }
    });
    controlPanel.add(testCreateCookie);

    JButton doneButton = new JButton("Done");
    doneButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
      }
    });
    controlPanel.add(doneButton);

    add(new JScrollPane(cookieTable));
    add(controlPanel, BorderLayout.SOUTH);

    if (manager == null) throw new NullPointerException("Cookie manager is null");
    manager.visitAllCookies(tblModel);
  }

  private class CookieTableModel extends AbstractTableModel implements CefCookieVisitor {
    private final String[] columnNames;
    private final ArrayList<Object[]> rowData = new ArrayList<>();

    CookieTableModel() {
      super();
      columnNames = new String[]{"Name", "Value", "Domain", "Path", "Secure", "HTTP only",
        "Created", "Last Access", "Expires"};
    }

    // add an entry to the table
    @Override
    public boolean visit(CefCookie cookie, int count, int total, BoolRef delete) {
      Object[] entry = {cookie.name, cookie.value, cookie.domain, cookie.path,
        cookie.secure, cookie.httponly, cookie.creation,
        cookie.lastAccess, cookie.expires};
      int row = rowData.size();
      rowData.add(entry);
      fireTableRowsInserted(row, row);

      return true;
    }

    public void removeCookies() {
      int cnt = rowData.size();
      if (cnt > 0) {
        rowData.clear();
        manager.deleteCookies("", "");
        fireTableRowsDeleted(0, cnt - 1);
      }
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
    public Object getValueAt(int rowIndex, int columnIndex) {
      return rowData.get(rowIndex)[columnIndex];
    }
  }
}
