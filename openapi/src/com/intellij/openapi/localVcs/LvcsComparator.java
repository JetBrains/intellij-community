/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import java.util.Comparator;

/**
 * author: lesya
 */

public class LvcsComparator implements Comparator{

  public static final Comparator INSTANCE = new LvcsComparator();

  private LvcsComparator(){

  }

  public int compare(Object obj1, Object obj2) {
    if (obj1 instanceof LvcsRevision){
      if (obj2 instanceof LvcsRevision)
        return ((LvcsRevision)obj1).compareTo((LvcsRevision)obj2);
      else if (obj2 instanceof LvcsLabel)
        return ((LvcsRevision)obj1).compareTo((LvcsLabel)obj2);
    } else if (obj1 instanceof LvcsLabel)
      if (obj2 instanceof LvcsRevision)
        return ((LvcsLabel)obj1).compareTo((LvcsRevision)obj2);
      else if (obj2 instanceof LvcsLabel)
        return ((LvcsLabel)obj1).compareTo((LvcsLabel)obj2);

    throw new RuntimeException("Cannot compare " + obj1 + " and " + obj2);
  }
}
