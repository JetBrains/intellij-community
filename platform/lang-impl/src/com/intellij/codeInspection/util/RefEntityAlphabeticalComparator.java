/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @Override
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
