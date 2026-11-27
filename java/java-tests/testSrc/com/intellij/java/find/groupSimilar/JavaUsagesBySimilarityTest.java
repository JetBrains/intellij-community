// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find.groupSimilar;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.similarity.JavaUsageSimilarityFeaturesProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.Distance;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavaUsagesBySimilarityTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/findSimilar/");

  @Test
  public void testFeaturesProvider() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("FeaturesProvider.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: {CALL: foo}"));
    assertEquals(1, features.get("CONTEXT: NEW: A"));
    assertEquals(1, features.get("NEW_KEYWORD"));
  }

  @Test
  public void testForFeatures() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("ForFeatures.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: FOR"));
    assertEquals(1, features.get("FOR_KEYWORD"));
    assertEquals(1, features.get("CONTEXT: VAR: int GP: FOR_STATEMENT -1"));
  }

  @Test
  public void testAnonymousExtractor() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").setValue(true);
      myFixture.configureByFile("Anonymous.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(1, features.get("anonymousClazz"));
      assertEquals(0, features.get("CONTEXT: anonymousClazz P: NEW_EXPRESSION -1"));
      assertEquals(0, features.get("CONTEXT: anonymousClazz"));
      assertEquals(1, features.get("USAGE: {CALL: A.foo() ret:int arg0 type: A} P: LOCAL_VARIABLE -1"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").resetToDefault();
    }
  }

  @Test
  public void testAnonymous() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").setValue(false);
      myFixture.configureByFile("Anonymous.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(0, features.get("anonymousClazz"));
      assertEquals(1, features.get("CONTEXT: anonymousClazz P: NEW_EXPRESSION -1"));
      assertEquals(1, features.get("CONTEXT: anonymousClazz"));
      assertEquals(1, features.get("USAGE: {CALL: A.foo() ret:int arg0 type: A} P: LOCAL_VARIABLE -1"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").resetToDefault();
    }
  }

  @NotNull
  private static Bag getFeatures(PsiElement elementAtCaret) throws ExecutionException, InterruptedException {
    return ReadAction.nonBlocking(() -> new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret)).submit(AppExecutorUtil.getAppExecutorService()).get();
  }

  @Test
  public void testIncrement() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      myFixture.configureByFile("Increment.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: +="));
      assertEquals(1, features.get("USAGE: {CALL: A.af() ret:int }"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  @Test
  public void testLambdaExtractor() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").setValue(true);
      myFixture.configureByFile("Lambda.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: {CALL: aPackage.A.foo() ret:int arg0 type: <lambda expression>}"));
      assertEquals(0, features.get("CONTEXT: lambda GP: METHOD_CALL_EXPRESSION -1"));
      assertEquals(1, features.get("lambda"));
      assertEquals(0, features.get("CONTEXT: lambda P: EXPRESSION_LIST -1"));

    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").resetToDefault();
    }
  }

  @Test
  public void testLambda() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").setValue(false);
      myFixture.configureByFile("Lambda.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: {CALL: aPackage.A.foo() ret:int arg0 type: <lambda expression>}"));
      assertEquals(1, features.get("CONTEXT: lambda GP: METHOD_CALL_EXPRESSION -1"));
      assertEquals(0, features.get("lambda"));
      assertEquals(1, features.get("CONTEXT: lambda P: EXPRESSION_LIST -1"));

    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
      Registry.get("similarity.find.usages.new.features.collector.for.lambda.and.anonymous.class").resetToDefault();
    }
  }

  @Test
  public void testMethodReference() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      myFixture.configureByFile("MethodReference.java");

      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: {CALL: aPackage.A.foo() ret:int arg0 type: <method reference>}"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  @Test
  public void testDeclarationInForStatement() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("DeclarationInForStatement.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: FOR"));
  }

  @Test
  public void testFieldFeatures() throws ExecutionException, InterruptedException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      Registry.get("similarity.find.usages.add.features.for.fields").setValue(true);
      myFixture.configureByFile("Field.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = getFeatures(elementAtCaret);
      assertEquals(1, features.get("CONTEXT: FIELD: field"));
      assertEquals(0, features.get("CONTEXT: FIELD: localVariable"));
      assertEquals(0, features.get("CONTEXT: FIELD: forLoopVariable"));
      assertEquals(0, features.get("CONTEXT: FIELD: nestedIfVar"));
      assertEquals(0, features.get("CONTEXT: FIELD: whileVariable"));
      assertEquals(0, features.get("CONTEXT: FIELD: ifVar"));
      assertEquals(0, features.get("CONTEXT: FIELD: elseVar"));
      assertEquals(0, features.get("CONTEXT: FIELD: list"));
      assertEquals(0, features.get("CONTEXT: FIELD: forEachVar"));
    }
    finally {
      Registry.get("similarity.find.usages.add.features.for.fields").resetToDefault();
    }
  }

  @Test
  public void testBag() {
    final Bag bag = new Bag("a", "b");
    assertEquals("""
                   a : 1
                   b : 1
                   """, bag.toString());
    bag.add("a");
    assertEquals(3, bag.getCardinality());
    Bag toAdd = new Bag("a", "c");
    bag.addAll(toAdd);
    assertEquals("""
                   c : 1
                   a : 3
                   b : 1
                   """, bag.toString());
    assertEquals(5, bag.getCardinality());
  }

  @Test
  public void testDistance() {
    assertEquals(0.6, Distance.jaccardSimilarityWithThreshold(new Bag("a", "b", "c", "d", "e", "f", "g", "h"),
                                                                             new Bag("a", "z", "c", "y", "e", "f", "g", "h"), 0.6));
  }
}
