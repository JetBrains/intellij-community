package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProviderBase;
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
    final PsiReferenceProviderBase softProvider = new PsiReferenceProviderBase() {
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element) {
          public PsiElement resolve() {
            return null;
          }

          public boolean isSoft() {
            return true;
          }

          @NotNull
          public Object[] getVariants() {
            return new Object[]{"MySoftVariant"};
          }
        }};
      }
    };
    final PsiReferenceProviderBase hardProvider = new PsiReferenceProviderBase() {
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element) {
          public PsiElement resolve() {
            return null;
          }

          public boolean isSoft() {
            return false;
          }

          @NotNull
          public Object[] getVariants() {
            return new Object[]{"MyHardVariant"};
          }
        }};
      }
    };
    ReferenceProvidersRegistry.getInstance(getProject()).registerReferenceProvider(PsiLiteralExpression.class, softProvider);
    ReferenceProvidersRegistry.getInstance(getProject()).registerReferenceProvider(PsiLiteralExpression.class, hardProvider);

    configureByFile(BASE_PATH + "3.java");
    checkResultByFile(BASE_PATH + "3_after.java");
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

}
