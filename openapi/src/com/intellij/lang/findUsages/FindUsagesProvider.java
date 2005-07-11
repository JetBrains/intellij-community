/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
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
package com.intellij.lang.findUsages;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the support for the "Find Usages" feature in a custom language.
 * @author max
 * @see com.intellij.lang.Language#getFindUsagesProvider()
 */
public interface FindUsagesProvider {
  /**
   * Checks if tokens of the specified type can contain references when the search
   * is done with the specified context.
   * @param token the token type to check for references.
   * @param searchContext represents find usages request,
   * a combination of constants in {@link com.intellij.psi.search.UsageSearchContext}
   */
  boolean mayHaveReferences(IElementType token, final short searchContext);

  /**
   * Gets the word scanner for building a word index for the specified language.
   * @return the word scanner implementation, or null if Find Usages is not supported for the language.
   */
  @Nullable
  WordsScanner getWordsScanner();

  /**
   * Checks if it makes sense to search for usages of the specified element.
   * @param psiElement the element for which usages are searched.
   * @return true if the search is allowed, false otherwise.
   */
  boolean canFindUsagesFor(PsiElement psiElement);

  /**
   * Returns the ID of the help topic which is shown when the specified element is selected
   * in the "Find Usages" dialog.
   * @param psiElement the element for which the help topic is requested.
   * @return the help topic ID, or null if no help is available.
   */
  @Nullable
  String getHelpId(PsiElement psiElement);

  /**
   * Returns the user-visible type of the specified element, shown in the "Find Usages"
   * dialog (for example, "class" or "variable"). The type name should not be upper-cased.
   * @param element the element for which the type is requested.
   * @return the type of the element.
   */
  @NotNull
  String getType(PsiElement element);

  /**
   * Returns an expanded user-visible name of the specified element, shown in the "Find Usages"
   * dialog. For classes, this can return a fully qualified name of the class; for methods -
   * a signature of the method with parameters.
   * @param element the element for which the name is requested.
   * @return the user-visible name.
   */
  @NotNull
  String getDescriptiveName(PsiElement element);

  /**
   * Returns the text representing the specified PSI element in the Find Usages tree.
   * @param element the element for which the node text is requested.
   * @param useFullName if true, the returned text should use fully qualified names
   * @return the text representing the element.
   */
  @NotNull
  String getNodeText(PsiElement element, boolean useFullName);
}
