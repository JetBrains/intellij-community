package com.intellij.codeInsight.completion;


import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

public class NormalCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/";
  }

  public void testSimple() throws Exception {
    configureByFile("Simple.java");
    assertStringItems("_local1", "_local2", "_field", "_method", "_baseField", "_baseMethod");
  }

  public void testDontCompleteFieldsAndMethodsInReferenceCodeFragment() throws Throwable {
    final String text = CommonClassNames.JAVA_LANG_OBJECT + ".<caret>";
    PsiFile file = getJavaFacade().getElementFactory().createReferenceCodeFragment(text, null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    complete();
    myFixture.checkResult(text);
    assertEmpty(myItems);
  }

  private JavaPsiFacade getJavaFacade() {
    return JavaPsiFacade.getInstance(getProject());
  }

  public void testCastToPrimitive1() throws Exception {
    configureByFile("CastToPrimitive1.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testCastToPrimitive2() throws Exception {
    configureByFile("CastToPrimitive2.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testCastToPrimitive3() throws Exception {
    configureByFile("CastToPrimitive3.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testWriteInInvokeLater() throws Exception {
    configureByFile("WriteInInvokeLater.java");
  }

  public void testQualifiedNew1() throws Exception {
    configureByFile("QualifiedNew1.java");

    assertEquals(2, myItems.length);
    assertEquals("IInner", myItems[0].getLookupString());
    assertEquals("Inner", myItems[1].getLookupString());
  }

  public void testQualifiedNew2() throws Exception {
    configureByFile("QualifiedNew2.java");

    assertEquals(2, myItems.length);
    assertEquals("AnInner", myItems[0].getLookupString());
    assertEquals("Inner", myItems[1].getLookupString());
  }

  public void testKeywordsInName() throws Exception {
    configureByFile("KeywordsInName.java");
    checkResultByFile("KeywordsInName_after.java");
  }

  public void testSimpleVariable() throws Exception {
    configureByFile("SimpleVariable.java");
    checkResultByFile("SimpleVariable_after.java");
  }

  public void testPreferLongerNamesOption() throws Exception {
    configureByFile("PreferLongerNamesOption.java");

    assertEquals(3, myItems.length);
    assertEquals("abcdEfghIjk", myItems[0].getLookupString());
    assertEquals("efghIjk", myItems[1].getLookupString());
    assertEquals("ijk", myItems[2].getLookupString());

    LookupManager.getInstance(getProject()).hideActiveLookup();

    CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = false;
    try{
      configureByFile("PreferLongerNamesOption.java");

      assertEquals(3, myItems.length);
      assertEquals("ijk", myItems[0].getLookupString());
      assertEquals("efghIjk", myItems[1].getLookupString());
      assertEquals("abcdEfghIjk", myItems[2].getLookupString());
    }
    finally{
      CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = true;
    }
  }

  public void testSCR7208() throws Exception {
    configureByFile("SCR7208.java");
  }

  public void testProtectedFromSuper() throws Exception {
    configureByFile("ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0);
  }

  public void testBeforeInitialization() throws Exception {
    configureByFile("BeforeInitialization.java");
    assertNotNull(myItems);
    assertTrue(myItems.length > 0);
  }

  public void testProtectedFromSuper2() throws Exception {

    configureByFile("ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0);
  }

  public void testReferenceParameters() throws Exception {
    configureByFile("ReferenceParameters.java");
    assertNotNull(myItems);
    assertEquals(myItems.length, 2);
    assertEquals(myItems[0].getLookupString(), "AAAA");
    assertEquals(myItems[1].getLookupString(), "AAAB");
  }

  public void testConstructorName1() throws Exception{
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean autocomplete_on_code_completion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configureByFile("ConstructorName1.java");
    assertNotNull(myItems);
    boolean failed = true;
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("ABCDE")) {
        failed = false;
      }
    }
    assertFalse(failed);
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete_on_code_completion;
  }

  public void testConstructorName2() throws Exception{
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean autocomplete_on_code_completion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configureByFile("ConstructorName2.java");
    assertNotNull(myItems);
    boolean failed = true;
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("ABCDE")) {
        failed = false;
      }
    }
    assertFalse(failed);
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete_on_code_completion;
  }

  public void testObjectsInThrowsBlock() throws Exception {
    configureByFile("InThrowsCompletion.java");

    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "C") > 0);
    assertFalse("Found not an Exception", Arrays.binarySearch(myItems, "B") > 0);
  }

  public void testAfterInstanceof() throws Exception {
    configureByFile("AfterInstanceof.java");

    assertNotNull(myItems);
    Arrays.sort(myItems);
    assertTrue("Classes not found after instanceof", Arrays.binarySearch(myItems, "A") >= 0);
  }

  public void testAfterCast1() throws Exception {
    configureByFile("AfterCast1.java");

    assertNotNull(myItems);
    assertEquals(2, myItems.length);
  }

  public void testAfterCast2() throws Exception {
    configureByFile("AfterCast2.java");
    checkResultByFile("AfterCast2-result.java");
  }

  public void testMethodCallForTwoLevelSelection() throws Exception {
    configureByFile("MethodLookup.java");
    assertEquals(2, myItems.length);
  }

   public void testMethodCallBeforeAnotherStatementWithParen() throws Exception {
     configureByFile("MethodLookup2.java");
     checkResultByFile("MethodLookup2_After.java");
  }

   public void testMethodCallBeforeAnotherStatementWithParen2() throws Exception {
     CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings();
     boolean oldvalue = settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE;
     settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
     configureByFile("MethodLookup2.java");
     checkResultByFile("MethodLookup2_After2.java");
     settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = oldvalue;
  }

  public void testSwitchEnumLabel() throws Exception {
    configureByFile("SwitchEnumLabel.java");
    assertEquals(3, myItems.length);
  }

  public void testMethodInAnnotation() throws Exception {
    configureByFile("Annotation.java");
    checkResultByFile("Annotation_after.java");
  }

  public void testMethodInAnnotation2() throws Exception {
    configureByFile("Annotation2.java");
    checkResultByFile("Annotation2_after.java");
  }

  public void testMethodInAnnotation3() throws Exception {

    configureByFile("Annotation3.java");
    checkResultByFile("Annotation3_after.java");
  }

  public void testMethodInAnnotation5() throws Exception {

    configureByFile("Annotation5.java");
    checkResultByFile("Annotation5_after.java");
  }

  public void testMethodInAnnotation7() throws Exception {

    configureByFile("Annotation7.java");
    selectItem(myItems[0]);
    checkResultByFile("Annotation7_after.java");
  }

  public void testEnumInAnnotation() throws Exception {
    configureByFile("Annotation4.java");
    checkResultByFile("Annotation4_after.java");
  }

  public void testSecondAttribute() throws Exception {
    configureByFile("Annotation6.java");
    checkResultByFile("Annotation6_after.java");
  }

  public void testIDEADEV6408() throws Exception {
    configureByFile("IDEADEV6408.java");
    assertEquals(2, myItems.length);
  }

  public void testMethodWithLeftParTailType() throws Exception {
    configureByFile("MethodWithLeftParTailType.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType_after.java");

    configureByFile("MethodWithLeftParTailType2.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType2_after.java");
  }

  public void testMethodWithLeftParTailTypeNoPairBrace() throws Exception {
    final boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    try {
      configureByFile(getTestName(false) + ".java");
      type('(');
      checkResultByFile(getTestName(false) + "_after.java");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testMethodWithLeftParTailTypeNoPairBrace2() throws Exception {
    final boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    try {
      //no tail type should work the normal way
      configureByFile("MethodWithLeftParTailTypeNoPairBrace.java");
      selectItem(myItems[0]);
      checkResultByFile("MethodWithLeftParTailTypeNoPairBrace_after2.java");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testExcessSpaceInTypeCast() throws Throwable {
   configureByFile(getTestName(false) + ".java");
   selectItem(myItems[0]);
   checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testPackageInAnnoParam() throws Throwable {
    doTest();
  }
  
  public void testClassLiteralInAnnoParam() throws Throwable {
    doTest();
  }

  public void testAtUnderClass() throws Throwable {
    doTest();
  }

  public void testAtUnderClassNoModifiers() throws Throwable {
    doTest();
  }

  public void testLastExpressionInFor() throws Throwable { doTest(); }

  public void testUndoCommonPrefixOnHide() throws Throwable {//actually don't undo
    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + "_after.java");
    LookupManager.getInstance(getProject()).hideActiveLookup();
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testOnlyKeywordsInsideSwitch() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("case", "default");
  }

  public void testBooleanLiterals() throws Throwable {
    doTest();
  }

  public void testNotOnlyKeywordsInsideSwitch() throws Throwable {
    doTest();
  }

  public void testChainedCallOnNextLine() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testFinishWithDot() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('.');
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testEnclosingThis() throws Throwable { doTest(); }

  public void testSeamlessConstant() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testDefaultAnnoParam() throws Throwable { doTest(); }

  public void testSpaceAfterLookupString() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type(' ');
    assertNull(getLookup());
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testNoSpaceInParensWithoutParams() throws Throwable {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try {
      doTest();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }
  
  public void testTwoSpacesInParensWithParams() throws Throwable {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try {
      doTest();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }

  public void testFillCommonPrefixOnSecondCompletion() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('g');
    complete();
    checkResultByFile(getTestName(false) + "_after.java");
    assertStringItems("getBar", "getFoo", "getClass");
  }

  public void testQualifierAsPackage() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testQualifierAsPackage2() throws Throwable {
    doTest();
  }
  
  public void testQualifierAsPackage3() throws Throwable {
    doTest();
  }
  
  public void testPackageNamedVariableBeforeAssignment() throws Throwable {
    doTest();
  }

  public void testMethodReturnType() throws Throwable {
    doTest();
  }

  public void testMethodReturnTypeNoSpace() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testEnumWithoutConstants() throws Throwable {
    doTest();
  }

  public void testDoWhileMethodCall() throws Throwable {
    doTest();
  }

  public void testSecondTypeParameterExtends() throws Throwable {
    doTest();
  }

  public void testGetterWithExistingNonEmptyParameterList() throws Throwable {
    doTest();
  }

  public void testNoAllClassesOnQualifiedReference() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    checkResultByFile(getTestName(false) + ".java");
  }

  public void testFinishClassNameWithDot() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('.');
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testFinishClassNameWithLParen() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('(');
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testSelectNoParameterSignature() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    final int parametersCount = ((PsiMethod)getLookup().getCurrentItem().getObject()).getParameterList().getParametersCount();
    assertEquals(0, parametersCount);
    getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testCompletionInsideClassLiteral() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testSuperInConstructor() throws Throwable {
    doTest();
  }

  public void testSuperInConstructorWithParams() throws Throwable {
    doTest();
  }

  public void testSuperInMethod() throws Throwable {
    doTest();
  }

  public void testSecondMethodParameterName() throws Throwable {
    doTest();
  }

  public void testAnnotationAsUsualObject() throws Throwable {
    doTest();
  }

  public void testAnnotationAsUsualObjectFromJavadoc() throws Throwable {
    doTest();
  }

  public void testAnnotationAsUsualObjectInsideClass() throws Throwable {
    doTest();
  }

  public void testAnnotationOnNothingParens() throws Throwable {
    doTest();
  }

  public void testMultiResolveQualifier() throws Throwable {
    doTest();
  }

  public void testSecondMethodParameter() throws Throwable { doTest(); }

  public void testAnnotationWithoutValueMethod() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("bar", "foo");
  }

  public void testUnnecessaryMethodMerging() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("fofoo", "fofoo");
  }

  public void testDontCancelPrefixOnTyping() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('~');
    assertNull(getLookup());
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testAnnotationQualifiedName() throws Throwable {
    doTest();
  }

  public void testDoubleFalse() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("false", "finalize");
  }

  public void testSameNamedVariableInNestedClasses() throws Throwable {
    doTest();
    assertNull(getLookup());
  }

  public void testHonorUnderscoreInPrefix() throws Throwable {
    doTest();
  }

  public void testCaseTailType() throws Throwable { doTest(); }

  public void testSecondInvocationToFillCommonPrefix() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('a');
    complete();
    assertStringItems("fai1", "fai2", "fai3");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testSuggestInaccessibleOnSecondInvocation() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("_bar", "_goo");
    complete();
    assertStringItems("_bar", "_goo", "_foo");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testNoCommonPrefixInsideIdentifier() throws Throwable {
    final String path = getTestName(false) + ".java";
    configureByFile(path);
    checkResultByFile(path);
    assertStringItems("fai1", "fai2");
  }

  public void testProtectedInaccessibleOnSecondInvocation() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testPropertyReferencePrefix() throws Throwable {
    myFixture.addFileToProject("test.properties", "foo.bar=Foo! Bar!").getVirtualFile();

    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + ".java");
    assertNull(getLookup());
  }

  private void doTest() throws Exception {
    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doAntiTest() throws Exception {
    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  public void testSecondAnonymousClassParameter() throws Throwable { doTest(); }

  public void testCastInstanceofedQualifier() throws Throwable { doTest(); }
  public void testCastComplexInstanceofedQualifier() throws Throwable { doTest(); }

  public void testCastTooComplexInstanceofedQualifier() throws Throwable { doAntiTest(); }
  public void testDontCastInstanceofedQualifier() throws Throwable { doTest(); }

  public void testWildcardsInLookup() throws Exception {
    configureByFile(getTestName(false) + ".java");
    assertNotNull(getLookup());
    type('*');
    type('f');
    type('z');
    final List<LookupElement> list = getLookup().getItems();
    assertEquals("azzzfzzz", list.get(0).getLookupString());
    assertEquals("fzazzz", list.get(1).getLookupString());
  }

  public void testMethodParameterAnnotationClass() throws Throwable { doTest(); }

  public void testEnumConstantFromEnumMember() throws Throwable { doTest(); }

  public void testPrimitiveMethodParameter() throws Throwable { doTest(); }

  public void testRightShift() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("myField1", "myField2");
  }

  public void testSuggestMembersOfStaticallyImportedClasses() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    myFixture.configureByText("a.java", """
    import static foo.Foo.foo;

    class Bar {{
      foo();
      ba<caret>
    }}
    """)
    complete()
    myFixture.checkResult """
    import static foo.Foo.bar;
    import static foo.Foo.foo;

    class Bar {{
      foo();
      bar();<caret>
    }}
    """
  }

  public void testSuggestMembersOfStaticallyImportedClassesUnqualifiedOnly() throws Exception {
    def old = CodeInsightSettings.instance.SHOW_STATIC_AFTER_INSTANCE
    CodeInsightSettings.instance.SHOW_STATIC_AFTER_INSTANCE = true

    try {
      myFixture.addClass("""package foo;
      public class Foo {
        public static void foo() {}
        public static void bar() {}
      }
      """)
      myFixture.configureByText("a.java", """
      import foo.Foo;
      import static foo.Foo.foo;

      class Bar {{
        foo();
        new Foo().ba<caret>z
      }}
      """)
      complete()
      assertOneElement(myFixture.getLookupElements())
      myFixture.type '\t'
      myFixture.checkResult """
      import foo.Foo;
      import static foo.Foo.foo;

      class Bar {{
        foo();
        new Foo().bar();<caret>
      }}
      """
    }
    finally {
      CodeInsightSettings.instance.SHOW_STATIC_AFTER_INSTANCE = old
    }
  }

  public void testInstanceMagicMethod() throws Exception {
    myFixture.configureByText("a.java", """
      public class JavaClass {
          <T> T magic() {}

          void foo() {
              mag<caret>
          }
      }
      """)
    myFixture.completeBasic()
    myFixture.checkResult """
      public class JavaClass {
          <T> T magic() {}

          void foo() {
              magic()<caret>
          }
      }
      """
  }

}
