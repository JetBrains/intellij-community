package com.intellij.lang.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Interface that should be implemented by the language in order to provide inline functionality and possibly
 * participate in inline of elements in other languages this language may reference.
 * @author ven
 */
public interface InlineHandler {
  interface Settings {
    /**
     * @return true if as a result of refactoring setup only the reference where refactoring
     * was triggered should be inlined.
     */
    boolean isOnlyOneReferenceToInline();
  }

  /**
   * @param element element to be inlined
   * @param invokedOnReference true if the user invoked the refactoring on an element reference
   * @param editor in case refactoring has been called in the editor
   * @return <code>Settings</code> object in case refactoring shoold be performed or null otherwise

   */
  @Nullable Settings prepareInlineElement(PsiElement element, Editor editor, boolean invokedOnReference);

  /**
   * @param element inlined element
   */
  void removeDefinition(PsiElement element);

  /**
   * @param element inlined element
   * @return Inliner instance to be used for inlining references in this language 
   */
  @Nullable Inliner createInliner (PsiElement element);

  interface Inliner {
    /**
     * @param reference reference to inlined element
     * @param referenced inlined element
     * @return set of conflicts inline of this element to the place denoted by reference would incur
     * or null if no conflicts detected.
     */
    @Nullable
    Collection<String> getConflicts(PsiReference reference, PsiElement referenced);

    /**
     * Perform actual inline of element to the point where it is referenced
     * @param reference reference to inlined element
     * @param referenced inlined element
     */
    void inlineReference(PsiReference reference, PsiElement referenced);
  }
}
