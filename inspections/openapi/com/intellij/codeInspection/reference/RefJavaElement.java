/*
 * User: anna
 * Date: 18-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.psi.Modifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface RefJavaElement extends RefElement {
   /**
   * Returns the collection of references used in this element.
   * @return the collection of used types
   */
  @NotNull
  Collection<RefClass> getOutTypeReferences();


  /**
   * Checks if the element is <code>final</code>.
   *
   * @return true if the element is final, false otherwise.
   */
  boolean isFinal();

  /**
   * Checks if the element is <code>static</code>.
   *
   * @return true if the element is static, false otherwise.
   */
  boolean isStatic();

  /**
   * Checks if the element directly references any elements marked as deprecated.
   *
   * @return true if the element references any deprecated elements, false otherwise.
   */
  boolean isUsesDeprecatedApi();

  /**
   * Checks if the element is, or belongs to, a synthetic class or method created for a JSP page.
   *
   * @return true if the element is a synthetic JSP element, false otherwise.
   */
  boolean isSyntheticJSP();

  /**
   * Returns the access modifier for the element, as one of the keywords from the
   * {@link com.intellij.psi.PsiModifier} class.
   *
   * @return the modifier, or null if the element does not have any access modifier.
   */
  @Nullable
  @Modifier
    String getAccessModifier();
}