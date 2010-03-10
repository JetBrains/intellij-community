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

import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.ide.commander.CommanderPanel;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.FileStructureDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;

public class FileStructureDialogTest extends BaseProjectViewTestCase {
  public void testFileStructureForClass() throws Exception {
    final PsiClass psiClass = JavaDirectoryService.getInstance().getPackage(getPackageDirectory()).getClasses()[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final StructureViewBuilder structureViewBuilder =
      StructureViewBuilder.PROVIDER.getStructureViewBuilder(virtualFile.getFileType(), virtualFile, myProject);
    final StructureViewModel structureViewModel = ((TreeBasedStructureViewBuilder)structureViewBuilder).createStructureViewModel();

    Editor editor = null;
    FileStructureDialog dialog = null;
    try {
      editor = EditorFactory.getInstance().createEditor(FileDocumentManager.getInstance().getDocument(virtualFile));
      dialog = ViewStructureAction.createStructureViewBasedDialog(structureViewModel, editor, myProject, psiClass, new Disposable() {
        public void dispose() {
          structureViewModel.dispose();
        }
      });
      final CommanderPanel panel = dialog.getPanel();
      assertListsEqual(panel.getModel(), "Inner1\n" + "Inner2\n" + "__method():void\n" + "_myField1:int\n" + "_myField2:String\n");
    }
    finally {
      if (dialog != null) dialog.close(0);
      if (editor != null) EditorFactory.getInstance().releaseEditor(editor);
    }
  }
}
