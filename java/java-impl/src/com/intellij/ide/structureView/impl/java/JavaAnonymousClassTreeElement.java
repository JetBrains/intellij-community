/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.util.Collection;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassTreeElement extends JavaClassTreeElement {
  public final static JavaAnonymousClassTreeElement[] EMPTY_ARRAY = {};

  private String myName;
  
  public JavaAnonymousClassTreeElement(PsiAnonymousClass aClass, Set<PsiClass> parents) {
    super(aClass, false, parents);
    //parents.add(aClass.getSuperClass());
  }

  @Override
  public boolean isPublic() {
    return false;
  }

  @Override
  public String getPresentableText() {
    if (myName != null) return myName;

    final PsiClass element = getElement();
    if (element != null) {
      final PsiClass container = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      final Collection<PsiAnonymousClass> all = PsiTreeUtil.collectElementsOfType(container, PsiAnonymousClass.class);
      int index = 0;
      for (PsiAnonymousClass anonymousClass : all) {
        if (PsiTreeUtil.getParentOfType(anonymousClass, PsiClass.class) == container) {
          index++;
          if (anonymousClass == element) {
            myName = "$" + index;
            return myName;
          }
        }
      }
    }
    return "Anonymous";
  }



  @Override
  public String getLocationString() {
    return ((PsiAnonymousClass)getElement()).getBaseClassType().getClassName();
  }

  @Override
  public Icon getIcon(boolean open) {
    return PlatformIcons.ANONYMOUS_CLASS_ICON;
  }
}
