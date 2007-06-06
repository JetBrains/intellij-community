package com.intellij.codeInsight.completion;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public abstract class CompletionContext implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionContext");

  protected Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public final Project project;
  public final Editor editor;
  public final PsiFile file;
  public int startOffset;
  public int selectionEndOffset;
  public int offset;

  public int identifierEndOffset = -1;
  public int lparenthOffset = -1;
  public int rparenthOffset = -1;
  public int argListEndOffset = -1;
  public boolean hasArgs = false;

  protected String myPrefix = "";

  protected CompletionContext(Project project, Editor editor, PsiFile file, int offset1, int offset2) {
    this.project = project;
    this.editor = editor;
    this.file = file;

    startOffset = offset1;
    selectionEndOffset = offset2;
  }

  public abstract void init();

  public void shiftOffsets(int shift){
    if (shift != 0){
      selectionEndOffset += shift; //?
      identifierEndOffset += shift;
      if (lparenthOffset >= 0){
        lparenthOffset += shift;
      }
      if (rparenthOffset >= 0){
        rparenthOffset += shift;
      }
      if (argListEndOffset >= 0){
        argListEndOffset += shift;
      }
    }
  }

  public void resetParensInfo(){
    rparenthOffset = -1;
    argListEndOffset = -1;
    identifierEndOffset = -1;
    lparenthOffset = -1;
  }

  public abstract boolean prefixMatches(String name);

  public String getPrefix() {
    return myPrefix;
  }

  public void setPrefix(PsiElement insertedElement, int offset, @Nullable CompletionData completionData) {
    setPrefix(completionData == null ? CompletionData.findPrefixStatic(insertedElement, offset) : completionData.findPrefix(insertedElement, offset));
  }

  public abstract void setPrefix(String prefix);
}

