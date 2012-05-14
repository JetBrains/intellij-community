/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.findUsages;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the support for the "Find Usages" feature in a custom language.
 *
 * @author max
 * @see com.intellij.lang.LanguageExtension#forLanguage(com.intellij.lang.Language)
 */
public interface FindUsagesProvider {

  /**
   * Gets the word scanner for building a word index for the specified language.
   * Note that the implementation MUST be thread-safe, otherwise you should return a new instance of your scanner
   * (that can be recommended as a best practice).
   *
   * @return the word scanner implementation, or null if {@link com.intellij.lang.cacheBuilder.SimpleWordsScanner} is OK.
   */
  @Nullable
  WordsScanner getWordsScanner();

  /**
   * Checks if it makes sense to search for usages of the specified element.
   *
   * @param psiElement the element for which usages are searched.
   * @return true if the search is allowed, false otherwise.
   * @see com.intellij.find.FindManager#canFindUsages(com.intellij.psi.PsiElement)
   */
  boolean canFindUsagesFor(@NotNull PsiElement psiElement);

  /**
   * Returns the ID of the help topic which is shown when the specified element is selected
   * in the "Find Usages" dialog.
   *
   * @param psiElement the element for which the help topic is requested.
   * @return the help topic ID, or null if no help is available.
   */
  @Nullable
  String getHelpId(@NotNull PsiElement psiElement);

  /**
   * Returns the user-visible type of the specified element, shown in the "Find Usages"
   * dialog (for example, "class" or "variable"). The type name should not be upper-cased.
   *
   * @param element the element for which the type is requested.
   * @return the type of the element.
   */
  @NotNull
  String getType(@NotNull PsiElement element);

  /**
   * Returns an expanded user-visible name of the specified element, shown in the "Find Usages"
   * dialog. For classes, this can return a fully qualified name of the class; for methods -
   * a signature of the method with parameters.
   *
   * @param element the element for which the name is requested.
   * @return the user-visible name.
   */
  @NotNull
  String getDescriptiveName(@NotNull PsiElement element);

  /**
   * Returns the text representing the specified PSI element in the Find Usages tree.
   *
   * @param element     the element for which the node text is requested.
   * @param useFullName if true, the returned text should use fully qualified names
   * @return the text representing the element.
   */
  @NotNull
  String getNodeText(@NotNull PsiElement element, boolean useFullName);
}
