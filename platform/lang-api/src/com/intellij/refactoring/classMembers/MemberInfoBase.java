/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.refactoring.classMembers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;

/**
 * @author Dennis.Ushakov
 */
public abstract class MemberInfoBase<T extends PsiElement> {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.MemberInfo");
  private SmartPsiElementPointer<T> myMember;
  protected boolean isStatic;
  protected String displayName;
  private boolean isChecked = false;
  /**
   * TRUE if is overriden, FALSE if implemented, null if not implemented or overriden
   */
  protected Boolean overrides;
  private boolean toAbstract = false;

  public MemberInfoBase(T member) {
    updateMember(member);
  }

  public boolean isStatic() {
    return isStatic;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isChecked() {
    return isChecked;
  }

  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  /**
   * Returns Boolean.TRUE if getMember() overrides something, Boolean.FALSE if getMember()
   * implements something, null if neither is the case.
   * If getMember() is a PsiClass, returns Boolean.TRUE if this class comes
   * from 'extends', Boolean.FALSE if it comes from 'implements' list, null
   * if it is an inner class.
   */
  public Boolean getOverrides() {
    return overrides;
  }

  public T getMember() {
    T element = myMember.getElement();
    PsiUtilCore.ensureValid(element);
    return element;
  }

  /**
   * Use this method solely to update element from smart pointer and the likes
   */
  public void updateMember(T element) {
    myMember = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public boolean isToAbstract() {
    return toAbstract;
  }

  public void setToAbstract(boolean toAbstract) {
    this.toAbstract = toAbstract;
  }

  public interface Filter<T extends PsiElement> {
    boolean includeMember(T member);
  }

  public static class EmptyFilter<T extends PsiElement> implements Filter<T> {
    @Override
    public boolean includeMember(T member) {
      return true;
    }
  }
}
