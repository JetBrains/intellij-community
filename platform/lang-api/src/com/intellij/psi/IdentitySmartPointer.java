/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

public class IdentitySmartPointer<T extends PsiElement> implements SmartPsiElementPointer<T> {
  private T myElement;
  private final PsiFile myFile;

  public IdentitySmartPointer(T element, PsiFile file) {
    myElement = element;
    myFile = file;
  }

  public IdentitySmartPointer(final T element) {
    this(element, element.getContainingFile());
  }

  @NotNull
  public Project getProject() {
    return myFile.getProject();
  }

  public T getElement() {
    if (myElement != null && !myElement.isValid()) {
      myElement = null;
    }
    return myElement;
  }

  public int hashCode() {
    final T elt = getElement();
    return elt == null ? 0 : elt.hashCode();
  }

  public boolean equals(Object obj) {
    return obj instanceof SmartPsiElementPointer && Comparing.equal(getElement(), ((SmartPsiElementPointer)obj).getElement());
  }

  public PsiFile getContainingFile() {
    return myFile;
  }
}
