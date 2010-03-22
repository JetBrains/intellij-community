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

package com.intellij.ide.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class PasteReferenceProvider implements PasteProvider {
  public void performPaste(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (project == null || editor == null) return;

    final String fqn = getCopiedFqn();

    QualifiedNameProvider theProvider = null;
    PsiElement element = null;
    for(QualifiedNameProvider provider: Extensions.getExtensions(QualifiedNameProvider.EP_NAME)) {
      element = provider.qualifiedNameToElement(fqn, project);
      if (element != null) {
        theProvider = provider;
        break;
      }
    }

    if (theProvider != null) {
      insert(fqn, element, editor, theProvider);
    }
  }

  public boolean isPastePossible(DataContext dataContext) {
    return isPasteEnabled(dataContext);
  }

  public boolean isPasteEnabled(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    return project != null && editor != null && getCopiedFqn() != null;
  }

  private static void insert(final String fqn, final PsiElement element, final Editor editor, final QualifiedNameProvider provider) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(editor.getProject());
    documentManager.commitDocument(editor.getDocument());
    final PsiFile file = documentManager.getPsiFile(editor.getDocument());
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final Project project = editor.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Document document = editor.getDocument();
            documentManager.doPostponedOperationsAndUnblockDocument(document);
            documentManager.commitDocument(document);
            EditorModificationUtil.deleteSelectedText(editor);
            provider.insertQualifiedName(fqn, element, editor, project);
          }
        });
      }
    }, IdeBundle.message("command.pasting.reference"), null);
  }

  @Nullable
  private static String getCopiedFqn() {
    final Transferable contents = CopyPasteManager.getInstance().getContents();
    if (contents == null) return null;
    try {
      final DataFlavor flavor = CopyReferenceAction.getFlavor();
      if (flavor != null) {
        return (String)contents.getTransferData(flavor);
      }
    }
    catch (UnsupportedFlavorException e) {
      // ignore
    }
    catch (IOException e) {
      // ignore
    }
    catch (NoClassDefFoundError e) {
      // ignore
    }
    return null;
  }
}
