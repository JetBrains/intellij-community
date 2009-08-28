/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:41:09
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;

import java.util.EventListener;

public interface MemberInfoChangeListener<T extends PsiElement, M extends MemberInfoBase<T>> extends EventListener {
  void memberInfoChanged(MemberInfoChange<T, M> event);
}
