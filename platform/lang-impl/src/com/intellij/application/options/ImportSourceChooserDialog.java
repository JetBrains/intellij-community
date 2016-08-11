/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeImporterEP;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ImportSourceChooserDialog <S extends Scheme> extends DialogWrapper {
  private JPanel myContentPane;
  private JBList mySourceList;

  private String mySelectedSourceName;
  private final ListModel myListModel;
  
  private final static String SHARED_IMPORT_SOURCE = ApplicationBundle.message("import.scheme.shared");
  
  public ImportSourceChooserDialog(JComponent parent, Class<S> schemeClass) {
    super(parent, true);
    setTitle(ApplicationBundle.message("title.import.scheme.from"));
    myListModel = new SourceListModel(SchemeImporterEP.getExtensions(schemeClass));
    initSourceList();
    init();
  }
  
  private void initSourceList() {
    mySourceList.setModel(myListModel);
    mySourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mySourceList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int index = mySourceList.getSelectedIndex();
        if (index >= 0) {
          setSelectedSourceName((String)myListModel.getElementAt(index));
        }
      }
    });
    mySourceList.setSelectedIndex(0);
  }
  
  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }
  
  private void setSelectedSourceName(String name) {
    mySelectedSourceName = name;
  }

  @Nullable
  public String getSelectedSourceName() {
    return mySelectedSourceName;
  }
  
  public boolean isImportFromSharedSelected() {
    return SHARED_IMPORT_SOURCE.equals(mySelectedSourceName);
  }
  
  private class SourceListModel extends DefaultListModel {
    private final List<String> mySourceNames = new ArrayList<>();
    
    public SourceListModel(Collection<SchemeImporterEP<S>> extensions) {
      for (SchemeImporterEP extension : extensions) {
        mySourceNames.add(extension.name);
      }
    }

    @Override
    public int getSize() {
      return mySourceNames.size();
    }

    @Override
    public Object getElementAt(int index) {
      return mySourceNames.get(index);
    }
  }
}
