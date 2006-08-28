/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * User: anna
 * Date: 16-Dec-2005
 */
public abstract class FilterComponent extends JPanel {
  private final TextFieldWithStoredHistory myFilter;
  private final Alarm myUpdateAlarm = new Alarm();

  public FilterComponent(@NonNls String propertyName, int historySize) {
    super(new BorderLayout());
    myFilter = new TextFieldWithStoredHistory(propertyName);
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          myFilter.addCurrentTextToHistory();
          filter();
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && getFilter().length() > 0){
          myFilter.setText("");
          e.consume();
        }
      }
    });

    myFilter.addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        onChange();
      }

      public void removeUpdate(DocumentEvent e) {
        onChange();
      }

      public void changedUpdate(DocumentEvent e) {
        onChange();
      }

      public void onChange() {
        myUpdateAlarm.cancelAllRequests();
        myUpdateAlarm.addRequest(new Runnable(){
          public void run() {
            onlineFilter();
          }
        }, 100, ModalityState.stateForComponent(myFilter));
      }
    });

    myFilter.setHistorySize(historySize);
    add(myFilter, BorderLayout.CENTER);
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction(InspectionsBundle.message("inspection.tools.action.filter"), InspectionsBundle.message("inspection.tools.action.filter"), IconLoader.getIcon("/ant/filter.png")) {
      public void actionPerformed(AnActionEvent e) {
        myFilter.addCurrentTextToHistory();
        filter();
      }
    });
  }

  public void setHistorySize(int historySize){
    myFilter.setHistorySize(historySize);
  }

  public void reset(){
    myFilter.reset();
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


  public boolean requestFocusInWindow() {
    return myFilter.requestFocusInWindow();
  }

  public abstract void filter();

  protected void onlineFilter(){
    filter();
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
  }
}
