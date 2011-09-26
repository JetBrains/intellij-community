/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.errorTreeView.impl;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.NavigatableMessageElement;
import com.intellij.util.SystemProperties;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TooManyListenersException;

public class ErrorViewTextExporter implements ExporterToTextFile {
  private final JCheckBox myCbShowDetails;
  private final ErrorViewStructure myStructure;
  private ChangeListener myChangeListener;

  public ErrorViewTextExporter(ErrorViewStructure treeStructure) {
    myStructure = treeStructure;
    myCbShowDetails = new JCheckBox(IdeBundle.message("checkbox.errortree.export.details"));
    myCbShowDetails.setSelected(true);
    myCbShowDetails.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myChangeListener.stateChanged(null);
      }
    });
  }

  public JComponent getSettingsEditor() {
    return myCbShowDetails;
  }

  public void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {
    if (myChangeListener != null) throw new TooManyListenersException();
    myChangeListener = listener;
  }

  public void removeSettingsChangedListener(ChangeListener listener) {
    myChangeListener = null;
  }

  public String getReportText() {
    StringBuffer buffer = new StringBuffer();
    getReportText(buffer, (ErrorTreeElement)myStructure.getRootElement(), myCbShowDetails.isSelected(), 0);
    return buffer.toString();
  }

  public String getDefaultFilePath() {
    return "";
  }

  public void exportedTo(String filePath) {
  }

  public boolean canExport() {
    return true;
  }

  private void getReportText(StringBuffer buffer, final ErrorTreeElement element, boolean withUsages, final int indent) {
    final String newline = SystemProperties.getLineSeparator();
    Object[] children = myStructure.getChildElements(element);
    for (final Object child : children) {
      if (!(child instanceof ErrorTreeElement)) {
        continue;
      }
      if (!withUsages && child instanceof NavigatableMessageElement) {
        continue;
      }
      final ErrorTreeElement childElement = (ErrorTreeElement)child;
      if (buffer.length() > 0) {
        buffer.append(newline);
      }
      shift(buffer, indent);
      exportElement(childElement, buffer, indent, newline);
      getReportText(buffer, childElement, withUsages, indent + 4);
    }
  }

  public static void exportElement(ErrorTreeElement element, final StringBuffer buffer, int baseIntent, final String newline) {
    final int startLength = buffer.length();
    buffer.append(element.getKind().getPresentableText());
    buffer.append(element.getExportTextPrefix());
    final int localIndent = startLength - buffer.length();

    final String[] text = element.getText();
    if (text != null && text.length > 0) {
      buffer.append(text[0]);
      for (int i = 1; i < text.length; i++) {
        buffer.append(newline);
        shift(buffer, baseIntent + localIndent);
        buffer.append(text[i]);
      }
    }
  }

  private static void shift(StringBuffer buffer, int indent) {
    for(int i=0; i<indent; i++) {
      buffer.append(' ');
    }
  }
}
