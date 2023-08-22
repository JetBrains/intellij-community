// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.visibility;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("WeakerAccess")
public class AccessCanBeTightenedInspectionTest extends LightJavaInspectionTestCase {
  private VisibilityInspection myVisibilityInspection;

  @Override
  protected LocalInspectionTool getInspection() {
    return myVisibilityInspection.getSharedLocalInspectionTool();
  }

  @Override
  protected void setUp() throws Exception {
    myVisibilityInspection = createTool();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myVisibilityInspection = null;
    super.tearDown();
  }

  private static VisibilityInspection createTool() {
    VisibilityInspection inspection = new VisibilityInspection();
    inspection.SUGGEST_PRIVATE_FOR_INNERS = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    return inspection;
  }

  @SuppressWarnings("FieldMayBeStatic")
  public void testSimple() {
    doTest("import java.util.*;\n" +
           "class C {\n" +
           "    final int /*Access can be 'private'*/fd/**/ = 0;\n" +
           "    /*Access can be 'private'*/public/**/ int fd2;\n" +
           "    /*Access can be package-private*/public/**/ int forSubClass;\n" +
           "    @Override\n" +
           "    public int hashCode() {\n" +
           "      return fd + fd2;\n" + // use field
           "    }\n" +
           " public void fff() {\n" +
           "   class Local {\n" +
           "        int /*Access can be 'private'*/fd/**/;\n" +
           "        void f(){}\n" + // unused, ignore
           "        void /*Access can be 'private'*/fd/**/(){}\n" +
           "        @Override\n" +
           "        public int hashCode() {\n" +
           "          fd();\n" +
           "          return fd;\n" +
           "        }\n" +
           "        class CantbePrivate {}\n" +
           "   }\n" +
           " }\n" +
           "}\n" +
           "class Over extends C {" +
           "  int r = forSubClass;" +
           "  @Override " +
           "  public void fff() {}" +
           "}");
  }
  public void testUseInAnnotation() {
    doTest("""
             import java.util.*;
             @interface Ann{ String value(); }
             @Ann(value = C.VAL
             )class C {
                 /*Access can be package-private*/public/**/ static final String VAL = "xx";
             }""");
  }

  public void testUseOfPackagePrivateInAnnotation() {
    doTest("""
             import java.util.*;
             @interface Ann{ String value(); }
             @Ann(value = C.VAL
             )class C {
                 static final String VAL = "xx";
             }""");
  }

  public void testSameFile() {
    doTest("""
             class C {
               private static class Err {
                 /*Access can be 'private'*/public/**/ boolean isVisible() { return true; }
               }
               boolean f = new Err().isVisible();
             }""");
  }

  public void testSameFileInheritance() {
    doTest("class C {\n" +
           "  private static class Err {\n" +
           "    /*Access can be package-private*/public/**/ boolean notVisible() { return true; }\n" +
           "  }\n"+
           "  boolean f = new Err(){}.notVisible();\n" + //call on anonymous class!
           "}");
    doTest("class C {\n" +
           "  private static class Err {\n" +
           "    /*Access can be 'private'*/public/**/ static boolean notVisible() { return true; }\n" +
           "  }\n"+
           "  boolean f = new Err(){}.notVisible();\n" + //call on anonymous class!
           "}");
  }

  public void testSiblingInheritance() {
    doTest("""
             abstract class Impl {
               public void doSomething() {
                 run();
               }

               public void run() {}
             }
             class Concrete extends Impl implements Runnable {
             }""");
  }

  public void testAccessFromSubclass() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java",
                """
                  package x; import y.C; class Sub extends C {
                    boolean f = new Err().isTVisible();
                  }""");
    addJavaFile("y/C.java",
                """
                  package y; public class C {
                    public static class Err {
                      public boolean isTVisible() { return true; }
                    }
                  }""");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testQualifiedAccessFromSubclass() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java", """
      package x; import y.C; class Sub extends C {
        void bazz(C c) {
          int a = c.foo; c.bar();  }
      }
      """);
    addJavaFile("y/C.java", """
      package y; public class C {
        public int foo = 0;
        public void bar() {}
      }""");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testQualifiedAccessFromSubclassSamePackage() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java", "package x; " +
                            "import y.C; " +
                            "class Sub extends C {}");
    addJavaFile("y/C.java", """
      package y; public class C {
        public int foo = 0;
        public void bar() {}
      }""");
    addJavaFile("y/U.java", """
      package y; import x.Sub;
      public class U {{
        Sub s = new Sub();
        s.bar(); int a = s.foo;
       }}""");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testDoNotSuggestPrivateInAnonymousClassIfPrivatesForInnersIsOff() {
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myVisibilityInspection.SUGGEST_PRIVATE_FOR_INNERS = false;

    doTest("""
             class C {
              {
               new Runnable() {
                 @Override
                 public void run() { isDisposed = true; }
                 boolean isVisible() { return true; }
                 boolean isDisposed;
               }.run();
              }
             }""");
  }

  public void testDoNotSuggestPrivateIfInExtendsOrImplements() {
    doTest("""
             abstract class C implements Comparable<C.Inner> {
               static class Inner {
               }
             }""");
  }

  public void testDoNotSuggestPrivateForAbstractIDEA151875() {
    doTest("""
             class C {
               abstract static class Inner {
                 abstract void foo();
               }
               void f(Inner i) {
                 i.foo();
               }
             }""");
  }

  public void testDoNotSuggestPrivateForInnerStaticSubclass() {
    doTest("""
             class A {
                 /*Access can be package-private*/protected/**/ String myElement;
                 static class B extends A {
                     @Override
                     public String toString() {
                         return myElement;
                     }
                 }
                 /*Access can be 'private'*/protected/**/ String myElement1;
                 class B1 {
                     @Override
                     public String toString() {
                         return myElement1;
                     }
                 }
             }""");
  }

  public void testStupidTwoPublicClassesInTheSamePackage() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java", """
      package x; public class Sub {
        Object o = new C();
      }
      """);
    addJavaFile("x/C.java", """
      package x;\s
      <warning descr="Access can be package-private">public</warning> class C {
      }""");
    myFixture.configureByFiles("x/C.java", "x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testInterfaceIsImplementedByLambda() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/MyInterface.java", """
      package x;
      public interface MyInterface {
        void doStuff();
      }
      """);
    addJavaFile("x/MyConsumer.java", """
      package x;
      public class MyConsumer {
          public void doIt(MyInterface i) {
              i.doStuff();
          }
      }""");
    addJavaFile("y/Test.java", """
      package y;

      import x.MyConsumer;

      public class Test {
          void ddd(MyConsumer consumer) {
              consumer.doIt(() -> {});
          }
      }""");
    myFixture.configureByFiles("x/MyInterface.java", "y/Test.java", "x/MyConsumer.java");
    myFixture.checkHighlighting();
  }

  public void testInnerClassIsUnusedButItsMethodsAre() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Outer.java", """
      package x;
      class Outer {
          static Inner makeInner() {
              return new Inner();
          }

          static class Inner {
              void frob() {}
          }
      }
      """);
    addJavaFile("x/Consumer.java", """
      package x;
      public class Consumer {
          public void doIt() {
              Outer.makeInner().frob();
          }
      }""");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java");
    myFixture.checkHighlighting();
  }

  public void testInnerClassIsExposedInMethodSignature() {
    doTest("""
             package x;
             class Record {
                 java.util.List<Inner> getRecord() {
                     return null;
                 }
                 static class Inner {}

                 void x(Inner2 inner) {}
                 class Inner2 {}
                \s
                 Inner3 field;
                 class Inner3 {}
                \s
                 private Inner4 field2;
                 class /*Access can be 'private'*/Inner4/**/ {}
                \s
                 private void y(Inner5 i) {}
                 class /*Access can be 'private'*/Inner5/**/ {}}""");
  }

  public void testClassIsExposedInMethodSignature() {
    addJavaFile("mypackage/sub1/Sub123.java",
                """
                  package mypackage.sub1;

                  public class Sub123 {}""");
    addJavaFile("mypackage/sub1/Intermediate.java",
                """
                  package mypackage.sub1;

                  public class Intermediate {
                    public Sub123 getSub() {return null;}
                  }""");
    myFixture.configureByFiles("mypackage/sub1/Sub123.java", "mypackage/sub1/Intermediate.java");
    myFixture.checkHighlighting();
  }

  public void testNestedEnumWithReferenceByName() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Outer.java", """
      package x;
      public class Outer {
          enum E {A}
          static final E abc = E.A;

          public static void main(String[] args) {
              System.out.println(abc.ordinal());
          }
      }
      """);
    addJavaFile("x/Consumer.java", """
      package x;
      public class Consumer {
          public String foo() {
             return Outer.abc.name();
          }
      }""");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java");
    myFixture.checkHighlighting();
  }

  public void testEnumMemberCanBePrivate() {
    doTest("enum E {" +
           "  A;" +
           "  void x() {" +
           "    y();" +
           "  }" +
           "  /*Access can be 'private'*/public/**/ void y() {" +
           "  }" +
           "}");
  }

  public void testRecord() {
    doTest("""
             class Demo {
                 public static void main(String... args) {
                     User user = new User(null, null);
                     System.out.println(user.email());
                     user.xylophone();
                 }

                 public record User(String email, String phone){
                     public User {
                         if (email == null && phone == null) {
                             throw new IllegalArgumentException();
                         }
                     }

                     public String email() {
                         return email;
                     }

                     /*Access can be 'private'*/public/**/ void xylophone() {

                     }
                 }
             }""");
  }

  public void testSuggestPackagePrivateForTopLevelClassSetting() {
    myFixture.allowTreeAccessForAllFiles();
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    addJavaFile("x/Outer.java", """
      package x;
      public class Outer {

      }
      """);
    addJavaFile("x/Consumer.java", """
      package x;
      public class Consumer {
          public void doIt() {
              System.out.println(Outer.class.hashCode());
          }
      }""");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java");
    myFixture.checkHighlighting();
  }

  public void testSuggestPackagePrivateForEntryPoint() {
    addJavaFile("x/MyTest.java", """
      package x;
      public class MyTest {
          <warning descr="Access can be 'protected'">public</warning> void foo() {}
      }""");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), EntryPointsManagerBase.DEAD_CODE_EP_NAME, new EntryPointWithVisibilityLevel() {
      @Override
      public void readExternal(Element element) throws InvalidDataException {}

      @Override
      public void writeExternal(Element element) throws WriteExternalException {}

      @NotNull
      @Override
      public String getDisplayName() {
        return "accepted visibility";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return isEntryPoint(psiElement);
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiMethod && "foo".equals(((PsiMethod)psiElement).getName()) || psiElement instanceof PsiClass;
      }

      @Override
      public int getMinVisibilityLevel(PsiMember member) {
        return member instanceof PsiMethod && isEntryPoint(member) ? PsiUtil.ACCESS_LEVEL_PROTECTED : ACCESS_LEVEL_INVALID;
      }

      @Override
      public boolean isSelected() {
        return true;
      }

      @Override
      public void setSelected(boolean selected) {}

      @Override
      public String getTitle() {
        return getDisplayName();
      }

      @Override
      public String getId() {
        return getDisplayName();
      }
    }, getTestRootDisposable());
    myFixture.enableInspections(myVisibilityInspection.getSharedLocalInspectionTool());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  public void testMinimalVisibilityForNonEntryPOint() {
    addJavaFile("x/MyTest.java", """
      package x;
      public class MyTest {
          <warning descr="Access can be 'protected'">public</warning> void foo() {}
          {foo();}
      }""");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), EntryPointsManagerBase.DEAD_CODE_EP_NAME, new EntryPointWithVisibilityLevel() {
      @Override
      public void readExternal(Element element) throws InvalidDataException {}

      @Override
      public void writeExternal(Element element) throws WriteExternalException {}

      @NotNull
      @Override
      public String getDisplayName() {
        return "accepted visibility";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return false;
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
        return false;
      }

      @Override
      public int getMinVisibilityLevel(PsiMember member) {
        return member instanceof PsiMethod && "foo".equals(((PsiMethod)member).getName()) ? PsiUtil.ACCESS_LEVEL_PROTECTED : ACCESS_LEVEL_INVALID;
      }

      @Override
      public boolean isSelected() {
        return true;
      }

      @Override
      public void setSelected(boolean selected) {}

      @Override
      public String getTitle() {
        return getDisplayName();
      }

      @Override
      public String getId() {
        return getDisplayName();
      }
    }, getTestRootDisposable());
    myFixture.enableInspections(myVisibilityInspection.getSharedLocalInspectionTool());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  public void testSuggestPackagePrivateForImplicitWrittenFields() {
    addJavaFile("x/MyTest.java", """
      package x;
      public class MyTest {
          String foo;
        {System.out.println(foo);}}""");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return element instanceof PsiField && "foo".equals(((PsiField)element).getName());
      }
    }, getTestRootDisposable());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  @SuppressWarnings("FieldMayBeStatic")
  public void testSuggestForConstants() {
    myVisibilityInspection.SUGGEST_FOR_CONSTANTS = true;
    doTest("""
             class SuggestForConstants {
                 /*Access can be 'private'*/public/**/ static final String MY_CONSTANT = "a";
                 private final String myField = MY_CONSTANT;}""");
  }

  @SuppressWarnings("FieldMayBeStatic")
  public void testDoNotSuggestForConstants() {
    myVisibilityInspection.SUGGEST_FOR_CONSTANTS = false;
    doTest("""
             class DoNotSuggestForConstants {
                 public static final String MY_CONSTANT = "a";
                 private final String myField = MY_CONSTANT;}""");
  }

  public void testSuggestPackagePrivateForMembersOfEvenPackagePrivateClass() {
    myFixture.allowTreeAccessForAllFiles();
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    addJavaFile("x/Outer.java", """
      package x;
      public class Outer {
        <warning descr="Access can be package-private">public</warning> void foo() {}
        public void fromOtherPackage() {}
      }
      """);
    addJavaFile("x/Consumer.java",
                """
                  package x;
                  public class Consumer {
                      public void doIt(Outer o) {
                          o.foo();
                          o.fromOtherPackage();
                      }
                  }""");
    addJavaFile("y/ConsumerOtherPackage.java",
                """
                  package y;
                  public class ConsumerOtherPackage {
                      public void doIt(x.Outer o) {
                          o.fromOtherPackage();
                      }
                  }""");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java", "y/ConsumerOtherPackage.java");
    myFixture.checkHighlighting();
  }

  private void addJavaFile(String relativePath, @Language("JAVA") String text) {
    myFixture.addFileToProject(relativePath, text);
  }
}