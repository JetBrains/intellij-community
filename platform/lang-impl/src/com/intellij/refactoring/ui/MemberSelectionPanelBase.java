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
package com.intellij.refactoring.ui;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;

import javax.swing.*;
import java.awt.*;

/**
 * @author Max Medvedev
 */
public class MemberSelectionPanelBase<Member extends PsiElement,
                                      MemberInfo extends MemberInfoBase<Member>,
                                      Table extends AbstractMemberSelectionTable<Member, MemberInfo>> extends AbstractMemberSelectionPanel<Member, MemberInfo> {
  private final Table myTable;

  /**
   * @param title if title contains 'm' - it would look and feel as mnemonic
   */
  public MemberSelectionPanelBase(String title, Table table) {
    super();
    setLayout(new BorderLayout());

    myTable = table;
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    add(SeparatorFactory.createSeparator(title, myTable), BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);
  }

  public Table getTable() {
    return myTable;
  }
}

