// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.lang.FCTSBackedLighterAST;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.FileLocalResolver;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class FileLocalResolverTest extends LightJavaCodeInsightFixtureTestCase {
  public void testUnknownVariable() {
    assertDoesNotResolve("class C {{ <caret>a = 2; }}");
  }

  public void testCodeBlockVariable() {
    assertResolves("""
                     class C {{
                       int a;
                       int b;
                       {
                         <caret>a = 2;
                       }
                     }}""");
  }

  public void testVariablesInTheSameDeclaration() {
    assertResolves("""
                     class C {{
                       int a = 2, b = <caret>a;
                     }}""");
  }

  public void testInsideForLoopVar() {
    assertResolves("""
                     class C {{
                       for (int a = 2, b = <caret>a;;) {}
                     }}""");
  }

  public void testForLoopVarInBody() {
    assertResolves("""
                     class C {{
                       for (int a = 2;;) { b = <caret>a; }
                     }}""");
  }

  public void testForLoopVarInCondition() {
    assertResolves("""
                     class C {{
                       for (int a = 2; <caret>a < 3;) {}
                     }}""");
  }

  public void testForLoopVarInModification() {
    assertResolves("""
                     class C {{
                       for (int a = 2; ; <caret>a++) {}
                     }}""");
  }

  public void testForeachLoopVarInBody() {
    assertResolves("""
                     class C {{
                       for (Object x : xs) { <caret>x = null; }
                     }}""");
  }

  public void testResourceVarInTryBody() {
    assertResolves("""
                     class C {{
                       try (Smth x = smth) { <caret>x = null; }
                     }}""");
  }

  public void testResourceVarInSecondResourceVar() {
    assertResolves("""
                     class C {{
                       try (Smth x = smth; Smth y = <caret>x) { }
                     }}""");
  }

  public void testNoForwardReferencesInResourceList() {
    assertDoesNotResolve("""
                           class C {{
                             try (Smth y = <caret>x; Smth x = smth) { }
                           }}""");
  }

  public void testCatchParameter() {
    assertResolves("""
                     class C {{
                       try { } catch (IOException e) { <caret>e = null; }
                     }}""");
  }

  public void testSingleLambdaParameter() {
    assertResolves("""
                     class C {{
                       Some r = param -> sout(para<caret>m);
                     }}""");
  }

  public void testListedLambdaParameter() {
    assertResolves("""
                     class C {{
                       Some r = (param, p2) -> sout(para<caret>m);
                     }}""");
  }

  public void testTypedLambdaParameter() {
    assertResolves("""
                     class C {{
                       Some r = (String param, int p2) -> sout(<caret>p2);
                     }}""");
  }

  public void testMethodParameter() {
    assertResolves("""
                     class C { void foo(int param) {
                       sout(para<caret>m);
                     }}""");
  }

  public void testField() {
    assertResolves("""
                     class C {
                     {
                       sout(<caret>f);
                     }
                     int f = 2;
                     }""");
  }

  public void testRecordComponent() {
    LighterASTNode result = configureAndResolve("record A(String s) {{String other = <caret>s;}}").getTarget();
    assertNotNull(result);
    assertEquals(result.getStartOffset(), findReference().resolve().getNavigationElement().getTextRange().getStartOffset());
  }

  public void testPossibleAnonymousSuperClassField() {
    assertEquals(FileLocalResolver.LightResolveResult.UNKNOWN, configureAndResolve("""
                                                                                     class C {
                                                                                     {
                                                                                       int a = 1;
                                                                                       new SuperClass() {
                                                                                       void foo() {
                                                                                         print(<caret>a);
                                                                                       }
                                                                                       }
                                                                                     }
                                                                                     }"""));
    assertEquals(FileLocalResolver.LightResolveResult.NON_LOCAL, configureAndResolve("""
                                                                                       class C {
                                                                                       {
                                                                                         new SuperClass() {
                                                                                         void foo() {
                                                                                           print(<caret>a);
                                                                                         }
                                                                                         }
                                                                                       }
                                                                                       }"""));
    assertEquals(FileLocalResolver.LightResolveResult.NON_LOCAL, configureAndResolve("""
                                                                                       class C {
                                                                                       int field = 1;
                                                                                       {
                                                                                         new SuperClass() {
                                                                                         void foo() {
                                                                                           print(<caret>field);
                                                                                         }
                                                                                         }
                                                                                       }
                                                                                       }"""));
    assertEquals(FileLocalResolver.LightResolveResult.NON_LOCAL, configureAndResolve("""
                                                                                       record C(int field) {
                                                                                       {
                                                                                         new SuperClass() {
                                                                                         void foo() {
                                                                                           print(<caret>field);
                                                                                         }
                                                                                         }
                                                                                       }
                                                                                       }"""));
  }

  public void testNoMethodResolving() {
    assertDoesNotResolve("""
                           class C {
                             int a = 1;
                             { <caret>a(); }
                           }
                           """);
  }

  public void testNoQualifiedReferenceResolving() {
    assertDoesNotResolve("""
                           class C {
                             int a = 1;
                             Object b;
                             { b.<caret>a = null; }
                           }
                           """);
  }

  private void assertDoesNotResolve(String fileText) {
    assertEquals(FileLocalResolver.LightResolveResult.NON_LOCAL, configureAndResolve(fileText));
  }

  private void assertResolves(String fileText) {
    LighterASTNode result = configureAndResolve(fileText).getTarget();
    assertNotNull(result);
    assertEquals(findReference().resolve().getTextRange().getStartOffset(), result.getStartOffset());
  }

  private FileLocalResolver.LightResolveResult configureAndResolve(String fileText) {
    myFixture.configureByText("a.java", fileText);

    TreeBackedLighterAST astTree = new TreeBackedLighterAST(myFixture.getFile().getNode());
    LighterASTNode astRef = TreeBackedLighterAST.wrap(findReference().getElement().getNode());
    FileLocalResolver.LightResolveResult astResult = new FileLocalResolver(astTree).resolveLocally(astRef);

    PsiFile file = PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.java", JavaLanguage.INSTANCE, myFixture.getFile().getText(), false, false);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
    LighterAST fctsTree = file.getNode().getLighterAST();

    assertTrue(fctsTree instanceof FCTSBackedLighterAST);
    LighterASTNode fctsRef = findEquivalentNode(fctsTree, astRef);
    FileLocalResolver.LightResolveResult fctsResult = new FileLocalResolver(fctsTree).resolveLocally(fctsRef);
    assertTrue(fctsResult.equals(astResult) || nodesEqual(fctsResult.getTarget(), astResult.getTarget()));

    return astResult;
  }

  private LighterASTNode findEquivalentNode(final LighterAST fctsTree, final LighterASTNode target) {
    final Ref<LighterASTNode> fctsRef = Ref.create();
    new RecursiveLighterASTNodeWalkingVisitor(fctsTree) {
      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        if (nodesEqual(element, target)) fctsRef.set(element);
        super.visitNode(element);
      }
    }.visitNode(fctsTree.getRoot());
    assertNotNull(fctsRef.get());
    return fctsRef.get();
  }

  private static boolean nodesEqual(LighterASTNode node1, LighterASTNode node2) {
    return node1.getStartOffset() == node2.getStartOffset() &&
           node1.getEndOffset() == node2.getEndOffset() &&
           node1.getTokenType().equals(node2.getTokenType());
  }

  private PsiReference findReference() {
    return myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
  }

  public void testShortFieldTypeName() {
    assertEquals("Bar", getShortClassTypeName("""
                                                class Foo {
                                                  foo.Bar <caret>f;
                                                }"""));
  }

  public void testShortFieldTypeNameThatCoincidesWithATypeParameter() {
    assertEquals("Bar", getShortClassTypeName("""
                                                class Foo<Bar> {
                                                  foo.Bar <caret>f;
                                                }"""));
  }

  public void testGenericFieldTypeName() {
    assertNull(getShortClassTypeName("""
                                       class Foo<Bar> {
                                         Bar <caret>f;
                                       }"""));
  }

  public void testVariableTypeNameInAStaticMethod() {
    assertEquals("Bar", getShortClassTypeName("""
                                                class Foo<Bar> {
                                                  static void foo() { Bar <caret>f; }
                                                }"""));
  }

  public void testVariableTypeNameInAnInstanceMethod() {
    assertNull(getShortClassTypeName("""
                                       class Foo<Bar> {
                                         void foo() { Bar <caret>f; }
                                       }"""));
  }

  public void testParameterTypeNameInAGenericMethod() {
    assertNull(getShortClassTypeName("""
                                       class Foo<Bar> {
                                         <Bar> void foo(Bar <caret>b) { }
                                       }"""));
  }

  public void testMissingLambdaParameterTypeName() {
    assertNull(getShortClassTypeName("class Foo {{ I i = <caret>a -> a }}"));
  }

  public void testPrimitiveVarTypeName() {
    assertNull(getShortClassTypeName("class Foo {{ int <caret>i = 2; }}"));
  }

  public void testArrayVarTypeName() {
    assertNull(getShortClassTypeName("class Foo {{ String[] <caret>i = 2; }}"));
  }

  private String getShortClassTypeName(String text) {
    myFixture.configureByText("a.java", text);
    PsiVariable var =
      PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), PsiVariable.class,
                                             false);
    TreeBackedLighterAST tree = new TreeBackedLighterAST(myFixture.getFile().getNode());
    LighterASTNode varNode = TreeBackedLighterAST.wrap(var.getNode());
    return new FileLocalResolver(tree).getShortClassTypeName(varNode);
  }
}
