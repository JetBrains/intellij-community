package com.intellij.refactoring.ui;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoBase;

import javax.swing.*;

/**
 * Nikolay.Tropin
 * 8/20/13
 */
public abstract class AbstractMemberSelectionPanel<T extends PsiElement, M extends MemberInfoBase<T>> extends JPanel {
  public abstract AbstractMemberSelectionTable<T, M> getTable();
}
