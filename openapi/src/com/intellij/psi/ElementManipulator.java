package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.04.2003
 * Time: 11:22:05
 *
 * @see com.intellij.psi.ElementManipulatorsRegistry
 */
public interface ElementManipulator<T extends PsiElement> {

  T handleContentChange(T element, TextRange range, String newContent) throws IncorrectOperationException;

  T handleContentChange(T element, String newContent) throws IncorrectOperationException;

  TextRange getRangeInElement(T element);
}
