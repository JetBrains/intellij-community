package com.intellij.json.psi;

import com.intellij.psi.PsiNamedElement;

/**
 * Empty interface as workaround for inability to specify two interfaces in BNF grammar.
 *
 * @author Mikhail Golubev
 */
public interface JsonNamedElement extends JsonElement, PsiNamedElement{
}
