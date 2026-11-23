// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.TypeMigrationTestUtil.RulesProvider;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestAnonymousClassTypeParameters;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFieldType;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFieldsType;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFirstParamType;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestMethodType;
import static com.intellij.refactoring.TypeMigrationTestUtil.start;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TypeMigrationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, TypeMigrationTestUtil.getTestDataPath() + "/refactoring/typeMigration/");

  private PsiElementFactory myFactory;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.HIGHEST);
    myFactory = JavaPsiFacade.getElementFactory(myFixture.getProject());
  }

  @Test
  public void testStringCompoundAssignment() {
    doTestFirstParamType(multiFile.get(), "x", PsiTypes.longType());
  }

  @Test
  public void testForeachProblem() {
    doTestFirstParamType(multiFile.get(), "x", PsiTypes.longType());
  }

  @Test
  public void testEnumConstant() {
    doTestFirstParamType(multiFile.get(), "Test", PsiTypes.byteType());
  }

  @Test
  public void testMigrateEnumType() {
    doTestFieldType(multiFile.get(), "someEnum", PsiTypes.intType());
  }

  @Test
  public void testVarargsAndBoxing() {
    doTestFieldType(multiFile.get(), "x", PsiTypes.longType());
  }

  @Test
  public void testArray2Vararg() {
    doTestFirstParamType(multiFile.get(), "doSomething", new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.CharSequence", null))));
  }

  @Test
  public void testVararg2Array() {
    doTestFirstParamType(multiFile.get(), "m", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Integer[]", null)));
  }

  @Test
  public void testIntVararg2LongArray() {
    doTestFirstParamType(multiFile.get(), "two", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("long[]", null)));
  }

  @Test
  public void testInt2Array() {
    doTestReturnType("x", "int[][]");
  }

  @Test
  public void testArray2Int() {
    doTestFirstParamType(multiFile.get(), "y", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("int", null)));
  }

  @Test
  public void testT07() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null).createArrayType()));
  }

  @Test
  public void testT08() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT09() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT10() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

  @Test
  public void testT11() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Map<java.lang.String, java.lang.Integer>", null)));
  }

  @Test
  public void testT12() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

  @Test
  public void testT13() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null)));
  }

  @Test
  public void testT14() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("A", null)));
  }

  //do not touch javadoc refs etc
  @Test
  public void testT15() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("A", null)));
  }

  //do not touch signature with method type parameters
  @Test
  public void testT16() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("B", null)));
  }

  //change method signature inspired by call on parameters
  @Test
  public void testT17() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("B", null)));
  }

  //extending iterable -> used in foreach statement
  @Test
  public void testT18() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("B", null)));
  }

  @Test
  public void testT19() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.HashMap<java.lang.Integer, java.lang.Integer>", null)));
  }

  @Test
  public void testT20() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Map<java.lang.String, java.lang.String>", null)));
  }

  @Test
  public void testT21() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Map<java.lang.String, java.util.Set<java.lang.String>>",
                                                 null))
    );
  }

  //varargs : removed after migration?!
  @Test
  public void testT22() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  //substitution from super class: type params substitution needed
  @Test
  public void testT23() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("HashMap<java.lang.String, java.util.List<java.lang.String>>", null)));
  }

  //check return type unchanged when it is possible
  @Test
  public void testT24() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("D", null)));
  }

  @Test
  public void testT25() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("D", null)));
  }

  //check param type change
  @Test
  public void testT26() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("D", null)));
  }

  @Test
  public void testT27() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("D", null)));
  }

  //list --> array
  @Test
  public void testT28() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT29() {
    doTestMethodType(multiFile.get(), "get",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

  @Test
  public void testT30() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT31() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  //non code usages
  @Test
  public void testT32() {
    doTestFirstParamType(multiFile.get(), "bar",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("int", null)));
  }

  //change type arguments for new expressions: l = new ArrayList<String>() -> l = new ArrayList<Integer>()
  @Test
  public void testT33() {
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null)));
  }

  //new expression new ArrayList<String>() should be left without modifications
  @Test
  public void testT34() {
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.AbstractList<java.lang.String>", null)));
  }

  @Test
  public void testT35() {
    doTestFieldType(multiFile.get(), "myParent",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("TestImpl", null)));
  }

  //co-variant/contra-variant positions for primitive types 36-39
  @Test
  public void testT36() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.byteType());
  }

  @Test
  public void testT37() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.intType());
  }

  @Test
  public void testT38() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.longType());
  }

  @Test
  public void testT39() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.byteType());
  }

  //Set s = new HashSet() -> HashSet s = new HashSet();
  @Test
  public void testT40() {
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.ArrayList", null)));
  }

  //Set s = new HashSet<String>() -> HashSet s = new HashSet<String>();
  @Test
  public void testT41() {
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.ArrayList", null)));
  }

  //Set s = new HashSet() -> HashSet<String> s = new HashSet();
  @Test
  public void testT42() {
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.ArrayList<java.lang.String>", null)));
  }

  //long l; Object o = l -> long l; Long o = l;
  @Test
  public void testT43() {
    doTestFieldType(multiFile.get(), "o",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Long", null)));
  }

  //long l; int  i; l = i; -> long l; byte i; l = i;
  @Test
  public void testT44() {
    doTestFieldType(multiFile.get(), "i", PsiTypes.byteType());
  }

  //long l; int i; l = i; -> byte l; -> byte i; l = i;
  @Test
  public void testT45() {
    doTestFieldType(multiFile.get(), "l", PsiTypes.byteType());
  }

  //byte i; long j = i; -> byte i; int j = i;
  @Test
  public void testT46() {
    doTestFieldType(multiFile.get(), "j", PsiTypes.intType());
  }

  //o = null -? int o = null
  @Test
  public void testT47() {
    doTestFieldType(multiFile.get(), "o", PsiTypes.intType());
  }

  //co-variant/contra-variant assignments: leave types if possible change generics signature only  48-49
  // foo(AbstractSet<String> s){Set<String> ss = s} -> foo(AbstractSet<Integer> s){Set<Integer> ss = s}
  @Test
  public void testT48() {
    doTestFirstParamType(multiFile.get(), "foo",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.AbstractSet<B>", null)));
  }

  // Set<String> f; foo(AbstractSet<String> s){f = s} -> Set<Integer>f; foo(AbstractSet<Integer> s){f = s}
  @Test
  public void testT49() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<B>", null)));
  }

  //captured wildcard: Set<? extends JComponent> s; Set<? extends JComponent> c1 = s; ->
  //  Set<? extends JButton> s; Set<? extends JButton> c1 = s;
  @Test
  public void testT50() {
    doTestFieldType(multiFile.get(), "c1",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<? extends JButton>", null)));
  }

  //array initialization: 51-52
  @Test
  public void testT51() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType()));
  }

  @Test
  public void testT52() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType()));
  }

  //generic type promotion to array initializer
  @Test
  public void testT53() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<java.lang.String>", null).createArrayType()));
  }

  //wildcard type promotion to expressions 54-55
  @Test
  public void testT54() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<? extends java.lang.Integer>", null)));
  }

  @Test
  public void testT55() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<?>", null)));
  }

  //array index should be integer 56-57
  @Test
  public void testT56() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.doubleType());
  }

  @Test
  public void testT57() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.byteType());
  }

  //Arrays can be assignable to Object/Serializable/Cloneable 58-59; ~ 60 varargs
  @Test
  public void testT58() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  @Test
  public void testT59() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Cloneable", null)));
  }

  @Test
  public void testT60() {
    doTestFieldType(multiFile.get(), "p",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  //change parameter type -> vararg; assignment changed to array
  @Test
  public void testT61() {
    doTestFirstParamType(multiFile.get(), "foo", new PsiEllipsisType(PsiTypes.intType()));
  }

  //change field type -> change vararg parameter type due to assignment: 62-63
  @Test
  public void testT62() {
    doTestFieldType(multiFile.get(), "p", ReadAction.computeBlocking(() -> myFactory.createTypeFromText(
      CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  @Test
  public void testT63() {
    doTestFieldType(multiFile.get(), "p", PsiTypes.doubleType().createArrayType());
  }

  //remove vararg type: 64-66
  @Test
  public void testT64() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.intType());
  }

  @Test
  public void testT65() {
    doTestFirstParamType(multiFile.get(), "foo",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT115() {
    doTestFirstParamType(multiFile.get(), "foo",
                         new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null))));
  }

  @Test
  public void testT66() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.intType());
  }

  @Test
  public void testT67() {
    doTestFirstParamType(multiFile.get(), "methMemAcc",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT68() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.doubleType());
  }

  @Test
  public void testT69() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.byteType());
  }

  @Test
  public void testT70() {
    doTestFieldType(multiFile.get(), "a", PsiTypes.floatType().createArrayType().createArrayType());
  }

  @Test
  public void testT71() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Class<? extends java.lang.Number>", null)));
  }

  @Test
  public void testT72() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Class<java.lang.Integer>", null)));
  }

  @Test
  public void testT73() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<java.awt.Component>", null).createArrayType().createArrayType()));
  }

  //prefix/postfix expression; binary expressions 74-76
  @Test
  public void testT74() {
    doTestFirstParamType(multiFile.get(), "meth", PsiTypes.floatType());
  }

  @Test
  public void testT75() {
    doTestFirstParamType(multiFile.get(), "meth", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT76() {
    doTestFirstParamType(multiFile.get(), "meth", PsiTypes.floatType());
  }

  //+= , etc 77-78
  @Test
  public void testT77() {
    doTestFirstParamType(multiFile.get(), "meth", ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  @Test
  public void testT78() {
    doTestFirstParamType(multiFile.get(), "meth", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  //casts 79-80,83
  @Test
  public void testT79() {
    doTestFirstParamType(multiFile.get(), "meth", PsiTypes.byteType());
  }

  @Test
  public void testT80() {
    doTestFirstParamType(multiFile.get(), "meth", PsiTypes.doubleType());
  }

  @Test
  public void testT83() {
    doTestFirstParamType(multiFile.get(), "meth", ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  //instanceofs 81-82
  @Test
  public void testT81() {
    doTestFirstParamType(multiFile.get(), "foo",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("A", null)));
  }

  @Test
  public void testT82() {
    doTestFirstParamType(multiFile.get(), "foo",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("C", null)));
  }

  @Test
  public void testT84() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<? extends java.util.Set>", null)));
  }

  @Test
  public void testT85() {
    doTestFieldType(multiFile.get(), "str",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  //array <-> list 86-89;94;95
  @Test
  public void testT86() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

   @Test
   public void testT87() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT88() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

  @Test
  public void testT89() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT94() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testT95() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

  @Test
  public void testT90() {
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<A>", null)));
  }

  //element type -> element type array
  @Test
  public void testT91() {
    doTestMethodType(multiFile.get(), "foo",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  //List<S>=new ArrayList<S>{}; -> List<I>=new ArrayList<I>{}; anonymous
  @Test
  public void testT92() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null)));
  }

  //generics signature do not support primitives: Map<Boolean, String> - Map<boolean, String>
  @Test
  public void testT93() {
    doTestFirstParamType(multiFile.get(), "foo", PsiTypes.booleanType());
  }

  //field initializers procession
  @Test
  public void testT96() {
    doTestFieldType(multiFile.get(), "f1",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT97() {
    doTestFieldType(multiFile.get(), "f1", PsiTypes.intType());
  }

  //list <-> array conversion in assignment statements
  @Test
  public void testT98() {
    doTestMethodType(multiFile.get(), "getArray",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.String>", null)));
  }

  //escape pattern from []
  @Test
  public void testT99() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<java.util.List<int[]>>", null)));
  }

  //non formatted type
  @Test
  public void testT100() {
    doTestFieldType(multiFile.get(), "f",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Map<java.lang.String,java.lang.Integer>", null)));
  }

  //param List -> Array[]
  @Test
  public void testT101() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.ArrayList<java.lang.Integer>[]", null)));
  }

  //param Set.add() -> Array[] with conflict
  @Test
  public void testT102() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Object[]", null)));
  }

  //set(1, "") should be assignment-checked over String
  @Test
  public void testT103() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null).createArrayType()));
  }

   //raw list type now should not be changed
  @Test
  public void testT104() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null).createArrayType()));
  }

  //implicit type parameter change 105-107
  @Test
  public void testT105() {
    doTestFieldType(multiFile.get(), "t",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT106() {
    doTestFieldType(multiFile.get(), "t",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT107() {
    doTestFieldType(multiFile.get(), "t",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  //foreach && wildcards: 108-110
  @Test
  public void testT108() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<? extends java.lang.Number>", null)));
  }

  @Test
  public void testT109() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<? super java.lang.Number>", null)));
  }

  @Test
  public void testT110() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<? extends java.lang.String>", null)));
  }

  //wrap with array creation only literals and refs outside of binary/unary expressions
  @Test
  public void testT111() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null).createArrayType()));
  }

  @Test
  public void testT112() {
    doTestMethodType(multiFile.get(), "method",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null).createArrayType()));
  }

  //varargs
  @Test
  public void testT113() {
    doTestFirstParamType(multiFile.get(), "method",
                         new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Number", null))));
  }

  @Test
  public void testT114() {
      doTestFirstParamType(multiFile.get(), "method",
                           new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null))));
    }

  //varargs && ArrayList
  @Test
  public void testT118() {
    doTestFirstParamType(multiFile.get(), "method",
                         new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null))));
  }

  //varargs && arrays
  @Test
  public void testT119() {
    doTestFirstParamType(multiFile.get(), "method",
                         new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null))));
  }

  @Test
  public void testT120() {
    doTestFirstParamType(multiFile.get(), "method",
                         new PsiEllipsisType(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null))));
  }

  //change parameter type in foreach statement: 116 - array, 117 - list
  @Test
  public void testT116() {
    doTestFieldType(multiFile.get(), "str",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT117() {
    doTestFieldType(multiFile.get(), "str",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT121() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.ArrayList<java.lang.Float>", null)));
  }

  @Test
  public void testT122() {
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null).createArrayType()));
  }

  @Test
  public void testT123() {
    doTestFieldType(multiFile.get(), "n",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  //124,125 - do not change formal method return type
  @Test
  public void testT124() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  @Test
  public void testT125() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  @Test
  public void testT126() {
    doTestMethodType(multiFile.get(), "meth",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("T", null)));
  }

  // Checking preserving method parameters alignment
  @Test
  public void testT127() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_PARAMETERS = true;
    javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTestMethodType(multiFile.get(), "test234",
                     ReadAction.computeBlocking(() -> myFactory.createTypeFromText("long", null)));
  }

  // test type migration from disjunction type
  @Test
  public void testT128() {
    doTestCatchParameter(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E1 | Test.E2", null)),
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E", null)));
  }

  // test type migration to disjunction type
  @Test
  public void testT129() {
    doTestCatchParameter(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E", null)),
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E1 | Test.E2", null)));
  }

  // test type migration from disjunction type with interfaces
  @Test
  public void testT130() {
    doTestCatchParameter(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E1 | Test.E2", null)),
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E", null)));
  }

  // test type migration between disjunction types
  @Test
  public void testT131() {
    doTestCatchParameter(ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E1 | Test.E2", null)),
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.E2 | Test.E1", null)));
  }

  private void doTestCatchParameter(final PsiType rootType, final PsiType migrationType) {
    start(multiFile.get(), new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return migrationType;
      }

      @Override
      public PsiElement victim(final PsiClass aClass) {
        final PsiCatchSection catchSection = PsiTreeUtil.findChildOfType(aClass, PsiCatchSection.class);
        assert catchSection != null : aClass.getText();
        final PsiParameter parameter = catchSection.getParameter();
        assert parameter != null : catchSection.getText();
        return parameter;
      }
    });
  }

  // IDEA-72420
  @Test
  public void testT132() {
    doTestFirstParamType(multiFile.get(), "h", "Test",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("I", null)));
  }

  @Test
  public void testT133() {
    doTestFirstParamType(multiFile.get(), "h", "Test",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("I", null)));
  }

  @Test
  public void testT134() {
    doTestFirstParamType(multiFile.get(), "buzz", "Test",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT135() {
    doTestFieldType(multiFile.get(), "foo", "Test", PsiTypes.intType());
  }

  @Test
  public void testT136() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    doTestFirstParamType(multiFile.get(), "foo", "Test",
                         ReadAction.computeBlocking(() -> PsiType.getJavaLangString(PsiManager.getInstance(myFixture.getProject()), scope)));
  }

  @Test
  public void testT137() {
    doTestFirstParamType(multiFile.get(), "foo", "Test", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT138() {
    doTestFirstParamType(multiFile.get(), "foo", "Test",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Collection<java.lang.String>", null)));
  }

  @Test
  public void testT139() {
    doTestForeachParameter(
      ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  @Test
  public void testT140() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Set<U>", null)));
  }

  @Test
  public void testT141() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  @Test
  public void testT142() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testT143() {
    doTestAnonymousClassTypeParameters(multiFile.get(), "n",
                                       ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.AnInterface<java.lang.String, java.lang.Void>", null)));
  }

  @Test
  public void testT144() {
    doTestAnonymousClassTypeParameters(multiFile.get(), "n",
                                       ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.AnInterface2<java.lang.String, java.lang.Void>", null)));
  }

  @Test
  public void testRemoveStaticMethodQualifier() {
    doTestFirstParamType(multiFile.get(), "meth",
                         ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test", null)));
  }

  @Test
  public void testPropagateViaEquals() {
    doTestFirstParamType(multiFile.get(), "meth", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.lang.Long", null)));
  }

  @Test
  public void testAssignableGetter() {
    doTestFieldType(multiFile.get(), "foo", "Test", PsiTypes.intType());
  }

  @Test
  public void testAssignableSetter() {
    doTestFieldType(multiFile.get(), "foo", "Test", PsiTypes.longType());
  }

  @Test
  public void testMethodReturnTypeWithTypeParameter() {
    doTestReturnType("meth", "java.util.List<T>");
  }

  @Test
  public void testBooleanGetterMethodName() {
    //test getter migration `is` -> `get`:
    doTestFieldType(multiFile.get(), "fooMigrateName", PsiTypes.intType());
  }

  @Test
  public void testBooleanGetterMethodName2() {
    //test _no_ getter migration `is` -> `get`
    // (see MigrateGetterNameSetting.showChooserDialog() for how 'DontMigrateName' controls migration):
    doTestFieldType(multiFile.get(), "fooDontMigrateName", PsiTypes.intType());
  }

  @Test
  public void testGetterToBoolean() {
    doTestFieldType(multiFile.get(), "fooMigrateName", PsiTypes.booleanType());
  }
  
  @Test
  public void testInt2OptionalInt() {
    doTestFirstParamType(multiFile.get(), "test", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.OptionalInt", null)));
  }
  
  @Test
  public void testString2OptionalString() {
    doTestFirstParamType(multiFile.get(), "testString", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Optional<java.lang.String>", null)));
  }

  @Test
  public void testGetterToBoolean2() {
    doTestFieldType(multiFile.get(), "fooDontMigrateName", PsiTypes.booleanType());
  }

  @Test
  public void testMethodMigrationToVoidWithUnusedReturns() {
    doTestMethodType(multiFile.get(), "toVoidMethod", PsiTypes.voidType());
  }

  @Test
  public void testDoNotPropagateMigrationToVoid() {
    doTestMethodType(multiFile.get(), "x", PsiTypes.voidType());
  }

  @Test
  public void testDoNotPropagateVoidToMethods() {
    doTestMethodType(multiFile.get(), "x", PsiTypes.voidType());
  }

  @Test
  public void testTernaryMigrateToVoid() {
    doTestMethodType(multiFile.get(), "ternary", PsiTypes.voidType());
  }

  @Test
  public void testMigrationToSuper() {
    doTestFieldType(multiFile.get(), "b", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.A<java.lang.String>", null)));
  }

  @Test
  public void testMigrationToSuper2() {
    doTestFieldType(multiFile.get(), "b", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test.Base<java.lang.String>", null)));
  }

  @Test
  public void testMultiVarDeclaration1() {
    doTestFieldsType(multiFile.get(), "Test", ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)), "a", "b");
  }

  @Test
  public void testMultiVarDeclaration2() {
    doTestFieldsType(multiFile.get(), "Test", ReadAction.computeBlocking(() -> myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)), "a", "b");
  }

  @Test
  public void testVoidMigrationInVarDecl() {
    doTestMethodType(multiFile.get(), "migrationMethod", PsiTypes.voidType());
  }

  @Test
  public void testVoidMigrationInVarDeclFailed() {
    doTestMethodType(multiFile.get(), "migrationMethod", PsiTypes.voidType());
  }

  @Test
  public void testVoidMigrationInAssignment() {
    doTestMethodType(multiFile.get(), "migrationMethod", PsiTypes.voidType());
  }

  @Test
  public void testVoidMigrationInAssignmentFailed() {
    doTestMethodType(multiFile.get(), "migrationMethod", PsiTypes.voidType());
  }

  @Test
  public void testGenericEllipsis() {
    doTestFieldType(multiFile.get(), "migrationField", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test<Short>", null)));
  }

  @Test
  public void testGenericEllipsis2() {
    doTestFieldType(multiFile.get(), "migrationField", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test<Short>", null)));
  }

  @Test
  public void testTypeParameterMigrationInInvalidCode() {
    doTestFieldType(multiFile.get(), "migrationField", ReadAction.computeBlocking(() -> myFactory.createTypeFromText("Test<Short>", null)));
  }

  private void doTestReturnType(String methodName, String migrationType) {
    start(multiFile.get(), new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return ReadAction.computeBlocking(() -> myFactory.createTypeFromText(migrationType, context));
      }

      @Override
      public PsiElement victim(PsiClass aClass) {
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(aClass, PsiMethod.class)) {
          if (methodName.equals(method.getName())) {
            return method;
          }
        }
        throw new AssertionError();
      }
    });
  }

  private void doTestForeachParameter(final PsiType migrationType) {
    start(multiFile.get(), new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return migrationType;
      }

      @Override
      public PsiElement victim(final PsiClass aClass) {
        final PsiForeachStatement foreachStatement = PsiTreeUtil.findChildOfType(aClass, PsiForeachStatement.class);
        assert foreachStatement != null : aClass.getText();
        return foreachStatement.getIterationParameter();
      }
    });
  }

  @Test
  public void testTypeAnno() {
    doTestFieldType(multiFile.get(), "list", "Test",
                    ReadAction.computeBlocking(() -> myFactory.createTypeFromText("java.util.Collection<java.lang.@TA Integer>", null)));
  }
}
