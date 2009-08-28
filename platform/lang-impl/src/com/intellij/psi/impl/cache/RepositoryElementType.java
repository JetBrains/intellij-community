/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 13, 2002
 * Time: 10:36:49 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.psi.impl.cache;

import org.jetbrains.annotations.NonNls;

public class RepositoryElementType {
  public static final RepositoryElementType DIR = new RepositoryElementType("DIR", 0);
  public static final RepositoryElementType FILE = new RepositoryElementType("FILE", 1);
  public static final RepositoryElementType CLASS = new RepositoryElementType("CLASS", 2);
  public static final RepositoryElementType METHOD = new RepositoryElementType("METHOD", 3);
  public static final RepositoryElementType FIELD = new RepositoryElementType("FIELD", 4);
  public static final RepositoryElementType CLASS_INITIALIZER = new RepositoryElementType("CLASS_INITIALIZER", 5);

  private final String myName; // for debug only
  private final int myType;


  private RepositoryElementType(@NonNls String name, int type) {
    myName = name;
    myType = type;
  }

  public String toString() {
    return myName;
  }

  public int getType() {
    return myType;
  }
}
