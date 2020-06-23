// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.IconTestUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

public class JavaIconTest extends LightJavaCodeInsightFixtureTestCase {
  public void testOnlyOneIconForLockedJavaClass() throws IOException {
    PsiFile psiFile = myFixture.configureByText("a.java", "class A {}");
    VirtualFile file = myFixture.getFile().getVirtualFile();
    setFileWritable(file, false);
    UIUtil.dispatchAllInvocationEvents(); // write actions
    UIUtil.dispatchAllInvocationEvents();
    try {
      PsiClass psiClass = ((PsiJavaFile) psiFile).getClasses()[0];
      Icon icon = AbstractPsiBasedNode.patchIcon(getProject(), psiClass.getIcon(Iconable.ICON_FLAG_READ_STATUS), file);
      List<Icon> icons = IconTestUtil.renderDeferredIcon(icon);
      assertOneElement(ContainerUtil.filter(icons, ic -> ic == IconTestUtil.unwrapRetrievableIcon(PlatformIcons.LOCKED_ICON)));
    }
    finally {
      setFileWritable(file, true);
    }
  }

  private void setFileWritable(VirtualFile file, boolean writable) throws IOException {
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (ThrowableComputable<Void, IOException>)() -> {
                                               file.setWritable(writable);
                                               return null;
                                             });
  }
}
