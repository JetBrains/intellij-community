package com.intellij.dupLocator;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;

import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 18, 2004
 * Time: 8:48:33 PM
 * To change this template use File | Settings | File Templates.
 */
public interface _DupInfo {
  TreeSet<Integer> getPatterns();

  int getHeight(Integer pattern);

  int getDensity(Integer pattern);

  HashSet<PsiElement> getOccurencies(Integer pattern);

  String toString(Integer pattern);
}
