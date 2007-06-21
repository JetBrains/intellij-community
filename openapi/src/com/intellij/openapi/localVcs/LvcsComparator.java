/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.localVcs;

import java.util.Comparator;

/**
 * @deprecated use LocalHistory instead
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
