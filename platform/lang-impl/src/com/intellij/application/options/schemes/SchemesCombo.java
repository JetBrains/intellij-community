/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class SchemesCombo<T extends Scheme> {
  private ComboBox<MySchemeItem> myComboBox;
  private DefaultSchemeActions<T> myActions;

  public SchemesCombo(@NotNull DefaultSchemeActions<T> actions) {
    myActions = actions;
    myComboBox = new ComboBox<>();
    myComboBox.setRenderer(new MyListCellRenderer());
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myActions.onSchemeChanged(getSelectedScheme());
      }
    });
    myComboBox.setModel(new DefaultComboBoxModel<MySchemeItem>() {
      @Override
      public void setSelectedItem(Object anObject) {
        if (anObject instanceof OptionalSeparatorItem && ((OptionalSeparatorItem)anObject).isSeparator()) {
          return;
        }
        super.setSelectedItem(anObject);
      }
    });
  }
  
  private SimpleTextAttributes getSchemeAttributes(@NotNull T scheme) {
    return myActions.isDeleteAvailable(scheme) ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES; 
  }
  
  public void resetSchemes(@NotNull Collection<T> schemes) {
    myComboBox.removeAllItems();
    DefaultSchemeActions.SchemeLevel currSchemeLevel = DefaultSchemeActions.SchemeLevel.IDE_Only;
    for (T scheme : schemes) {
      DefaultSchemeActions.SchemeLevel schemeLevel = myActions.getSchemeLevel(scheme);
      if (!currSchemeLevel.equals(schemeLevel)) {
        currSchemeLevel = schemeLevel;
        if (!schemeLevel.equals(DefaultSchemeActions.SchemeLevel.IDE_Only)) {
          myComboBox.addItem(new MySchemeItem(currSchemeLevel.toString()));
        }
      }
      myComboBox.addItem(new MySchemeItem(scheme));
    }
  }

  private class MyListCellRenderer extends ColoredListCellRenderer<MySchemeItem> {
    private ListCellRendererWrapper<MySchemeItem> myWrapper = new ListCellRendererWrapper<MySchemeItem>() {
      @Override
      public void customize(JList list,
                            MySchemeItem value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        if (value.isSeparator()) {
          setText(" Stored in " + value.getPresentableText());
          setSeparator();
        }
      }
    };

    @Override
    public Component getListCellRendererComponent(JList<? extends MySchemeItem> list,
                                                  MySchemeItem value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value.isSeparator()) {
        Component c = myWrapper.getListCellRendererComponent(list, value, index, selected, hasFocus);
        if (c instanceof TitledSeparator) {
          ((TitledSeparator)c).getLabel().setForeground(JBColor.GRAY);
          return c;
        }
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MySchemeItem> list,
                                         MySchemeItem value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      if (value.getScheme() != null) {
        append(value.getPresentableText(), getSchemeAttributes(value.getScheme()));
        DefaultSchemeActions.SchemeLevel schemeLevel = myActions.getSchemeLevel(value.getScheme());
        if (index == -1 && !DefaultSchemeActions.SchemeLevel.IDE_Only.equals(schemeLevel)) {
          append("  " + schemeLevel.toString(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }
  }
  
  @Nullable
  public T getSelectedScheme() {
    int i = myComboBox.getSelectedIndex();
    return i >= 0 ? myComboBox.getItemAt(i).getScheme() : null;
  }
  
  public void selectScheme(@Nullable T scheme) {
    for (int i = 0; i < myComboBox.getItemCount(); i ++) {
      if (myComboBox.getItemAt(i).getScheme() == scheme) {
        myComboBox.setSelectedIndex(i);
        break;
      }
    }
  }
  
  public ComboBox getComboBox() {
    return myComboBox;
  }

  private interface OptionalSeparatorItem {
    boolean isSeparator();
  }

  private final class MySchemeItem implements OptionalSeparatorItem {
    private @Nullable T myScheme;
    private @Nullable String myText;

    public MySchemeItem(@NotNull String text) {
      myText = text;
    }

    public MySchemeItem(@Nullable T scheme) {
      myScheme = scheme;
    }

    @Nullable
    public String getSchemeName() {
      return myScheme != null ? myScheme.getName() : null;
    }

    @Nullable
    public T getScheme() {
      return myScheme;
    }

    @NotNull
    public String getPresentableText() {
      return myScheme != null ? SchemeManager.getDisplayName(myScheme) : myText != null ? myText : "";
    }

    @Override
    public boolean isSeparator() {
      return myScheme == null;
    }
  }
}
