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
package com.intellij.codeHighlighting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public abstract class TextEditorHighlightingPass implements HighlightingPass {
  protected final Document myDocument;
  private final Project myProject;
  private final long myInitialStamp;


  protected TextEditorHighlightingPass(final Project project, final Document document) {
    myDocument = document;
    myProject = project;
    myInitialStamp = document.getModificationStamp();
  }

  @Deprecated
  protected TextEditorHighlightingPass(Document document) {
    this(null, document);
  }

  public final void collectInformation(ProgressIndicator progress) {
    if (!isValid()) return; //Document has changed.
    doCollectInformation(progress);
  }

  private boolean isValid() {
    if (myDocument.getModificationStamp() != myInitialStamp) return false;
    if (myProject != null) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
      if (file == null || !file.isValid()) return false;
    }

    return true;
  }

  public final void applyInformationToEditor() {
    if (!isValid()) return; // Document has changed.
    doApplyInformationToEditor();
  }

  public abstract void doCollectInformation(ProgressIndicator progress);
  public abstract void doApplyInformationToEditor();

  public abstract int getPassId();
}