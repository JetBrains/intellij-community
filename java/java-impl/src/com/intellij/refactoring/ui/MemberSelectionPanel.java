/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 13:19:44
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.TitledBorderWithMnemonic;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public class MemberSelectionPanel extends JPanel {
  private final MemberSelectionTable myTable;

  /**
   * @param title if title contains 'm' - it would look and feel as mnemonic
   */
  public MemberSelectionPanel(String title, List<MemberInfo> memberInfo, String abstractColumnHeader) {
    super();
    TitledBorderWithMnemonic titledBorder = new TitledBorderWithMnemonic(appendTitledBorderMnemonic(title));
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    setBorder(border);
    setLayout(new BorderLayout());

    myTable = createMemberSelectionTable(memberInfo, abstractColumnHeader);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    add(scrollPane, BorderLayout.CENTER);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.ensureSelectionExists(myTable);
        myTable.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_M, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK),
                              JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private static String appendTitledBorderMnemonic(String title) {
    int membersIdx = title.indexOf('M');
    if (membersIdx >= 0) {
      title = title.replaceFirst("M", "&M");
    } else {
      membersIdx = title.indexOf('m');
      if (membersIdx >= 0) {
        title = title.replaceFirst("m", "&m");
      }
    }
    return title;
  }

  protected MemberSelectionTable createMemberSelectionTable(List<MemberInfo> memberInfo, String abstractColumnHeader) {
    return new MemberSelectionTable(memberInfo, abstractColumnHeader);
  }

  public MemberSelectionTable getTable() {
    return myTable;
  }


}
