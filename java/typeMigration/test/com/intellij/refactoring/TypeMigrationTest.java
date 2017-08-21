package com.intellij.refactoring;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author db
 * @since 22.07.2003
 */
public class TypeMigrationTest extends TypeMigrationTestBase {
  private PsiElementFactory myFactory;

  @NotNull
  @Override
  public String getTestRoot() {
    return "/refactoring/typeMigration/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    myFactory = myJavaFacade.getElementFactory();
  }

  @Override
  public void tearDown() throws Exception {
    myFactory = null;

    super.tearDown();
  }

  public void testT07() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.Integer", null).createArrayType());
  }

  public void testT08() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT09() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT10() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT11() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Map<java.lang.String, java.lang.Integer>", null));
  }

  public void testT12() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT13() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null));
  }

  public void testT14() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("A", null));
  }

  //do not touch javadoc refs etc
  public void testT15() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("A", null));
  }

  //do not touch signature with method type parameters
  public void testT16() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("B", null));
  }

  //change method signature inspired by call on parameters
  public void testT17() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("B", null));
  }

  //extending iterable -> used in foreach statement
  public void testT18() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("B", null));
  }

  public void testT19() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.HashMap<java.lang.Integer, java.lang.Integer>", null));
  }

  public void testT20() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Map<java.lang.String, java.lang.String>", null));
  }

  public void testT21() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Map<java.lang.String, java.util.Set<java.lang.String>>",
                                                 null)
    );
  }

  //varargs : removed after migration?!
  public void testT22() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.Integer", null));
  }

  //substitution from super class: type params substitution needed
  public void testT23() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("HashMap<java.lang.String, java.util.List<java.lang.String>>", null));
  }

  //check return type unchanged when it is possible
  public void testT24() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("D", null));
  }

  public void testT25() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("D", null));
  }

  //check param type change
  public void testT26() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("D", null));
  }

  public void testT27() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("D", null));
  }

  //list --> array
  public void testT28() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT29() {
    doTestMethodType("get",
                     myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT30() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }


  public void testT31() {
    doTestFieldType("f",
                    myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  //non code usages
  public void testT32() {
    doTestFirstParamType("bar",
                         myFactory.createTypeFromText("int", null));
  }

  //change type arguments for new expressions: l = new ArrayList<String>() -> l = new ArrayList<Integer>()
  public void testT33() {
    doTestFieldType("l",
                    myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null));
  }

  //new expression new ArrayList<String>() should be left without modifications
  public void testT34() {
    doTestFieldType("l",
                    myFactory.createTypeFromText("java.util.AbstractList<java.lang.String>", null));
  }

  public void testT35() {
    doTestFieldType("myParent",
                    myFactory.createTypeFromText("TestImpl", null));
  }

  //co-variant/contra-variant positions for primitive types 36-39
  public void testT36() {
    doTestFirstParamType("foo", PsiType.BYTE);
  }

  public void testT37() {
    doTestFirstParamType("foo", PsiType.INT);
  }

  public void testT38() {
    doTestFirstParamType("foo", PsiType.LONG);
  }

  public void testT39() {
    doTestFirstParamType("foo", PsiType.BYTE);
  }

  //Set s = new HashSet() -> HashSet s = new HashSet();
  public void testT40() {
    doTestFieldType("l",
                    myFactory.createTypeFromText("java.util.ArrayList", null));
  }

  //Set s = new HashSet<String>() -> HashSet s = new HashSet<String>();
  public void testT41() {
    doTestFieldType("l",
                    myFactory.createTypeFromText("java.util.ArrayList", null));
  }

  //Set s = new HashSet() -> HashSet<String> s = new HashSet();
  public void testT42() {
    doTestFieldType("l",
                    myFactory.createTypeFromText("java.util.ArrayList<java.lang.String>", null));
  }

  //long l; Object o = l -> long l; Long o = l;
  public void testT43() {
    doTestFieldType("o",
                    myFactory.createTypeFromText("java.lang.Long", null));
  }

  //long l; int  i; l = i; -> long l; byte i; l = i;
  public void testT44() {
    doTestFieldType("i", PsiType.BYTE);
  }

  //long l; int i; l = i; -> byte l; -> byte i; l = i;
  public void testT45() {
    doTestFieldType("l", PsiType.BYTE);
  }

  //byte i; long j = i; -> byte i; int j = i;
  public void testT46() {
    doTestFieldType("j", PsiType.INT);
  }

  //o = null -? int o = null
  public void testT47() {
    doTestFieldType("o", PsiType.INT);
  }

  //co-variant/contra-variant assignments: leave types if possible change generics signature only  48-49
  // foo(AbstractSet<String> s){Set<String> ss = s} -> foo(AbstractSet<Integer> s){Set<Integer> ss = s}
  public void testT48() {
    doTestFirstParamType("foo",
                         myFactory.createTypeFromText("java.util.AbstractSet<B>", null));
  }

  // Set<String> f; foo(AbstractSet<String> s){f = s} -> Set<Integer>f; foo(AbstractSet<Integer> s){f = s}
  public void testT49() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Set<B>", null));
  }

  //captured wildcard: Set<? extends JComponent> s; Set<? extends JComponent> c1 = s; ->
  //  Set<? extends JButton> s; Set<? extends JButton> c1 = s;
  public void testT50() {
    doTestFieldType("c1",
                    myFactory.createTypeFromText("java.util.Set<? extends JButton>", null));
  }

  //array initialization: 51-52
  public void testT51() {
    doTestFieldType("f",
                    myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType());
  }

  public void testT52() {
    doTestFieldType("f",
                    myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType());
  }

  //generic type promotion to array initializer
  public void testT53() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Set<java.lang.String>", null).createArrayType());
  }

  //wildcard type promotion to expressions 54-55
  public void testT54() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Set<? extends java.lang.Integer>", null));
  }

  public void testT55() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Set<?>", null));
  }

  //array index should be integer 56-57
  public void testT56() {
    doTestFirstParamType("foo", PsiType.DOUBLE);
  }

  public void testT57() {
    doTestFirstParamType("foo", PsiType.BYTE);
  }

  //Arrays can be assignable to Object/Serializable/Cloneable 58-59; ~ 60 varargs
  public void testT58() {
    doTestFieldType("f",
                    myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testT59() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.Cloneable", null));
  }

  public void testT60() {
    doTestFieldType("p",
                    myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  //change parameter type -> vararg; assignment changed to array
  public void testT61() {
    doTestFirstParamType("foo", new PsiEllipsisType(PsiType.INT));
  }

  //change field type -> change vararg parameter type due to assignment: 62-63
  public void testT62() {
    doTestFieldType("p", myFactory.createTypeFromText(
      CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testT63() {
    doTestFieldType("p", PsiType.DOUBLE.createArrayType());
  }

  //remove vararg type: 64-66
  public void testT64() {
    doTestFirstParamType("foo", PsiType.INT);
  }

  public void testT65() {
    doTestFirstParamType("foo",
                         myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT115() {
    doTestFirstParamType("foo",
                         new PsiEllipsisType(myFactory.createTypeFromText("java.lang.String", null)));
  }

  public void testT66() {
    doTestFirstParamType("foo", PsiType.INT);
  }

  public void testT67() {
    doTestFirstParamType("methMemAcc",
                         myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT68() {
    doTestFirstParamType("foo", PsiType.DOUBLE);
  }

  public void testT69() {
    doTestFirstParamType("foo", PsiType.BYTE);
  }

  public void testT70() {
    doTestFieldType("a", PsiType.FLOAT.createArrayType().createArrayType());
  }

  public void testT71() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.Class<? extends java.lang.Number>", null));
  }

  public void testT72() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.lang.Class<java.lang.Integer>", null));
  }

  public void testT73() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Set<java.awt.Component>", null).createArrayType().createArrayType());
  }

  //prefix/postfix expression; binary expressions 74-76
  public void testT74() {
    doTestFirstParamType("meth", PsiType.FLOAT);
  }

  public void testT75() {
    doTestFirstParamType("meth", myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT76() {
    doTestFirstParamType("meth", PsiType.FLOAT);
  }

  //+= , etc 77-78
  public void testT77() {
    doTestFirstParamType("meth", myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testT78() {
    doTestFirstParamType("meth", myFactory.createTypeFromText("java.lang.String", null));
  }

  //casts 79-80,83
  public void testT79() {
    doTestFirstParamType("meth", PsiType.BYTE);
  }

  public void testT80() {
    doTestFirstParamType("meth", PsiType.DOUBLE);
  }

  public void testT83() {
    doTestFirstParamType("meth", myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  //instanceofs 81-82
  public void testT81() {
    doTestFirstParamType("foo",
                         myFactory.createTypeFromText("A", null));
  }

  public void testT82() {
    doTestFirstParamType("foo",
                         myFactory.createTypeFromText("C", null));
  }

  public void testT84() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.util.Set<? extends java.util.Set>", null));
  }

  public void testT85() {
    doTestFieldType("str",
                    myFactory.createTypeFromText("java.lang.Integer", null));
  }

  //array <-> list 86-89;94;95
  public void testT86() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }

   public void testT87() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT88() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT89() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT94() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT95() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }


  public void testT90() {
    doTestFieldType("l",
                    myFactory.createTypeFromText("java.util.List<A>", null));
  }

  //element type -> element type array
  public void testT91() {
    doTestMethodType("foo",
                     myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  //List<S>=new ArrayList<S>{}; -> List<I>=new ArrayList<I>{}; anonymous
  public void testT92() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null));
  }

  //generics signature do not support primitives: Map<Boolean, String> - Map<boolean, String>
  public void testT93() {
    doTestFirstParamType("foo", PsiType.BOOLEAN);
  }

  //field initializers procession
  public void testT96() {
    doTestFieldType("f1",
                    myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT97() {
    doTestFieldType("f1", PsiType.INT);
  }

  //list <-> array conversion in assignment statements
  public void testT98() {
    doTestMethodType("getArray",
                     myFactory.createTypeFromText("java.util.List<java.lang.String>", null));
  }

  //escape pattern from []
  public void testT99() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Set<java.util.List<int[]>>", null));
  }

  //non formatted type
  public void testT100() {
    doTestFieldType("f",
                    myFactory.createTypeFromText("java.util.Map<java.lang.String,java.lang.Integer>", null));
  }

  //param List -> Array[]
  public void testT101() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.util.ArrayList<java.lang.Integer>[]", null));
  }

  //param Set.add() -> Array[] with conflict
  public void testT102() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.lang.Object[]", null));
  }

  //set(1, "") should be assignment-checked over String
  public void testT103() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.lang.Integer", null).createArrayType());
  }

   //raw list type now should not be changed
  public void testT104() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.lang.String", null).createArrayType());
  }

  //implicit type parameter change 105-107
  public void testT105() {
    doTestFieldType("t",
                    myFactory.createTypeFromText("java.lang.String", null));
  }


  public void testT106() {
    doTestFieldType("t",
                    myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT107() {
    doTestFieldType("t",
                    myFactory.createTypeFromText("java.lang.Integer", null));
  }

  //foreach && wildcards: 108-110
  public void testT108() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.util.List<? extends java.lang.Number>", null));
  }

  public void testT109() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.util.List<? super java.lang.Number>", null));
  }

  public void testT110() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.util.List<? extends java.lang.String>", null));
  }

  //wrap with array creation only literals and refs outside of binary/unary expressions
  public void testT111() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.lang.Integer", null).createArrayType());
  }

  public void testT112() {
    doTestMethodType("method",
                     myFactory.createTypeFromText("java.lang.Integer", null).createArrayType());
  }

  //varargs
  public void testT113() {
    doTestFirstParamType("method",
                         new PsiEllipsisType(myFactory.createTypeFromText("java.lang.Number", null)));
  }

  public void testT114() {
      doTestFirstParamType("method",
                           new PsiEllipsisType(myFactory.createTypeFromText("java.lang.String", null)));
    }

  //varargs && ArrayList
  public void testT118() {
    doTestFirstParamType("method",
                         new PsiEllipsisType(myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  //varargs && arrays
  public void testT119() {
    doTestFirstParamType("method",
                         new PsiEllipsisType(myFactory.createTypeFromText("java.lang.Integer", null)));
  }

  public void testT120() {
    doTestFirstParamType("method",
                         new PsiEllipsisType(myFactory.createTypeFromText("java.lang.String", null)));
  }

  //change parameter type in foreach statement: 116 - array, 117 - list
  public void testT116() {
    doTestFieldType("str",
                    myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT117() {
    doTestFieldType("str",
                    myFactory.createTypeFromText("java.lang.String", null));
  }


  public void testT121() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.util.ArrayList<java.lang.Float>", null));
  }

  public void testT122() {
    doTestFirstParamType("method",
                         myFactory.createTypeFromText("java.util.List<java.lang.Integer>", null).createArrayType());
  }

  public void testT123() {
    doTestFieldType("n",
                    myFactory.createTypeFromText("java.lang.Integer", null));
  }

  //124,125 - do not change formal method return type
  public void testT124() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.lang.Integer", null));
  }

  public void testT125() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.lang.Integer", null));
  }

  public void testT126() {
    doTestMethodType("meth",
                     myFactory.createTypeFromText("T", null));
  }

  // Checking preserving method parameters alignment
  public void testT127() {
    CommonCodeStyleSettings javaSettings = getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_PARAMETERS = true;
    javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTestMethodType("test234",
                     myFactory.createTypeFromText("long", null));
  }

  // test type migration from disjunction type
  public void testT128() {
    doTestCatchParameter(myFactory.createTypeFromText("Test.E1 | Test.E2", null),
                         myFactory.createTypeFromText("Test.E", null));
  }

  // test type migration to disjunction type
  public void testT129() {
    doTestCatchParameter(myFactory.createTypeFromText("Test.E", null),
                         myFactory.createTypeFromText("Test.E1 | Test.E2", null));
  }

  // test type migration from disjunction type with interfaces
  public void testT130() {
    doTestCatchParameter(myFactory.createTypeFromText("Test.E1 | Test.E2", null),
                         myFactory.createTypeFromText("Test.E", null));
  }

  // test type migration between disjunction types
  public void testT131() {
    doTestCatchParameter(myFactory.createTypeFromText("Test.E1 | Test.E2", null),
                         myFactory.createTypeFromText("Test.E2 | Test.E1", null));
  }

  private void doTestCatchParameter(final PsiType rootType, final PsiType migrationType) {
    start(new RulesProvider() {
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
  public void testT132() {
    doTestFirstParamType("h", "Test",
                         myFactory.createTypeFromText("I", null));
  }

  public void testT133() {
    doTestFirstParamType("h", "Test",
                         myFactory.createTypeFromText("I", null));
  }

  public void testT134() {
    doTestFirstParamType("buzz", "Test",
                         myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT135() {
    doTestFieldType("foo", "Test", PsiType.INT);
  }

  public void testT136() {
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    doTestFirstParamType("foo", "Test",
                         PsiType.getJavaLangString(myPsiManager, scope));
  }

  public void testT137() {
    doTestFirstParamType("foo", "Test", myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT138() {
    doTestFirstParamType("foo", "Test",
                         myFactory.createTypeFromText("java.util.Collection<java.lang.String>", null));
  }

  public void testT139() {
    doTestForeachParameter(
      myFactory.createTypeFromText("java.lang.Integer", null));
  }

  public void testT140() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.util.Set<U>", null));
  }

  public void testT141() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.lang.Integer", null));
  }

  public void testT142() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("java.lang.String", null));
  }

  public void testT143() {
    doTestAnonymousClassTypeParameters("n",
                                       myFactory.createTypeFromText("Test.AnInterface<java.lang.String, java.lang.Void>", null));
  }

  public void testT144() {
    doTestAnonymousClassTypeParameters("n",
                                       myFactory.createTypeFromText("Test.AnInterface2<java.lang.String, java.lang.Void>", null));
  }

  public void testRemoveStaticMethodQualifier() {
    doTestFirstParamType("meth",
                         myFactory.createTypeFromText("Test", null));
  }

  public void testPropagateViaEquals() {
    doTestFirstParamType("meth", myFactory.createTypeFromText("java.lang.Long", null));
  }

  public void testAssignableGetter() {
    doTestFieldType("foo", "Test", PsiType.INT);
  }

  public void testAssignableSetter() {
    doTestFieldType("foo", "Test", PsiType.LONG);
  }

  public void testMethodReturnTypeWithTypeParameter() {
    doTestReturnType("meth", "java.util.List<T>");
  }

  public void testBooleanGetterMethodName() {
    doTestFieldType("fooMigrateName", PsiType.INT);
  }

  public void testBooleanGetterMethodName2() {
    doTestFieldType("fooDontMigrateName", PsiType.INT);
  }

  public void testGetterToBoolean() {
    doTestFieldType("fooMigrateName", PsiType.BOOLEAN);
  }

  public void testGetterToBoolean2() {
    doTestFieldType("fooDontMigrateName", PsiType.BOOLEAN);
  }

  public void testMethodMigrationToVoidWithUnusedReturns() {
    doTestMethodType("toVoidMethod", PsiType.VOID);
  }

  public void testMigrationToSuper() {
    doTestFieldType("b", myJavaFacade.getElementFactory().createTypeFromText("Test.A<java.lang.String>", null));
  }

  public void testMigrationToSuper2() {
    doTestFieldType("b", myJavaFacade.getElementFactory().createTypeFromText("Test.Base<java.lang.String>", null));
  }

  public void testMultiVarDeclaration1() {
    doTestFieldsType("Test", myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null), "a", "b");
  }

  public void testMultiVarDeclaration2() {
    doTestFieldsType("Test", myFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null), "a", "b");
  }

  public void testVoidMigrationInVarDecl() {
    doTestMethodType("migrationMethod", PsiType.VOID);
  }

  public void testVoidMigrationInVarDeclFailed() {
    doTestMethodType("migrationMethod", PsiType.VOID);
  }

  public void testVoidMigrationInAssignment() {
    doTestMethodType("migrationMethod", PsiType.VOID);
  }

  public void testVoidMigrationInAssignmentFailed() {
    doTestMethodType("migrationMethod", PsiType.VOID);
  }

  public void testGenericEllipsis() {
    doTestFieldType("migrationField", myJavaFacade.getElementFactory().createTypeFromText("Test<Short>", null));
  }

  public void testGenericEllipsis2() {
    doTestFieldType("migrationField", myJavaFacade.getElementFactory().createTypeFromText("Test<Short>", null));
  }

  public void testTypeParameterMigrationInInvalidCode() {
    doTestFieldType("migrationField", myJavaFacade.getElementFactory().createTypeFromText("Test<Short>", null));
  }

  private void doTestReturnType(final String methodName, final String migrationType) {
    start(new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return myFactory.createTypeFromText(migrationType, context);
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
    start(new RulesProvider() {
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


  public void testTypeAnno() {
    doTestFieldType("list", "Test",
                    myFactory.createTypeFromText("java.util.Collection<java.lang.@TA Integer>", null));
  }
}
