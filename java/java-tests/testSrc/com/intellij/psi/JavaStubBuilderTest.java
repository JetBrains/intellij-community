/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.JavaFileStubBuilder;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.testFramework.LightIdeaTestCase;


public class JavaStubBuilderTest extends LightIdeaTestCase {
  private static final StubBuilder OLD_BUILDER = new JavaFileStubBuilder();
  private static final StubBuilder NEW_BUILDER = new JavaLightStubBuilder();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    doTest("@interface A { int i() default 42; }\n class C { void m(int p) throws E { } }", null);  // warm up
  }

  public void testFileHeader() {
    doTest("package p;\n" +
           "import a/*comment to skip*/.b;\n" +
           "import static c.d.*;\n" +
           "import static java.util.Arrays.sort",

           "PsiJavaFileStub [p]\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "    IMPORT_STATEMENT:PsiImportStatementStub[a.b]\n" +
           "    IMPORT_STATIC_STATEMENT:PsiImportStatementStub[static c.d.*]\n" +
           "    IMPORT_STATIC_STATEMENT:PsiImportStatementStub[static java.util.Arrays.sort]\n");
  }

  public void testClassDeclaration() throws Exception {
    doTest("package p;" +
           "class A implements I, J { }\n" +
           "class B<K,V extends X> extends a/*skip*/.A { class I { } }\n" +
           "@java.lang.Deprecated interface I { }\n" +
           "/** @deprecated just don't use */ @interface N { }",

           "PsiJavaFileStub [p]\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=A fqn=p.A]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:I, J]\n" +
           "  CLASS:PsiClassStub[name=B fqn=p.B]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      TYPE_PARAMETER:PsiTypeParameter[K]\n" +
           "        EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:]\n" +
           "      TYPE_PARAMETER:PsiTypeParameter[V]\n" +
           "        EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:X]\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:a/*skip*/.A]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    CLASS:PsiClassStub[name=I fqn=p.B.I]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "      IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "  CLASS:PsiClassStub[interface deprecatedA name=I fqn=p.I]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=5120]\n" +
           "      ANNOTATION:PsiAnnotationStub[@java.lang.Deprecated]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "  CLASS:PsiClassStub[interface annotation deprecated name=N fqn=p.N]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=5120]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n");
  }

  public void testMethods() throws Exception {
    doTest("public @interface Anno {\n" +
           "  int i() default 42;\n" +
           "  public static String s();\n" +
           "}\n" +
           "private class C {\n" +
           "  public C() throws Exception { }\n" +
           "  public abstract void m(final int i, int[] a1, int a2[], int[] a3[])\n" +
           "  private static int v2a(int... v) [] { return v; }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[interface annotation name=Anno fqn=Anno]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=1025]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    ANNOTATION_METHOD:PsiMethodStub[annotation i:int default=42]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=1025]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "    ANNOTATION_METHOD:PsiMethodStub[annotation s:String]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=1033]\n" +
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
           "          MODIFIER_LIST:PsiModifierListStub[mask=4112]\n" +
           "        PARAMETER:PsiParameterStub[a1:int[]]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "        PARAMETER:PsiParameterStub[a2:int[]]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "        PARAMETER:PsiParameterStub[a3:int[][]]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "    METHOD:PsiMethodStub[varargs v2a:int[]]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=10]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "        PARAMETER:PsiParameterStub[v:int...]\n" +
           "          MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n");
  }

  public void testFields() throws Exception {
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
           "    MODIFIER_LIST:PsiModifierListStub[mask=4104]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    FIELD:PsiFieldStub[f:float]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=6144]\n" +
           "    FIELD:PsiFieldStub[j:int[]={0}]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "    FIELD:PsiFieldStub[k:int]\n" +
           "    FIELD:PsiFieldStub[s:String=\"-\"]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=4104]\n" +
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

  public void testAnonymousClasses() throws Exception {
    doTest("class C { {\n" +
           "  new O.P() { };\n" +
           "  X.new Y() { }\n" +
           "} }",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    CLASS_INITIALIZER:PsiClassInitializerStub\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=O.P]\n" +
           "      ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Y inqualifnew]\n");
  }

  public void testEnums() throws Exception {
    doTest("enum E {\n" +
           "  E1() { }" +
           "  abstract void m();\n" +
           "}\n" +
           "public enum U { U1, U2 }",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[enum name=E fqn=E]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=5120]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    ENUM_CONSTANT:PsiFieldStub[enumconst E1:E]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=25]\n" +
           "      ENUM_CONSTANT_INITIALIZER:PsiClassStub[anonymous enumInit name=null fqn=null baseref=E]\n" +
           "    METHOD:PsiMethodStub[m:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=5120]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n" +
           "  CLASS:PsiClassStub[enum name=U fqn=U]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=17]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    ENUM_CONSTANT:PsiFieldStub[enumconst U1:U]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=25]\n" +
           "    ENUM_CONSTANT:PsiFieldStub[enumconst U2:U]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=25]\n");
  }

  public void testLocalVariables() throws Exception {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    int local = 0;\n" +
           "  }\n" +
           "}",

           "PsiJavaFileStub []\n" +
           "  IMPORT_LIST:PsiImportListStub\n" +
           "  CLASS:PsiClassStub[name=C fqn=C]\n" +
           "    MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "    TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "    EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]\n" +
           "    IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]\n" +
           "    METHOD:PsiMethodStub[m:void]\n" +
           "      MODIFIER_LIST:PsiModifierListStub[mask=4096]\n" +
           "      TYPE_PARAMETER_LIST:PsiTypeParameterListStub\n" +
           "      PARAMETER_LIST:PsiParameterListStub\n" +
           "      THROWS_LIST:PsiRefListStub[THROWS_LIST:]\n");
  }

  private static void doTest(final String source, final String tree) {
    final PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", source);
    final LazyParseableElement fileNode = (LazyParseableElement)file.getNode();
    assertNotNull(fileNode);
    assertFalse(fileNode.isParsed());

    long t1 = System.nanoTime();
    final StubElement lighterTree = NEW_BUILDER.buildStubTree(file);
    t1 = System.nanoTime() - t1;
    assertFalse(fileNode.isParsed());

    long t2 = System.nanoTime();
    final StubElement originalTree = OLD_BUILDER.buildStubTree(file);
    t2 = System.nanoTime() - t2;
    assertTrue(fileNode.isParsed());

    final String lightStr = DebugUtil.stubTreeToString(originalTree);
    final String originalStr = DebugUtil.stubTreeToString(lighterTree);
    if (tree != null) {
      final String msg = "light=" + t1/1000 + "mks, heavy=" + t2/1000 + "mks";
      // todo: assertTrue("Expected to be at least 2x faster: " + msg, 2*t1 <= t2);
      System.out.println(msg);

      assertEquals("wrong test data", tree, lightStr);
      if (!"".equals(tree)) {
        assertEquals("light tree differs", tree, originalStr);
      }
    }
  }
}