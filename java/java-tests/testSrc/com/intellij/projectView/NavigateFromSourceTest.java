/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.IdeaTestUtil;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

@SuppressWarnings({"HardCodedStringLiteral"})
public class NavigateFromSourceTest extends BaseProjectViewTestCase {
  public void testShowClassMembers() throws Exception {
    useStandardProviders();
    final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
    Arrays.sort(classes, new Comparator<PsiClass>() {
      public int compare(PsiClass o1, PsiClass o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    PsiClass psiClass = classes[0];

    final AbstractProjectViewPSIPane pane = createPane();
    final PsiFile containingFile = psiClass.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();

    checkNavigateFromSourceBehaviour(psiClass, virtualFile, pane);

    IdeaTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                                 " -PsiDirectory: showClassMembers\n" +
                                                 "  -PsiDirectory: src\n" +
                                                 "   -PsiDirectory: com\n" +
                                                 "    -PsiDirectory: package1\n" +
                                                 "     [Class1]\n" +
                                                 "     Class2\n" +
                                                 getRootFiles() +
                                                 " +External Libraries\n"
                                                 , true);

    changeClassTextAndTryToNavigate("class Class11 {}", (PsiJavaFile)containingFile, pane, "-Project\n" +
                                                                                           " -PsiDirectory: showClassMembers\n" +
                                                                                           "  -PsiDirectory: src\n" +
                                                                                           "   -PsiDirectory: com\n" +
                                                                                           "    -PsiDirectory: package1\n" +
                                                                                           "     [Class11]\n" +
                                                                                           "     Class2\n" +
                                                                                           getRootFiles() +
                                                                                           " +External Libraries\n");

    changeClassTextAndTryToNavigate("class Class1 {}", (PsiJavaFile)containingFile, pane, "-Project\n" +
                                                                                          " -PsiDirectory: showClassMembers\n" +
                                                                                          "  -PsiDirectory: src\n" +
                                                                                          "   -PsiDirectory: com\n" +
                                                                                          "    -PsiDirectory: package1\n" +
                                                                                          "     [Class1]\n" +
                                                                                          "     Class2\n" +
                                                                                          getRootFiles() +
                                                                                          " +External Libraries\n");

    doTestMultipleSelection(pane, ((PsiJavaFile)containingFile).getClasses()[0]);
  }

  private static void doTestMultipleSelection(final AbstractProjectViewPSIPane pane, final PsiClass psiClass) {
    JTree tree = pane.getTree();
    int rowCount = tree.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      tree.addSelectionRow(i);
    }

    pane.select(psiClass, psiClass.getContainingFile().getVirtualFile(), true);

    assertEquals(1, tree.getSelectionCount());
  }

  private void changeClassTextAndTryToNavigate(final String newClassString,
                                               PsiJavaFile psiFile,
                                               final AbstractProjectViewPSIPane pane,
                                               final String expected) throws IOException, InterruptedException {
    PsiClass psiClass = psiFile.getClasses()[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final JTree tree = pane.getTree();
    writeToFile(virtualFile, newClassString.getBytes());

    IdeaTestUtil.waitForAlarm(600);

    pane.select(psiClass, virtualFile, true);
    IdeaTestUtil.assertTreeEqual(tree, expected, true);
  }

  private static void writeToFile(final VirtualFile virtualFile, final byte[] b) throws IOException {
    virtualFile.setBinaryContent(b);
  }


}
