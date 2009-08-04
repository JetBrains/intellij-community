/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 08.07.2002
 * Time: 17:53:29
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;

import java.util.Set;

public interface MemberDependencyGraph<T extends PsiElement, M extends MemberInfoBase<T>> {
  /**
   * Call this to notify that a new member info have been added
   * or a state of some memberInfo have been changed.
   * @param memberInfo
   */
  void memberChanged(M memberInfo);

  /**
   * Returns class members that are dependent on checked MemberInfos.
   * @return set of PsiMembers
   */
  Set<? extends T> getDependent();

  /**
   * Returns PsiMembers of checked MemberInfos that member depends on.
   * member should belong to getDependent()
   * @param member
   * @return set of PsiMembers
   */
  Set<? extends T> getDependenciesOf(T member);
}
