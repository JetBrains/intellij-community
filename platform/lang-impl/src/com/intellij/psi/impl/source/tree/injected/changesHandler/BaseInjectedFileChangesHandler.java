// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected.changesHandler;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.InjectedFileChangesHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

public abstract class BaseInjectedFileChangesHandler implements InjectedFileChangesHandler {

  protected final Editor myEditor;
  protected final Document myOrigDocument;
  protected final Document myNewDocument;
  protected final Project myProject;
  protected PsiFile myInjectedFile;

  public BaseInjectedFileChangesHandler(Editor editor, Document newDocument, PsiFile injectedFile) {
    myProject = editor.getProject();
    myEditor = editor;
    myOrigDocument = editor.getDocument();
    myNewDocument = newDocument;
    myInjectedFile = injectedFile;
  }


  @Override
  public boolean tryReuse(@NotNull PsiFile injectedFile, TextRange hostRange) {
    if (myInjectedFile == injectedFile) return changesRange(hostRange);

    if ((myInjectedFile == null || !myInjectedFile.isValid())) {
      DocumentWindow documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedFile);
      if (documentWindow != null && changesRange(hostRange)) {
        myInjectedFile = injectedFile;
        return true;
      }
    }

    return false;
  }
}
