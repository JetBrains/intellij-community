// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PsiAugmentProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String AUGMENTED_FIELD = "augmented";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/augment");
    PsiAugmentProvider.EP_NAME.getPoint().registerExtension(new TestAugmentProvider(), myFixture.getTestRootDisposable());
    myFixture.addClass("package lombok;\npublic @interface val { }");
  }

  @Test
  public void testLombokVal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testLombokEditing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("a.java", "import lombok.val;\nclass Foo { {val o = <caret>;} }");
    PsiLocalVariable var = ReadAction.computeBlocking(() -> PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class));
    assertNotNull(var);

    ReadAction.runBlocking(() -> {
      PsiType type1 = var.getType();
      assertNotNull(type1);
      assertEquals("lombok.val", type1.getCanonicalText(false));
    });

    myFixture.type('1');
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      assertTrue(var.isValid());

      PsiType type2 = var.getType();
      assertNotNull(type2);
      assertEquals(PsiTypes.intType().getCanonicalText(false), type2.getCanonicalText(false));
    });
  }

  @Test
  public void testDuplicatesFromSeveralAugmenterCallsAreIgnored() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("class C {}");
    PsiField field = ReadAction.computeBlocking(() -> psiClass.findFieldByName(AUGMENTED_FIELD, false));
    assertNotNull(field);

    GCUtil.tryGcSoftlyReachableObjects();

    assertSame(field, ReadAction.computeBlocking(() -> psiClass.findFieldByName(AUGMENTED_FIELD, false)));
  }

  @Test
  public void testDoNotCacheInvalidAugmentedFields() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("class C {}");

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      PsiField field = psiClass.findFieldByName(AUGMENTED_FIELD, false);
      assertTrue(field.isValid());

      PsiManager.getInstance(myFixture.getProject()).dropPsiCaches();
      assertFalse(field.isValid());

      PsiUtilCore.ensureValid(psiClass.findFieldByName(AUGMENTED_FIELD, false));
    });
  }

  @Test
  public void testPassNameHintToAugmenter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("class C {}");

    List<String> hints = new ArrayList<>();
    PsiAugmentProvider.EP_NAME.getPoint().registerExtension(new PsiAugmentProvider() {
      @Override
      protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                        @NotNull Class<Psi> type,
                                                                        @Nullable String nameHint) {
        if (element == psiClass) {
          hints.add(nameHint);
        }
        return Collections.emptyList();
      }
    }, myFixture.getTestRootDisposable());

    ReadAction.runBlocking(() -> {
      assertNotNull(psiClass.findFieldByName(AUGMENTED_FIELD, true));
      assertNull(psiClass.findFieldByName("another", false));
      assertThat(psiClass.getFields()).hasSize(1);
      assertThat(psiClass.getAllFields()).hasSize(2);

      assertIterableEquals(Arrays.asList(AUGMENTED_FIELD, "another", null), hints);
    });
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
      var manager = element.getManager();
      var count = manager.getModificationTracker().getModificationCount();
      if (type.equals(PsiField.class)) {
        //noinspection unchecked
        return (List<Psi>)Collections.singletonList(new LightFieldBuilder(manager, AUGMENTED_FIELD, PsiTypes.booleanType()) {
          @Override
          public int hashCode() {
            return 0;
          }

          @Override
          public boolean equals(Object obj) {
            return obj.getClass() == getClass();
          }

          @Override
          public boolean isValid() {
            return count == manager.getModificationTracker().getModificationCount();
          }
        });
      }
      return super.getAugments(element, type, nameHint);
    }

    @NotNull
    @Override
    protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
      if (isLombokVal(modifierList.getParent())) {
        Set<String> result = new HashSet<>(modifiers);
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