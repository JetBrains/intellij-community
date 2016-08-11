/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class JavaFileTreeElement extends PsiTreeElementBase<PsiClassOwner> implements ItemPresentation {
  public JavaFileTreeElement(PsiClassOwner file) {
    super(file);
  }

  @Override
  public String getPresentableText() {
    PsiClassOwner element = getElement();
    return element == null ? "" : element.getName();
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    PsiClassOwner element = getElement();
    if (element == null) return Collections.emptyList();
    PsiClass[] classes = element.getClasses();
    ArrayList<StructureViewTreeElement> result = new ArrayList<>();
    for (PsiClass aClass : classes) {
      result.add(new JavaClassTreeElement(aClass, false, new HashSet<>()));
    }
    return result;

  }
}
