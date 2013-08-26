package com.intellij.refactoring.ui;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoBase;

import javax.swing.*;
import java.util.List;

/**
 * Nikolay.Tropin
 * 8/20/13
 */
public abstract class AbstractMemberSelectionPanel<T extends PsiElement, M extends MemberInfoBase<T>> extends JPanel {

  protected abstract AbstractMemberSelectionTable<T, M> createMemberSelectionTable(List<M> memberInfo, String abstractColumnHeader);

  public abstract AbstractMemberSelectionTable<T, M> getTable();
}
