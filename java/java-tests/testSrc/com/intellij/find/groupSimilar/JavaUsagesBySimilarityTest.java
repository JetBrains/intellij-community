// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.groupSimilar;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.similarity.JavaUsageSimilarityFeaturesProvider;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.Distance;

public class JavaUsagesBySimilarityTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/findSimilar/";
  }

  public void testFeaturesProvider() {
    myFixture.configureByFile("FeaturesProvider.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: {CALL: foo}"));
    assertEquals(1, features.get("CONTEXT: NEW: A"));
    assertEquals(1, features.get("NEW_KEYWORD"));
  }

  public void testForFeatures() {
    myFixture.configureByFile("ForFeatures.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: FOR"));
    assertEquals(1, features.get("FOR_KEYWORD"));
    assertEquals(1, features.get("CONTEXT: VAR: int GP: FOR_STATEMENT -1"));
  }

  public void testAnonymous() {
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      myFixture.configureByFile("Anonymous.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
      assertEquals(1, features.get("CONTEXT: anonymousClazz P: NEW_EXPRESSION -1"));
      assertEquals(1, features.get("USAGE: {CALL: A.foo() ret:int arg0 type: A} NEXT: PsiJavaToken:SEMICOLON"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  public void testIncrement() {
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      myFixture.configureByFile("Increment.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: +="));
      assertEquals(1, features.get("USAGE: {CALL: A.af() ret:int }"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  public void testLambda() {
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      myFixture.configureByFile("Lambda.java");
      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: {CALL: aPackage.A.foo() ret:int arg0 type: <lambda expression>}"));
      assertEquals(1, features.get("CONTEXT: lambda GP: METHOD_CALL_EXPRESSION -1"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  public void testMethodReference() {
    try {
      Registry.get("similarity.find.usages.fast.clustering").setValue(false);
      myFixture.configureByFile("MethodReference.java");

      PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
      final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
      assertEquals(1, features.get("USAGE: {CALL: aPackage.A.foo() ret:int arg0 type: <method reference>}"));
    }
    finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  public void testDeclarationInForStatement() {
    myFixture.configureByFile("DeclarationInForStatement.java");
    PsiElement elementAtCaret = myFixture.getReferenceAtCaretPosition().getElement();
    final Bag features = new JavaUsageSimilarityFeaturesProvider().getFeatures(elementAtCaret);
    assertEquals(1, features.get("USAGE: FOR"));
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
