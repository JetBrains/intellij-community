/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PsiAugmentProviderTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/augment";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(PsiAugmentProvider.EP_NAME, new TestAugmentProvider(), getTestRootDisposable());
    myFixture.addClass("package lombok;\npublic @interface val { }");
  }

  public void testLombokVal() {
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testLombokEditing() {
    PsiFile file = myFixture.configureByText("a.java", "import lombok.val;\nclass Foo { {val o = <caret>;} }");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
    assertNotNull(var);

    PsiType type1 = var.getType();
    assertNotNull(type1);
    assertEquals("lombok.val", type1.getCanonicalText(false));

    myFixture.type('1');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(var.isValid());

    PsiType type2 = var.getType();
    assertNotNull(type2);
    assertEquals(PsiType.INT.getCanonicalText(false), type2.getCanonicalText(false));
  }

  private static class TestAugmentProvider extends PsiAugmentProvider {
    private static final String LOMBOK_VAL_FQN = "lombok.val";
    private static final String LOMBOK_VAL_SHORT_NAME = "val";

    @Nullable
    @Override
    protected PsiType inferType(@NotNull PsiTypeElement typeElement) {
      PsiElement parent = typeElement.getParent();
      if (isLombokVal(parent)) {
        PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
        if (referenceElement != null) {
          PsiElement resolve = referenceElement.resolve();
          if (resolve instanceof PsiClass) {
            if (parent instanceof PsiLocalVariable) {
              PsiExpression initializer = ((PsiVariable)parent).getInitializer();
              assertNotNull(initializer);
              PsiType initializerType = initializer.getType();
              if (initializer instanceof PsiNewExpression) {
                PsiJavaCodeReferenceElement reference = ((PsiNewExpression)initializer).getClassOrAnonymousClassReference();
                if (reference != null) {
                  PsiReferenceParameterList parameterList = reference.getParameterList();
                  if (parameterList != null) {
                    PsiTypeElement[] elements = parameterList.getTypeParameterElements();
                    if (elements.length == 1 && elements[0].getType() instanceof PsiDiamondType) {
                      return TypeConversionUtil.erasure(initializerType);
                    }
                  }
                }
              }
              return initializerType;
            }

            PsiForeachStatement foreachStatement = (PsiForeachStatement)((PsiParameter)parent).getDeclarationScope();
            PsiExpression iteratedValue = foreachStatement.getIteratedValue();
            if (iteratedValue != null) {
              return JavaGenericsUtil.getCollectionItemType(iteratedValue);
            }
          }
        }
      }

      return null;
    }

    @NotNull
    @Override
    protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
      if (isLombokVal(modifierList.getParent())) {
        THashSet<String> result = ContainerUtil.newTroveSet(modifiers);
        result.add(PsiModifier.FINAL);
        return result;
      }
      return modifiers;
    }

    private static boolean isLombokVal(PsiElement variable) {
      if (variable instanceof PsiLocalVariable && ((PsiLocalVariable)variable).getInitializer() != null ||
          variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement) {
        PsiTypeElement typeElement = ((PsiVariable)variable).getTypeElement();
        if (typeElement != null) {
          String text = typeElement.getText();
          if (LOMBOK_VAL_SHORT_NAME.equals(text) || LOMBOK_VAL_FQN.equals(text)) {
            return true;
          }
        }
      }

      return false;
    }
  }
}