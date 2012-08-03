package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class WordCompletionTest extends CompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/word/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testKeyWordCompletion() throws Exception {
    configureByFile(BASE_PATH + "1.txt");
    checkResultByFile(BASE_PATH + "1_after.txt");
    
    configureByFile(BASE_PATH + "1.properties");
    checkResultByFile(BASE_PATH + "1_after.properties");

    configureByFile(BASE_PATH + "1.xml");
    checkResultByFile(BASE_PATH + "1_after.xml");

    configureByFile(BASE_PATH + "2.xml");
    configureByFile(BASE_PATH + "2_after.xml");
  }

  public void testNoWordCompletionForNonSoftReference() throws Throwable {
    final PsiReferenceProvider softProvider = new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element, true) {
          @Override
          public PsiElement resolve() {
            return null;
          }

          @Override
          @NotNull
          public Object[] getVariants() {
            return new Object[]{"MySoftVariant"};
          }
        }};
      }
    };
    final PsiReferenceProvider hardProvider = new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element, false) {
          @Override
          public PsiElement resolve() {
            return null;
          }

          @Override
          @NotNull
          public Object[] getVariants() {
            return new Object[]{"MyHardVariant"};
          }
        }};
      }
    };
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(StdLanguages.JAVA);
    try {
      registrar.registerReferenceProvider(PsiLiteralExpression.class, softProvider);
      registrar.registerReferenceProvider(PsiLiteralExpression.class, hardProvider);

      configureByFile(BASE_PATH + "3.java");
      checkResultByFile(BASE_PATH + "3_after.java");
    }
    finally {
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, softProvider);
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, hardProvider);
    }
  }

  public void testInJavaLiterals() throws Exception {
    configureByFile(BASE_PATH + "InJavaLiterals.java");
    checkResultByFile(BASE_PATH + "InJavaLiterals_after.java");
  }

  public void testComments() throws Throwable {
    configureByFile(BASE_PATH + "4.java");
    checkResultByFile(BASE_PATH + "4_after.java");
  }

  public void testSpaceInComment() throws Throwable {
    configureByFile(BASE_PATH + "SpaceInComment.java");
    checkResultByFile(BASE_PATH + "SpaceInComment.java");
  }

  public void testTextInComment() throws Throwable {
    configureByFile(BASE_PATH + "TextInComment.java");
    checkResultByFile(BASE_PATH + "TextInComment_after.java");
  }

  public void testDollarsInPrefix() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".txt");
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.txt");
  }

  public void testCompleteStringLiteralCopy() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

}
