package com.intellij.psi.impl.light;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public abstract class LightElement extends PsiElementBase {
  protected final PsiManager myManager;
  private final Language myLanguage;

  protected LightElement(PsiManager manager, final Language language) {
    myManager = manager;
    myLanguage = language;
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  public PsiManager getManager(){
    return myManager;
  }

  public PsiElement getParent(){
    return null;
  }

  @NotNull
  public PsiElement[] getChildren(){
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiFile getContainingFile(){
    return null;
  }

  public TextRange getTextRange(){
    return null;
  }

  public int getStartOffsetInParent(){
    return -1;
  }

  public final int getTextLength(){
    String text = getText();
    return text != null ? text.length() : 0;
  }

  @NotNull
  public char[] textToCharArray(){
    return getText().toCharArray();
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return getText().equals(text.toString());
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset(){
    return -1;
  }

  public boolean isValid(){
    return true;
  }

  public boolean isWritable(){
    return false;
  }

  public boolean isPhysical() {
    return false;
  }

  public abstract String toString();

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkDelete() throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public ASTNode getNode() {
    return null;
  }
}
