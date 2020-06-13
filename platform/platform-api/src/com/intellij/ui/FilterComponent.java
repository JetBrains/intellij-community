// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.util.Alarm;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public abstract class FilterComponent extends JPanel {
  private final SearchTextField myFilter;
  private final Alarm myUpdateAlarm = new Alarm();
  private final boolean myOnTheFly;

  public FilterComponent(@NonNls String propertyName, int historySize) {
    this(propertyName, historySize, true);
  }

  public FilterComponent(@NonNls String propertyName, int historySize, boolean onTheFlyUpdate) {
    super(new BorderLayout());
    myOnTheFly = onTheFlyUpdate;
    myFilter = new SearchTextField(propertyName) {
      @Override
      protected Runnable createItemChosenCallback(JList list) {
        final Runnable callback = super.createItemChosenCallback(list);
        return () -> {
          callback.run();
          filter();
        };
      }

      @Override
      protected Component getPopupLocationComponent() {
        return FilterComponent.this.getPopupLocationComponent();
      }
    };
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      @Override
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          userTriggeredFilter();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          onEscape(e);
        }
      }
    });

    myFilter.addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onChange();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onChange();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onChange();
      }
    });

    myFilter.setHistorySize(historySize);
    AccessibleContextUtil.setName(myFilter.getTextEditor(), "Message text filter");
    add(myFilter, BorderLayout.CENTER);
  }

  protected JComponent getPopupLocationComponent() {
    return myFilter;
  }

  public JTextField getTextEditor() {
    return myFilter.getTextEditor();
  }

  private void onChange() {
    if (myOnTheFly) {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm.addRequest(() -> onlineFilter(), 100, ModalityState.stateForComponent(myFilter));
    }
  }

  public void setHistorySize(int historySize){
    myFilter.setHistorySize(historySize);
  }

  public void reset(){
    myFilter.reset();
  }

  protected void onEscape(@NotNull KeyEvent e) {
  }

  public String getFilter(){
    return myFilter.getText();
  }

  public void setSelectedItem(final String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void setFilter(final String filter){
    myFilter.setText(filter);
  }

  public void selectText(){
    myFilter.selectText();
  }

  @Override
  public boolean requestFocusInWindow() {
    return myFilter.requestFocusInWindow();
  }

  public abstract void filter();

  protected void onlineFilter(){
    filter();
  }

  protected void userTriggeredFilter() {
    myFilter.addCurrentTextToHistory();
    filter();
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
  }

  protected void setHistory(List<String> strings) {
    myFilter.setHistory(strings);
  }
}
