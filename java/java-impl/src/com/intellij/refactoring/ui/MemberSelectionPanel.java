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

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MemberSelectionPanel extends AbstractMemberSelectionPanel<PsiMember, MemberInfo> {
  private final MemberSelectionTable myTable;

  /**
   * @param title if title contains 'm' - it would look and feel as mnemonic
   */
  public MemberSelectionPanel(@NlsContexts.Separator String title, List<MemberInfo> memberInfo, @NlsContexts.ColumnName String abstractColumnHeader) {
    super();
    setLayout(new BorderLayout());

    myTable = createMemberSelectionTable(memberInfo, abstractColumnHeader);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    add(SeparatorFactory.createSeparator(title, myTable), BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);
  }

  protected MemberSelectionTable createMemberSelectionTable(List<MemberInfo> memberInfo, @NlsContexts.ColumnName String abstractColumnHeader) {
    return new MemberSelectionTable(memberInfo, abstractColumnHeader);
  }

  @Override
  public MemberSelectionTable getTable() {
    return myTable;
  }
}
