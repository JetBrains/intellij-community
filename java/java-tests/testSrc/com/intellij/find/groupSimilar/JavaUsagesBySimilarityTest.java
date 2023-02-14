// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.groupSimilar;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.similarity.JavaUsageSimilarityFeaturesProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.Distance;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;

public class JavaUsagesBySimilarityTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/findSimilar/";
  }

  public void testFeaturesProvider() throws ExecutionException, InterruptedException {
    myFixture.configureByFile("FeaturesProvider.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: {CALL: foo}"));
    assertEquals(1, features.get("CONTEXT: NEW: A"));
    assertEquals(1, features.get("NEW_KEYWORD"));
  }

  public void testForFeatures() throws ExecutionException, InterruptedException {
    myFixture.configureByFile("ForFeatures.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: FOR"));
    assertEquals(1, features.get("FOR_KEYWORD"));
    assertEquals(1, features.get("CONTEXT: VAR: int GP: FOR_STATEMENT -1"));
  }

  public void testAnonymousExtractor() throws ExecutionException, InterruptedException {
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

  public void testAnonymous() throws ExecutionException, InterruptedException {
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

  public void testIncrement() throws ExecutionException, InterruptedException {
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

  public void testLambdaExtractor() throws ExecutionException, InterruptedException {
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

  public void testLambda() throws ExecutionException, InterruptedException {
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

  public void testMethodReference() throws ExecutionException, InterruptedException {
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

  public void testDeclarationInForStatement() throws ExecutionException, InterruptedException {
    myFixture.configureByFile("DeclarationInForStatement.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: FOR"));
  }

  public void testFieldFeatures() throws ExecutionException, InterruptedException {
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

  public void testBag() {
    final Bag bag = new Bag("a", "b");
    assertEquals("""
                   a : 1
                   b : 1
                   """, bag.toString());
    bag.add("a");
    assertEquals(bag.getCardinality(), 3);
    Bag toAdd = new Bag("a", "c");
    bag.addAll(toAdd);
    assertEquals("""
                   c : 1
                   a : 3
                   b : 1
                   """, bag.toString());
    assertEquals(bag.getCardinality(), 5);
  }

  public void testDistance() {
    assertEquals(0.6, Distance.jaccardSimilarityWithThreshold(new Bag("a", "b", "c", "d", "e", "f", "g", "h"),
                                                                             new Bag("a", "z", "c", "y", "e", "f", "g", "h"), 0.6));
  }
}
