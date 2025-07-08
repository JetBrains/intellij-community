// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.lang.FileASTNode;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST;

@SuppressWarnings("SpellCheckingInspection")
public class JavaStubBuilderTest extends LightIdeaTestCase {
  private StubBuilder myBuilder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myBuilder = new JavaLightStubBuilder();
  }

  @Override
  protected void tearDown() throws Exception {
    myBuilder = null;
    super.tearDown();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }

  public void testEmpty() {
    doTest("/**/",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
             """);
  }

  public void testFileHeader() {
    doTest("""
             package p;
             import a/*comment to skip*/.b;
             import static c.d.*;
             import static java.util.Arrays.sort;""",

           """
             PsiJavaFileStub [p]
               PACKAGE_STATEMENT:PsiPackageStatementStub[p]
               IMPORT_LIST:PsiImportListStub
                 IMPORT_STATEMENT:PsiImportStatementStub[a.b]
                 IMPORT_STATIC_STATEMENT:PsiImportStatementStub[static c.d.*]
                 IMPORT_STATIC_STATEMENT:PsiImportStatementStub[static java.util.Arrays.sort]
             """);
  }

  public void testClassDeclaration() {
    doTest("""
             package p;class A implements I, J<I> { }
             class B<K, V extends X> extends a/*skip*/.A { class I { } }
             @java.lang.Deprecated interface I { }
             /** @deprecated just don't use */ @interface N { }
             /** {@code @deprecated}? nope. */ class X { }""",

           """
             PsiJavaFileStub [p]
               PACKAGE_STATEMENT:PsiPackageStatementStub[p]
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=A fqn=p.A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:I, J<I>]
               CLASS:PsiClassStub[name=B fqn=p.B]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   TYPE_PARAMETER:PsiTypeParameter[K]
                     EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:]
                   TYPE_PARAMETER:PsiTypeParameter[V]
                     EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:X]
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:a/*skip*/.A]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 CLASS:PsiClassStub[name=I fqn=p.B.I]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                   IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
               CLASS:PsiClassStub[interface deprecatedA name=I fqn=p.I]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                   ANNOTATION:PsiAnnotationStub[@java.lang.Deprecated]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
               CLASS:PsiClassStub[interface annotation deprecated name=N fqn=p.N]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
               CLASS:PsiClassStub[name=X fqn=p.X]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testMethods() {
    doTest("""
             public @interface Anno {
               int i() default 42;
               public static String s();
             }
             private class C {
               public C() throws Exception { }
               public abstract void m(final int i, int[] a1, int a2[], int[] a3[]);
               private static int v2a(int... v) [] { return v; }
             }
             interface I {
               void m1();
               default void m2() { }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[interface annotation name=Anno fqn=Anno]
                 MODIFIER_LIST:PsiModifierListStub[mask=1]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 ANNOTATION_METHOD:PsiMethodStub[annotation i:int default=42]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                 ANNOTATION_METHOD:PsiMethodStub[annotation s:String]
                   MODIFIER_LIST:PsiModifierListStub[mask=9]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=2]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[cons C:null]
                   MODIFIER_LIST:PsiModifierListStub[mask=1]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:Exception]
                 METHOD:PsiMethodStub[m:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=1025]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                     PARAMETER:PsiParameterStub[i:int]
                       MODIFIER_LIST:PsiModifierListStub[mask=16]
                     PARAMETER:PsiParameterStub[a1:int[]]
                       MODIFIER_LIST:PsiModifierListStub[mask=0]
                     PARAMETER:PsiParameterStub[a2:int[]]
                       MODIFIER_LIST:PsiModifierListStub[mask=0]
                     PARAMETER:PsiParameterStub[a3:int[][]]
                       MODIFIER_LIST:PsiModifierListStub[mask=0]
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                 METHOD:PsiMethodStub[varargs v2a:int[]]
                   MODIFIER_LIST:PsiModifierListStub[mask=10]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                     PARAMETER:PsiParameterStub[v:int...]
                       MODIFIER_LIST:PsiModifierListStub[mask=0]
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
               CLASS:PsiClassStub[interface name=I fqn=I]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[m1:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                 METHOD:PsiMethodStub[m2:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=512]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
             """);
  }

  public void testFields() {
    doTest("""
             static class C {
               strictfp float f;
               int j[] = {0}, k;
               static String s = "-";
             }
             public class D {
               private volatile boolean b;
               public final double x;
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=8]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 FIELD:PsiFieldStub[f:float]
                   MODIFIER_LIST:PsiModifierListStub[mask=2048]
                 FIELD:PsiFieldStub[j:int[]={0}]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                 FIELD:PsiFieldStub[k:int]
                 FIELD:PsiFieldStub[s:String="-"]
                   MODIFIER_LIST:PsiModifierListStub[mask=8]
               CLASS:PsiClassStub[name=D fqn=D]
                 MODIFIER_LIST:PsiModifierListStub[mask=1]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 FIELD:PsiFieldStub[b:boolean]
                   MODIFIER_LIST:PsiModifierListStub[mask=66]
                 FIELD:PsiFieldStub[x:double]
                   MODIFIER_LIST:PsiModifierListStub[mask=17]
             """);
  }

  public void testAnonymousClasses() {
    doTest("""
             class C { {
               new O.P() { };
               X.new Y() { };
               f(p -> new R() { });
             } }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 CLASS_INITIALIZER:PsiClassInitializerStub
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=O.P]
                   ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Y inqualifnew]
                   LAMBDA_EXPRESSION:FunctionalExpressionStub
                     ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=R]
             """);
  }

  public void testEnums() {
    doTest("""
             enum E {
               E1() { };
               abstract void m();
             }
             public enum U { U1, U2 }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[enum name=E fqn=E]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 ENUM_CONSTANT:PsiFieldStub[enumconst E1:E]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   ENUM_CONSTANT_INITIALIZER:PsiClassStub[anonymous enumInit name=null fqn=null baseref=E]
                 METHOD:PsiMethodStub[m:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=1024]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
               CLASS:PsiClassStub[enum name=U fqn=U]
                 MODIFIER_LIST:PsiModifierListStub[mask=1]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 ENUM_CONSTANT:PsiFieldStub[enumconst U1:U]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                 ENUM_CONSTANT:PsiFieldStub[enumconst U2:U]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
             """);
  }

  public void testLocalVariables() {
    doTest("""
             class C {
               void m() {
                 int local = 0;
                 Object r = new Runnable() {
                   public void run() { }
                 };
                 for (int loop = 0; loop < 10; loop++) ;
                 try (Resource r = new Resource()) { }
                 try (Resource r = new Resource() {
                        @Override public void close() { }
                      }) { }
                 try (new Resource()) { }  }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[m:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                   ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Runnable]
                     METHOD:PsiMethodStub[run:void]
                       MODIFIER_LIST:PsiModifierListStub[mask=1]
                       TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                       PARAMETER_LIST:PsiParameterListStub
                       THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                   ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Resource]
                     METHOD:PsiMethodStub[close:void]
                       MODIFIER_LIST:PsiModifierListStub[mask=1]
                         ANNOTATION:PsiAnnotationStub[@Override]
                           ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                       TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                       PARAMETER_LIST:PsiParameterListStub
                       THROWS_LIST:PsiRefListStub[THROWS_LIST:]
             """);
  }

  public void testNonListParameters() {
    doTest("""
             class C {
               {
                 for (int i : arr) ;
                 for (String s : new Iterable<String>() {
                   @Override public Iterator<String> iterator() { return null; }
                 }) ;
                 try { }
                   catch (Throwable t) { }
                   catch (E1|E2 e) { }
               }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 CLASS_INITIALIZER:PsiClassInitializerStub
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   ANONYMOUS_CLASS:PsiClassStub[anonymous name=null fqn=null baseref=Iterable<String>]
                     METHOD:PsiMethodStub[iterator:Iterator<String>]
                       MODIFIER_LIST:PsiModifierListStub[mask=1]
                         ANNOTATION:PsiAnnotationStub[@Override]
                           ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                       TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                       PARAMETER_LIST:PsiParameterListStub
                       THROWS_LIST:PsiRefListStub[THROWS_LIST:]
             """);
  }

  public void testAnnotations() {
    doTest("""
             @Deprecated
             @SuppressWarnings("UnusedDeclaration")
             class Foo { }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[deprecatedA name=Foo fqn=Foo]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                   ANNOTATION:PsiAnnotationStub[@Deprecated]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@SuppressWarnings("UnusedDeclaration")]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                       NAME_VALUE_PAIR:PsiNameValuePairStubImpl
                         LITERAL_EXPRESSION:PsiLiteralStub
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testNestedGenerics() {
    doTest("""
             class X {
               A<?, ? extends B>.C<? extends D>.  E< ? /* */>.F field;
             }
             """, """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=X fqn=X]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 FIELD:PsiFieldStub[field:A<?,? extends B>.C<? extends D>.E<?>.F]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
             """);
  }

  public void testTypeAnnotations() {
    doTest("""
             import j.u.@A C;
             import @A j.u.D;

             class C<@A T extends @A C> implements @A I<@A T> {
               @TA T<@A T1, @A ? extends @A T2> f;
               @TA T m(@A C this, @TA int p) throws @A E {
                 o.<@A1 C>m();
                 new <T> @A2 C();
                 C.@A3 B v = (@A4 C)v.new @A5 C();
                 m(@A6 C::m);
                 @A7 T @A8[] @A9[] a = new @A7 T @A8[0] @A9[0];
               }
               int @A [] v() @A [] { }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
                 IMPORT_STATEMENT:PsiImportStatementStub[j.u.C]
                 IMPORT_STATEMENT:PsiImportStatementStub[j.u.D]
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   TYPE_PARAMETER:PsiTypeParameter[T]
                     ANNOTATION:PsiAnnotationStub[@A]
                       ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                     EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:@A C]
                       ANNOTATION:PsiAnnotationStub[@A]
                         ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:@A I<@A T>]
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                 FIELD:PsiFieldStub[f:T<T1,? extends T2>]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                     ANNOTATION:PsiAnnotationStub[@TA]
                       ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                 METHOD:PsiMethodStub[m:T]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                     ANNOTATION:PsiAnnotationStub[@TA]
                       ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                     PARAMETER:PsiParameterStub[p:int]
                       MODIFIER_LIST:PsiModifierListStub[mask=0]
                         ANNOTATION:PsiAnnotationStub[@TA]
                           ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:@A E]
                     ANNOTATION:PsiAnnotationStub[@A]
                       ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A1]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A2]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A3]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A4]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A5]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   METHOD_REF_EXPRESSION:FunctionalExpressionStub
                     ANNOTATION:PsiAnnotationStub[@A6]
                       ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A7]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A8]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A9]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A7]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A8]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   ANNOTATION:PsiAnnotationStub[@A9]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                 METHOD:PsiMethodStub[v:int[][]]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   PARAMETER_LIST:PsiParameterListStub
                   ANNOTATION:PsiAnnotationStub[@A]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
             """);
  }

  public void testLocalClass() {
    doTest("""
             class C {
               void m() {
                 class L { }
               }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=C fqn=C]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[m:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                   CLASS:PsiClassStub[name=L fqn=null]
                     MODIFIER_LIST:PsiModifierListStub[mask=0]
                     TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                     EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                     IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testModuleInfo() {
    doTest("""
             @Deprecated open module M. /*ignore me*/ N {
               requires transitive static A.B;
               exports k.l;
               opens m.n;
               uses p.C;
               provides p.C with x.Y;
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               MODULE:PsiJavaModuleStub[name=M.N, resolution=0]
                 MODIFIER_LIST:PsiModifierListStub[mask=8192]
                   ANNOTATION:PsiAnnotationStub[@Deprecated]
                     ANNOTATION_PARAMETER_LIST:PsiAnnotationParameterListStubImpl
                 REQUIRES_STATEMENT:PsiRequiresStatementStub:A.B
                   MODIFIER_LIST:PsiModifierListStub[mask=16392]
                 EXPORTS_STATEMENT:PsiPackageAccessibilityStatementStub[EXPORTS]:k.l
                 OPENS_STATEMENT:PsiPackageAccessibilityStatementStub[OPENS]:m.n
                 USES_STATEMENT:PsiUsesStatementStub:p.C
                 PROVIDES_STATEMENT:PsiProvidesStatementStub:p.C
                   PROVIDES_WITH_LIST:PsiRefListStub[PROVIDES_WITH_LIST:x.Y]
             """);
  }

  public void testRecord() {
    doTest("record A(int x, String y) {}",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[record name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 RECORD_HEADER:PsiRecordHeaderStub
                   RECORD_COMPONENT:PsiRecordComponentStub[x:int]
                     MODIFIER_LIST:PsiModifierListStub[mask=0]
                   RECORD_COMPONENT:PsiRecordComponentStub[y:String]
                     MODIFIER_LIST:PsiModifierListStub[mask=0]
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testIncompleteRecord() {
    doTest("record A",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[record name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testLocalRecord() {
    doTest("""
             class A {
               void test() {
                 record A(String s) { }
               }
             }
             """,

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[test:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                   CLASS:PsiClassStub[record name=A fqn=null]
                     MODIFIER_LIST:PsiModifierListStub[mask=0]
                     TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                     RECORD_HEADER:PsiRecordHeaderStub
                       RECORD_COMPONENT:PsiRecordComponentStub[s:String]
                         MODIFIER_LIST:PsiModifierListStub[mask=0]
                     EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                     IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testLocalRecordIncorrect() {
    doTest("""
             class A {
               void test() {
                 record A { }
               }
             }
             """,

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[test:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
             """);
  }

  public void testLocalRecordLikeIncompleteCode() {
    doTest("""
             class A {
               void foo(){
                 record turn getTitle();
               }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[foo:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
             """);
  }

  public void testLocalRecordLikeIncompleteCodeWithTypeParameters() {
    doTest("""
             class A {
               void foo(){
                 record turn<A>  }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[foo:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                   CLASS:PsiClassStub[record name=turn fqn=null]
                     MODIFIER_LIST:PsiModifierListStub[mask=0]
                     TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                       TYPE_PARAMETER:PsiTypeParameter[A]
                         EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:]
                     EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                     IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testLocalRecordWithTypeParameters() {
    doTest("""
             class A {
               void foo(){
                 record R<String>(){}
               }
             }""",

           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
               CLASS:PsiClassStub[name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=0]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                 METHOD:PsiMethodStub[foo:void]
                   MODIFIER_LIST:PsiModifierListStub[mask=0]
                   TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                   PARAMETER_LIST:PsiParameterListStub
                   THROWS_LIST:PsiRefListStub[THROWS_LIST:]
                   CLASS:PsiClassStub[record name=R fqn=null]
                     MODIFIER_LIST:PsiModifierListStub[mask=0]
                     TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                       TYPE_PARAMETER:PsiTypeParameter[String]
                         EXTENDS_BOUND_LIST:PsiRefListStub[EXTENDS_BOUNDS_LIST:]
                     RECORD_HEADER:PsiRecordHeaderStub
                     EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                     IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testInvalidGenericEmptyBody() {
    doTest("""
             import java.util.*;
             import java.util.function.*;
             
             private static class A implements BiConsumer<List<A>, List<A>n>> {}
             """,
           """
             PsiJavaFileStub []
               IMPORT_LIST:PsiImportListStub
                 IMPORT_STATEMENT:PsiImportStatementStub[java.util.*]
                 IMPORT_STATEMENT:PsiImportStatementStub[java.util.function.*]
               CLASS:PsiClassStub[name=A fqn=A]
                 MODIFIER_LIST:PsiModifierListStub[mask=10]
                 TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                 EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                 IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
             """);
  }

  public void testCommentInType() {
    doTest("""
             class A {
               void foo(List<String /*hello > */> list){
                 
               }
             }""",

           """
            PsiJavaFileStub []
              IMPORT_LIST:PsiImportListStub
              CLASS:PsiClassStub[name=A fqn=A]
                MODIFIER_LIST:PsiModifierListStub[mask=0]
                TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                METHOD:PsiMethodStub[foo:void]
                  MODIFIER_LIST:PsiModifierListStub[mask=0]
                  TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                  PARAMETER_LIST:PsiParameterListStub
                    PARAMETER:PsiParameterStub[list:List<String>]
                      MODIFIER_LIST:PsiModifierListStub[mask=0]
                  THROWS_LIST:PsiRefListStub[THROWS_LIST:]
            """);
  }

  public void testInterfaceKeywordInBody() {
    String source = """
      class X {
        void test() {}interface
      }""";
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", source);
    FileASTNode fileNode = file.getNode();
    assertNotNull(fileNode);
    assertFalse(fileNode.isParsed());
    StubElement<?> element = myBuilder.buildStubTree(file);
    List<StubElement<?>> stubs = element.getChildrenStubs();
    assertSize(2, stubs);
    PsiClassStub<?> classStub = (PsiClassStub<?>)stubs.get(1);
    assertFalse(classStub.isInterface());
  }

  public void testTypeAnnotation() {
    String source = """
      import org.jetbrains.annotations.NotNull;
      
      public final class Container<E> {
          public final Container<@NotNull Container<@NotNull E>> test() {
              return new Container<>();
          }
      }
      """;
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", source);
    FileASTNode fileNode = file.getNode();
    assertNotNull(fileNode);
    assertFalse(fileNode.isParsed());
    StubElement<?> element = myBuilder.buildStubTree(file);
    PsiClassStub<?> classStub = ContainerUtil.findInstance(element.getChildrenStubs(), PsiClassStub.class);
    assertNotNull(classStub);
    PsiMethodStub methodStub = ContainerUtil.findInstance(classStub.getChildrenStubs(), PsiMethodStub.class);
    assertNotNull(methodStub);
    TypeInfo typeInfo = methodStub.getReturnTypeText();
    assertEquals("Container<Container<E>>", typeInfo.text());
    TypeAnnotationContainer annotations = typeInfo.getTypeAnnotations();
    assertEquals("""
                   /1->@NotNull
                   /1/1->@NotNull""", annotations.toString());
  }

  public void testTypeAnnotationQualified() {
    String source = """
      import pkg.Anno1;
      import pkg.Anno2;
      
      public final class Container {
          public final native com.foo.@Anno1 Outer.@Anno2 Inner test();
      }
      """;
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", source);
    FileASTNode fileNode = file.getNode();
    assertNotNull(fileNode);
    assertFalse(fileNode.isParsed());
    StubElement<?> element = myBuilder.buildStubTree(file);
    PsiClassStub<?> classStub = ContainerUtil.findInstance(element.getChildrenStubs(), PsiClassStub.class);
    assertNotNull(classStub);
    PsiMethodStub methodStub = ContainerUtil.findInstance(classStub.getChildrenStubs(), PsiMethodStub.class);
    assertNotNull(methodStub);
    TypeInfo typeInfo = methodStub.getReturnTypeText();
    assertEquals("com.foo.Outer.Inner", typeInfo.text());
    TypeAnnotationContainer annotations = typeInfo.getTypeAnnotations();
    assertEquals("""
                   /.->@Anno1
                   ->@Anno2""", annotations.toString());
  }

  public void testSOEProof() {
    StringBuilder sb = new StringBuilder();
    SecureRandom random = new SecureRandom();
    sb.append("class SOE_test {\n BigInteger BIG = new BigInteger(\n");
    int i;
    for (i = 0; i < 100000; i++) {
      sb.append("  \"").append(random.nextInt(Integer.MAX_VALUE)).append("\" +\n");
    }
    sb.append("  \"\");\n}");

    PsiJavaFile file = (PsiJavaFile)createLightFile("SOE_test.java", sb.toString());
    long t = System.currentTimeMillis();
    StubElement<?> tree = myBuilder.buildStubTree(file);
    t = System.currentTimeMillis() - t;
    assertEquals("""
                   PsiJavaFileStub []
                     IMPORT_LIST:PsiImportListStub
                     CLASS:PsiClassStub[name=SOE_test fqn=SOE_test]
                       MODIFIER_LIST:PsiModifierListStub[mask=0]
                       TYPE_PARAMETER_LIST:PsiTypeParameterListStub
                       EXTENDS_LIST:PsiRefListStub[EXTENDS_LIST:]
                       IMPLEMENTS_LIST:PsiRefListStub[IMPLEMENTS_LIST:]
                       FIELD:PsiFieldStub[BIG:BigInteger=;INITIALIZER_NOT_STORED;]
                         MODIFIER_LIST:PsiModifierListStub[mask=0]
                   """,
                 DebugUtil.stubTreeToString(tree));
    LOG.debug("SOE depth=" + i + ", time=" + t + "ms");
  }

  private void doTest(/*@Language("JAVA")*/ String source, @Language("TEXT") String expected) {
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", source);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
    FileASTNode fileNode = file.getNode();
    assertNotNull(fileNode);
    assertFalse(fileNode.isParsed());

    StubElement<?> lightTree = myBuilder.buildStubTree(file);
    assertFalse(fileNode.isParsed());

    file.getNode().getChildren(null); // force switch to AST
    StubElement<?> astBasedTree = myBuilder.buildStubTree(file);
    assertTrue(fileNode.isParsed());

    assertEquals("light tree differs", expected, DebugUtil.stubTreeToString(lightTree));
    assertEquals("AST-based tree differs", expected, DebugUtil.stubTreeToString(astBasedTree));
  }
}