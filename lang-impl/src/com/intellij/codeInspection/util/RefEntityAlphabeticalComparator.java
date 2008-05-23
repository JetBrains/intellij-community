/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2002
 * Time: 10:11:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.RefEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class RefEntityAlphabeticalComparator implements Comparator<RefEntity> {

  public int compare(@NotNull final RefEntity o1, @NotNull final RefEntity o2) {
    if (o1 == o2) return 0;
    return o1.getQualifiedName().compareToIgnoreCase(o2.getQualifiedName());
  }

  private static class RefEntityAlphabeticalComparatorHolder {
    private static final RefEntityAlphabeticalComparator ourEntity = new RefEntityAlphabeticalComparator();
  }

  public static RefEntityAlphabeticalComparator getInstance() {

    return RefEntityAlphabeticalComparatorHolder.ourEntity;
  }
}
