package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class FragmentCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testDontCompleteFieldsAndMethodsInReferenceCodeFragment() {
    final String text = CommonClassNames.JAVA_LANG_OBJECT + ".<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment(text, null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertEquals(0, myFixture.completeBasic().length);
    myFixture.checkResult(text);
  }

  public void testNoKeywordsInReferenceCodeFragment() {
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment("<caret>", null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertNotNull(myFixture.completeBasic());
    assertFalse(myFixture.getLookupElementStrings().contains("package"));
    assertFalse(myFixture.getLookupElementStrings().contains("import"));
  }

  public void test_no_classes_in_reference_code_fragment() {
    myFixture.addClass("package foo; public interface FooIntf { }");

    String text = "FooInt<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment(text, null, true, false);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertEquals(0, myFixture.complete(CompletionType.BASIC, 2).length);
    myFixture.checkResult(text);
  }

  @NeedsIndex.Full
  public void testPreferClassOverPackage() {
    myFixture.addClass("package Xyz; public class Xyz {}");

    String text = "Xy<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment(text, null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals(2, elements.length);
    assertTrue(elements[0].getPsiElement() instanceof PsiClass);
    assertTrue(elements[1].getPsiElement() instanceof PsiPackage);
  }

  public void test_no_constants_in_reference_code_fragment() {
    myFixture.addClass("package foo; public interface FooIntf { int constant = 2; }");

    String text = "FooInt.con<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment(text, null, true, false);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertEquals(0, myFixture.complete(CompletionType.BASIC, 2).length);
    myFixture.checkResult(text);
  }

  public void testNoPackagesInExpressionCodeFragment() {
    final String text = "jav<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment(text, null, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertEquals(0, myFixture.completeBasic().length);
    myFixture.checkResult(text);
  }

  public void testSubPackagesInExpressionCodeFragment() {
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("java.la<caret>", null, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertNull(myFixture.completeBasic());
    myFixture.checkResult("java.lang.<caret>");
  }

  public void testPrimitivesInTypeCodeFragmentWithParameterListContext() {
    PsiClass clazz = myFixture.addClass("class Foo { void foo(int a) {} }");

    PsiFile file =
      JavaCodeFragmentFactory.getInstance(getProject()).createTypeCodeFragment("b<caret>", clazz.getMethods()[0].getParameterList(), true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    assertEquals(List.of("boolean", "byte"), myFixture.getLookupElementStrings().subList(0, 2));
  }

  @NeedsIndex.ForStandardLibrary
  public void testQualifierCastingInExpressionCodeFragment() {
    final String ctxText = "class Bar {{ Object o; o=null }}";
    final PsiFile ctxFile = createLightFile(JavaFileType.INSTANCE, ctxText);
    final PsiElement context = ctxFile.findElementAt(ctxText.indexOf("o="));
    assertNotNull(context);

    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject())
      .createExpressionCodeFragment("o instanceof String && o.subst<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertNull(myFixture.completeBasic());
    myFixture.checkResult("o instanceof String && ((String) o).substring(<caret>)");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoGenericQualifierCastingWithRuntimeType() {
    final String ctxText = "import java.util.*; class Bar {{ Map<Integer,Integer> map = new HashMap<Integer,Integer>(); map=null; }}";
    final PsiFile ctxFile = createLightFile(JavaFileType.INSTANCE, ctxText);
    final PsiElement context = ctxFile.findElementAt(ctxText.indexOf("map="));
    assertNotNull(context);

    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("map.entry<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.getFile().putCopyableUserData(
      JavaCompletionUtil.DYNAMIC_TYPE_EVALUATOR,
      (t, v) -> JavaPsiFacade.getElementFactory(t.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_UTIL_HASH_MAP));
    assertNull(myFixture.completeBasic());
    myFixture.checkResult("map.entrySet()<caret>");
  }

  public void test_no_static_after_instance_in_expression_fragment() {
    //noinspection ClassInitializerMayBeStatic
    PsiFile ctxFile = myFixture.addClass("package foo; public class Class {{\n int a = 2; }}").getContainingFile();
    PsiElement context = ctxFile.findElementAt(ctxFile.getText().indexOf("int"));

    String text = "Double.valueOf(2).v<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment(text, context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    assertFalse(myFixture.getLookupElementStrings().contains("valueOf"));
  }

  public void test_no_class_keywords_in_expression_fragment() {
    //noinspection ClassInitializerMayBeStatic
    PsiFile ctxFile = myFixture.addClass("package foo; public class Class {{\n int a = 2; }}").getContainingFile();
    PsiElement context = ctxFile.findElementAt(ctxFile.getText().indexOf("int"));

    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    assertFalse(myFixture.getLookupElementStrings().contains("enum"));
    assertFalse(myFixture.getLookupElementStrings().contains("class"));
  }

  @NeedsIndex.ForStandardLibrary
  public void test_annotation_context() {
    PsiFile ctxFile = myFixture.addClass("class Class { void foo(int context) { @Anno int a; } }").getContainingFile();
    PsiElement context = ctxFile.findElementAt(ctxFile.getText().indexOf("Anno"));
    PsiFile file = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("c<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().contains("context"));
  }

  @NeedsIndex.Full
  public void test_proximity_ordering_in_scratch_like_file() {
    PsiClass barField = myFixture.addClass("package bar; public class Field {}");
    PsiClass fooField = myFixture.addClass("package foo; public class Field {}");
    String text = "import foo.Field; class C { Field<caret> }";
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", JavaLanguage.INSTANCE, text, true, false);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    LookupElement[] items = myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "Field", "Field");
    assertEquals(fooField, items[0].getObject());
    assertEquals(barField, items[1].getObject());
  }

  @NeedsIndex.ForStandardLibrary
  public void test_package_default_class_in_code_fragment() {
    myFixture.addClass("class ABCD {}");
    PsiJavaCodeReferenceCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment("ABC<caret>", null, true, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.complete(CompletionType.BASIC);
    assertTrue(ContainerUtil.exists(myFixture.getLookupElements(), it -> it.getLookupString().equals("ABCD")));
  }

  @NeedsIndex.Full
  public void test_qualified_class_in_code_fragment() {
    myFixture.addClass("package foo; public class Foo1 {}");
    PsiJavaCodeReferenceCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment("Foo<caret>", null, true, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.completeBasic();
    assertTrue(ContainerUtil.exists(myFixture.getLookupElements(), it -> it.getLookupString().equals("Foo1")));
    myFixture.type("\n");
    myFixture.checkResult("foo.Foo1");
  }
}
