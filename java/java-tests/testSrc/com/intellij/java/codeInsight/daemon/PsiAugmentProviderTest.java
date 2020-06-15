// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ref.GCUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PsiAugmentProviderTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String AUGMENTED_FIELD = "augmented";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/augment";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    PsiAugmentProvider.EP_NAME.getPoint().registerExtension(new TestAugmentProvider(), myFixture.getTestRootDisposable());
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

  public void testDuplicatesFromSeveralAugmenterCallsAreIgnored() {
    PsiClass psiClass = myFixture.addClass("class C {}");
    PsiField field = psiClass.findFieldByName(AUGMENTED_FIELD, false);
    assertNotNull(field);

    GCUtil.tryGcSoftlyReachableObjects();

    assertSame(field, psiClass.findFieldByName(AUGMENTED_FIELD, false));
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

    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                      @NotNull Class<Psi> type,
                                                                      @Nullable String nameHint) {
      if (type.equals(PsiField.class)) {
        //noinspection unchecked
        return (List<Psi>)Collections.singletonList(new LightFieldBuilder(element.getManager(), AUGMENTED_FIELD, PsiType.BOOLEAN) {
          @Override
          public int hashCode() {
            return 0;
          }

          @Override
          public boolean equals(Object obj) {
            return obj.getClass() == getClass();
          }
        });
      }
      return super.getAugments(element, type, nameHint);
    }

    @NotNull
    @Override
    protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
      if (isLombokVal(modifierList.getParent())) {
        THashSet<String> result = new THashSet<>(modifiers);
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