/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.util.IncorrectOperationException;

/**
 *  @author dsl
 */
public class ReferenceParameterListTest extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ReferenceParameterListTest");
  public void testParameterListInExtends() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiClass classFromText = factory.createClassFromText("class X extends Y<Z, W> {}", null);
    final PsiClass classX = classFromText.getInnerClasses()[0];
    final PsiJavaCodeReferenceElement[] extendsOfX = classX.getExtendsList().getReferenceElements();
    assertEquals(1, extendsOfX.length);
    final PsiJavaCodeReferenceElement ref = extendsOfX[0];
    assertEquals("Y<Z,W>", ref.getCanonicalText());
    assertEquals("Y", ref.getReferenceName());
    final PsiTypeElement[] refParams = ref.getParameterList().getTypeParameterElements();
    assertEquals(2, refParams.length);
    assertEquals("Z", refParams[0].getType().getCanonicalText());
    assertEquals("W", refParams[1].getType().getCanonicalText());
    final PsiType refType = factory.createType(ref);
    assertEquals("Y<Z,W>", refType.getCanonicalText());
    final PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType) refType).getReference();
    assertEquals("Y<Z,W>", reference.getCanonicalText());
  }
  public void testResolvableParameterListInExtends() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiClass classFromText = factory.createClassFromText(
            "class Z {} class W{}" +
            "class Y<A, B> {} " +
            "class X extends Y<Z, W> {}",
            null);


    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        classFromText.setName("Q");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
    final PsiClass classX = classFromText.getInnerClasses()[3];
    final PsiJavaCodeReferenceElement[] extendsOfX = classX.getExtendsList().getReferenceElements();
    assertEquals(1, extendsOfX.length);
    final PsiJavaCodeReferenceElement ref = extendsOfX[0];
    assertEquals("Q.Y<Q.Z,Q.W>", ref.getCanonicalText());
    assertEquals("Y", ref.getReferenceName());
    final PsiTypeElement[] refParams = ref.getParameterList().getTypeParameterElements();
    assertEquals(2, refParams.length);
    assertEquals("Q.Z", refParams[0].getType().getCanonicalText());
    assertEquals("Q.W", refParams[1].getType().getCanonicalText());
    final PsiType refType = factory.createType(ref);
    assertEquals("Q.Y<Q.Z,Q.W>", refType.getCanonicalText());
    final PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType) refType).getReference();
    assertEquals("Q.Y<Q.Z,Q.W>", reference.getCanonicalText());
  }
}
