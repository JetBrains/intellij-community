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
package com.intellij.projectView;

import com.intellij.ide.commander.CommanderPanel;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.FileStructureDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;

import javax.swing.*;

public class FileStructureDialogTest extends BaseProjectViewTestCase {
  public void testFileStructureForClass() {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(getPackageDirectory());
    assertNotNull(aPackage);
    final PsiClass psiClass = aPackage.getClasses()[0];
    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    assertNotNull(virtualFile);
    final StructureViewBuilder structureViewBuilder =
      StructureViewBuilder.PROVIDER.getStructureViewBuilder(virtualFile.getFileType(), virtualFile, myProject);
    assertNotNull(structureViewBuilder);
    final StructureViewModel structureViewModel = ((TreeBasedStructureViewBuilder)structureViewBuilder).createStructureViewModel(null);

    final EditorFactory factory = EditorFactory.getInstance();
    assertNotNull(factory);
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);

    final Editor editor = factory.createEditor(document, myProject);
    try {
      final FileStructureDialog dialog =
        new FileStructureDialog(structureViewModel, editor, myProject, psiClass, new Disposable() {
          @Override
          public void dispose() {
            structureViewModel.dispose();
          }
        }, true);
      try {
        final CommanderPanel panel = dialog.getPanel();
        assertListsEqual((ListModel)panel.getModel(), "Inner1\n" + "Inner2\n" + "__method(): void\n" + "_myField1: int\n" + "_myField2: String\n");
      }
      finally {
        dialog.close(0);
      }
    }
    finally {
      factory.releaseEditor(editor);
    }
  }
}
