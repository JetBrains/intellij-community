/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi;

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;

@SuppressWarnings("SpellCheckingInspection")
public class JavaStubBuilderTest extends LightIdeaTestCase {
  private StubBuilder myBuilder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    myBuilder = new JavaLightStubBuilder();
  }

  @Override
  protected void tearDown() throws Exception {
    myBuilder = null;
    super.tearDown();
  }

  public void testEmpty() {
    doTest("/**/",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n");
  }

  public void testFileHeader() {
    doTest("package p;\n" +
           "import a/*comment to skip*/.b;\n" +
           "import static c.d.*;\n" +
           "import static java.util.Arrays.sort;",

           "PsiJavaFileStub [p]\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "    IMPORT_STATEMENT:PsiImportStatementStub[a.b]\n" +
           "    IMPORT_STATIC_STATEMENT:PsiImportStatementStub[static c.d.*]\n" +
           "    IMPORT_STATIC_STATEMENT:PsiImportStatementStub[static java.util.Arrays.sort]\n");
  }

  public void testClassDeclaration() {
    doTest("package p;" +
           "class A implements I, J<I> { }\n" +
           "class B<K, V extends X> extends a/*skip*/.A { class I { } }\n" +
           "@java.lang.Deprecated interface I { }\n" +
           "/** @deprecated just don't use */ @interface N { }\n" +
           "/** {@code @deprecated}? nope. */ class X { }",

           "PsiJavaFileStub [p]\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=A fqn=p.A]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:I, J<I>]\n" +
           "  CLASS:PsiClassStub[name=B fqn=p.B]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      TYPE_PARAMETER:PsiTypeParameter[K]\n" +
           "        EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:]\n" +
           "      TYPE_PARAMETER:PsiTypeParameter[V]\n" +
           "        EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:X]\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:a/*skip*/.A]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    CLASS:PsiClassStub[name=I fqn=p.B.I]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "      IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "  CLASS:PsiClassStub[interface deprecatedA name=I fqn=p.I]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      ANNOTATION:PsiAnnotationStub[@java.lang.Deprecated]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "  CLASS:PsiClassStub[interface annotation deprecated name=N fqn=p.N]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "  CLASS:PsiClassStub[name=X fqn=p.X]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n");
  }

  public void testMethods() {
    doTest("public @interface Anno {\n" +
           "  int i() default 42;\n" +
           "  public static String s();\n" +
           "}\n" +
           "private class C {\n" +
           "  public C() throws Exception { }\n" +
           "  public abstract void m(final int i, int[] a1, int a2[], int[] a3[]);\n" +
           "  private static int v2a(int... v) [] { return v; }\n" +
           "}\n" +
           "interface I {\n" +
           "  void m1();\n" +
           "  default void m2() { }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[interface annotation name=Anno fqn=Anno]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    ANNOTATION_METHOD:PsiMethodStub[annotation i:int default=42]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "    ANNOTATION_METHOD:PsiMethodStub[annotation s:String]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=9]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=2]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    METHOD:PsiMethodStub[cons C:null]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:Exception]\n" +
           "    METHOD:PsiMethodStub[m:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=1025]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "        PARAMETER:PsiParameterStub[i:int]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=16]\n" +
           "        PARAMETER:PsiParameterStub[a1:int[]]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "        PARAMETER:PsiParameterStub[a2:int[]]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "        PARAMETER:PsiParameterStub[a3:int[][]]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "    METHOD:PsiMethodStub[varargs v2a:int[]]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=10]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "        PARAMETER:PsiParameterStub[v:int...]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "  CLASS:PsiClassStub[interface name=I fqn=I]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    METHOD:PsiMethodStub[m1:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "    METHOD:PsiMethodStub[m2:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=512]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n");
  }

  public void testFields() {
    doTest("static class C {\n" +
           "  strictfp float f;\n" +
           "  int j[] = {0}, k;\n" +
           "  static String s = \"-\";\n" +
           "}\n" +
           "public class D {\n" +
           "  private volatile boolean b;\n" +
           "  public final double x;\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=8]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    FIELD:PsiFieldStub[f:float]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=2048]\n" +
           "    FIELD:PsiFieldStub[j:int[]={0}]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    FIELD:PsiFieldStub[k:int]\n" +
           "    FIELD:PsiFieldStub[s:String=\"-\"]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=8]\n" +
           "  CLASS:PsiClassStub[name=D fqn=D]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    FIELD:PsiFieldStub[b:boolean]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=66]\n" +
           "    FIELD:PsiFieldStub[x:double]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=17]\n");
  }

  public void testAnonymousClasses() {
    doTest("class C { {\n" +
           "  new O.P() { };\n" +
           "  X.new Y() { };\n" +
           "  f(p -> new R() { });\n" +
           "} }",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    CLASS_INITIALIZER:PsiClassInitializerStub\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=O.P]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Y inqualifnew]\n" +
           "      LAMBDA_EXPRESSION:FunctionalExpressionStub\n" +
           "        ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=R]\n");
  }

  public void testEnums() {
    doTest("enum E {\n" +
           "  E1() { };\n" +
           "  abstract void m();\n" +
           "}\n" +
           "public enum U { U1, U2 }",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[enum name=E fqn=E]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    ENUM_CONSTANT:PsiFieldStub[enumconst E1:E]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      ENUM_CONSTANT_INITIALIZER:PsiClassStub[anonymous enumInit name=null fqn=null baseref=E]\n" +
           "    METHOD:PsiMethodStub[m:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=1024]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "  CLASS:PsiClassStub[enum name=U fqn=U]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    ENUM_CONSTANT:PsiFieldStub[enumconst U1:U]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    ENUM_CONSTANT:PsiFieldStub[enumconst U2:U]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n");
  }

  public void testLocalVariables() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    int local = 0;\n" +
           "    Object r = new Runnable() {\n" +
           "      public void run() { }\n" +
           "    };\n" +
           "    for (int loop = 0; loop < 10; loop++) ;\n" +
           "    try (Resource r = new Resource()) { }\n" +
           "    try (Resource r = new Resource() {\n" +
           "           @Override public void close() { }\n" +
           "         }) { }\n" +
           "    try (new Resource()) { }" +
           "  }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    METHOD:PsiMethodStub[m:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Runnable]\n" +
           "        METHOD:PsiMethodStub[run:void]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "          TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "          PARAMETER_LIST:PsiParameterListStub\n" +
           "          THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Resource]\n" +
           "        METHOD:PsiMethodStub[close:void]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "            ANNOTATION:PsiAnnotationStub[@Override]\n" +
           "              ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "          TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "          PARAMETER_LIST:PsiParameterListStub\n" +
           "          THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n");
  }

  public void testNonListParameters() {
    doTest("class C {\n" +
           "  {\n" +
           "    for (int i : arr) ;\n" +
           "    for (String s : new Iterable<String>() {\n" +
           "      @Override public Iterator<String> iterator() { return null; }\n" +
           "    }) ;\n" +
           "    try { }\n" +
           "      catch (Throwable t) { }\n" +
           "      catch (E1|E2 e) { }\n" +
           "  }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    CLASS_INITIALIZER:PsiClassInitializerStub\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Iterable<String>]\n" +
           "        METHOD:PsiMethodStub[iterator:Iterator<String>]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=1]\n" +
           "            ANNOTATION:PsiAnnotationStub[@Override]\n" +
           "              ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "          TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "          PARAMETER_LIST:PsiParameterListStub\n" +
           "          THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n");
  }

  public void testAnnotations() {
    doTest("@Deprecated\n" +
           "@SuppressWarnings(\"UnusedDeclaration\")\n" +
           "class Foo { }",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[deprecatedA name=Foo fqn=Foo]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      ANNOTATION:PsiAnnotationStub[@Deprecated]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@SuppressWarnings(\"UnusedDeclaration\")]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "          NAME_VALUE_PAIR:PsiNameValuePairStubImpl\n" +
           "            LITERAL_EXPRESSION:PsiLiteralStub\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n");
  }

  public void testTypeAnnotations() {
    doTest("import j.u.@A C;\n" +
           "import @A j.u.D;\n" +
           "\n" +
           "class C<@A T extends @A C> implements @A I<@A T> {\n" +
           "  @TA T<@A T1, @A ? extends @A T2> f;\n" +
           "  @TA T m(@A C this, @TA int p) throws @A E {\n" +
           "    o.<@A1 C>m();\n" +
           "    new <T> @A2 C();\n" +
           "    C.@A3 B v = (@A4 C)v.new @A5 C();\n" +
           "    m(@A6 C::m);\n" +
           "    @A7 T @A8[] @A9[] a = new @A7 T @A8[0] @A9[0];\n" +
           "  }\n" +
           "  int @A [] v() @A [] { }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "    IMPORT_STATEMENT:PsiImportStatementStub[j.u.C]\n" +
           "    IMPORT_STATEMENT:PsiImportStatementStub[j.u.D]\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      TYPE_PARAMETER:PsiTypeParameter[T]\n" +
           "        ANNOTATION:PsiAnnotationStub[@A]\n" +
           "          ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "        EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:@A C]\n" +
           "          ANNOTATION:PsiAnnotationStub[@A]\n" +
           "            ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:@A I<@A T>]\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "    FIELD:PsiFieldStub[f:T<@A T1, @A ? extends @A T2>]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "        ANNOTATION:PsiAnnotationStub[@TA]\n" +
           "          ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "    METHOD:PsiMethodStub[m:T]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "        ANNOTATION:PsiAnnotationStub[@TA]\n" +
           "          ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "        PARAMETER:PsiParameterStub[p:int]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "            ANNOTATION:PsiAnnotationStub[@TA]\n" +
           "              ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:@A E]\n" +
           "        ANNOTATION:PsiAnnotationStub[@A]\n" +
           "          ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A1]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A2]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A3]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A4]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A5]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      METHOD_REF_EXPRESSION:FunctionalExpressionStub\n" +
           "        ANNOTATION:PsiAnnotationStub[@A6]\n" +
           "          ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A7]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A8]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A9]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A7]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A8]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      ANNOTATION:PsiAnnotationStub[@A9]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "    METHOD:PsiMethodStub[v:int[][]]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      ANNOTATION:PsiAnnotationStub[@A]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n");
  }

  public void testLocalClass() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    class L { }\n" +
           "  }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    METHOD:PsiMethodStub[m:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "      CLASS:PsiClassStub[name=L fqn=null]\n" +
           "        MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
           "        TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "        EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "        IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n");
  }

  public void testModuleInfo() {
    doTest("@Deprecated open module M. /*ignore me*/ N {\n" +
           "  requires transitive static A.B;\n" +
           "  exports k.l;\n" +
           "  opens m.n;\n" +
           "  uses p.C;\n" +
           "  provides p.C with x.Y;\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  MODULE:PsiJavaModuleStub:M.N\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=8192]\n" +
           "      ANNOTATION:PsiAnnotationStub[@Deprecated]\n" +
           "        ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl\n" +
           "    REQUIRES_STATEMENT:PsiRequiresStatementStub:A.B\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=16392]\n" +
           "    EXPORTS_STATEMENT:PsiPackageAccessibilityStatementStub[EXPORTS]:k.l\n" +
           "    OPENS_STATEMENT:PsiPackageAccessibilityStatementStub[OPENS]:m.n\n" +
           "    USES_STATEMENT:PsiUsesStatementStub:p.C\n" +
           "    PROVIDES_STATEMENT:PsiProvidesStatementStub:p.C\n" +
           "      PROVIDES_WITH_LIST:PsiRefListStub[PROVIDES_WITH_LIST:x.Y]\n");
  }

  public void testSOEProof() {
    StringBuilder sb = new StringBuilder();
    SecureRandom random = new SecureRandom();
    sb.append("class SOE_test {\n BigInteger BIG = new BigInteger(\n");
    int i;
    for (i = 0; i < 100000; i++) {
      sb.append("  \"").append(Math.abs(random.nextInt())).append("\" +\n");
    }
    sb.append("  \"\");\n}");

    PsiJavaFile file = (PsiJavaFile)createLightFile("SOE_test.java", sb.toString());
    long t = System.currentTimeMillis();
    StubElement tree = myBuilder.buildStubTree(file);
    t = System.currentTimeMillis() - t;
    assertEquals("PsiJavaFileStub []\n" +
                 "  IMPORT_LIST:PsiImportListStub\n" +
                 "  CLASS:PsiClassStub[name=SOE_test fqn=SOE_test]\n" +
                 "    MODIFIER_LIST:PsiModifierListStub[mask=0]\n" +
                 "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
                 "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
                 "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
                 "    FIELD:PsiFieldStub[BIG:BigInteger=;INITIALIZER_NOT_STORED;]\n" +
                 "      MODIFIER_LIST:PsiModifierListStub[mask=0]\n",
                 DebugUtil.stubTreeToString(tree));
    System.out.println("SOE depth=" + i + ", time=" + t + "ms");
  }

  public void testPerformance() throws IOException {
    String path = PathManagerEx.getTestDataPath() + "/psi/stub/StubPerformanceTest.java";
    String text = FileUtil.loadFile(new File(path));
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", text);
    String message = "Source file size: " + text.length();
    PlatformTestUtil.startPerformanceTest(message, 500, () -> myBuilder.buildStubTree(file)).assertTiming();
  }

  private void doTest(String source, String expected) {
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", source);
    FileASTNode fileNode = file.getNode();
    assertNotNull(fileNode);
    assertFalse(fileNode.isParsed());

    StubElement lightTree = myBuilder.buildStubTree(file);
    assertFalse(fileNode.isParsed());

    file.getNode().getChildren(null); // force switch to AST
    StubElement astBasedTree = myBuilder.buildStubTree(file);
    assertTrue(fileNode.isParsed());

    assertEquals("light tree differs", expected, DebugUtil.stubTreeToString(lightTree));
    assertEquals("AST-based tree differs", expected, DebugUtil.stubTreeToString(astBasedTree));
  }
}