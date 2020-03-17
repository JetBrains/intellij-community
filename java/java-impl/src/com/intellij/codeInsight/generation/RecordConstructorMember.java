// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RecordConstructorMember implements ClassMember {
  private final PsiClass myRecord;
  private final boolean myCompact;

  public RecordConstructorMember(PsiClass aRecord, boolean compact) {
    myRecord = aRecord;
    myCompact = compact;
  }
  
  @Override
  public MemberChooserObject getParentNodeDelegate() {
    final String text = PsiFormatUtil.formatClass(myRecord, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
    return new PsiDocCommentOwnerMemberChooserObject(myRecord, text, myRecord.getIcon(0));
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    
  }

  public boolean isCompact() {
    return myCompact;
  }

  @NotNull
  @Override
  public String getText() {
    return myCompact ? "Compact constructor" : "Canonical constructor";
  }
}
