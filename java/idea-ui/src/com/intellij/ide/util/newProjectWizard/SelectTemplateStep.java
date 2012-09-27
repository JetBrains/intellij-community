/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.util.Condition;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class SelectTemplateStep extends ModuleWizardStep {

  private JPanel myPanel;
  private JBList myTemplatesList;
  private JPanel mySettingsPanel;
  private SearchTextField mySearchField;
  private FactoryMap<WebProjectGenerator, WebProjectGenerator.GeneratorPeer> myPeers = new FactoryMap<WebProjectGenerator, WebProjectGenerator.GeneratorPeer>() {
    @Nullable
    @Override
    protected WebProjectGenerator.GeneratorPeer create(WebProjectGenerator key) {
      return key.createPeer();
    }
  };

  public SelectTemplateStep() {
    final DirectoryProjectGenerator[] extensions = DirectoryProjectGenerator.EP_NAME.getExtensions();
    myTemplatesList.setModel(new CollectionListModel<DirectoryProjectGenerator>(extensions));
    myTemplatesList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(((DirectoryProjectGenerator)value).getName());
      }
    });
    myTemplatesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (mySettingsPanel.getComponentCount() > 0) {
          mySettingsPanel.remove(0);
        }
        DirectoryProjectGenerator generator = getSelectedGenerator();
        if (generator instanceof WebProjectGenerator) {
          WebProjectGenerator.GeneratorPeer peer = myPeers.get(generator);
          mySettingsPanel.add(peer.getComponent(), BorderLayout.NORTH);
        }
        mySettingsPanel.revalidate();
      }
    });
    if (myTemplatesList.getModel().getSize() > 0) {
      myTemplatesList.setSelectedIndex(0);
    }
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final MinusculeMatcher matcher = NameUtil.buildMatcher(mySearchField.getText(), NameUtil.MatchingCaseSensitivity.NONE);
        DirectoryProjectGenerator generator = getSelectedGenerator();
        List<DirectoryProjectGenerator> list = ContainerUtil.filter(extensions, new Condition<DirectoryProjectGenerator>() {
          @Override
          public boolean value(DirectoryProjectGenerator generator) {
            String name = generator.getName();
            String[] words = NameUtil.nameToWords(name);
            for (String word : words) {
              if (matcher.matches(word)) return true;
            }
            return false;
          }
        });
        myTemplatesList.setModel(new CollectionListModel<DirectoryProjectGenerator>(list));
        if (!list.isEmpty()) {
          if (list.contains(generator)) {
            myTemplatesList.setSelectedValue(generator, true);
          }
          else {
            myTemplatesList.setSelectedIndex(0);
          }
        }
      }
    });
  }

  public DirectoryProjectGenerator getSelectedGenerator() {
    return (DirectoryProjectGenerator)myTemplatesList.getSelectedValue();
  }

  public WebProjectGenerator.GeneratorPeer getPeer(DirectoryProjectGenerator generator) {
    return myPeers.get(generator);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchField;
  }

  @Override
  public void updateDataModel() {
  }
}
