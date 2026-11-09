// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
@SuppressWarnings("WeakerAccess")
public class AccessCanBeTightenedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  private VisibilityInspection myVisibilityInspection = createTool();

  private LocalInspectionTool getInspection() {
    return myVisibilityInspection.getSharedLocalInspectionTool();
  }

  @AfterEach
  void tearDown() {
    myVisibilityInspection = null;
  }

  private static VisibilityInspection createTool() {
    VisibilityInspection inspection = new VisibilityInspection();
    inspection.SUGGEST_PRIVATE_FOR_INNERS = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    return inspection;
  }

  @Test
  @SuppressWarnings("FieldMayBeStatic")
  public void testSimple() {
    inspectionFixture.get().doTest("import java.util.*;\n" +
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
  @Test
  public void testUseInAnnotation() {
    inspectionFixture.get().doTest("""
             import java.util.*;
             @interface Ann{ String value(); }
             @Ann(value = C.VAL
             )class C {
                 /*Access can be package-private*/public/**/ static final String VAL = "xx";
             }""");
  }

  @Test
  public void testUseOfPackagePrivateInAnnotation() {
    inspectionFixture.get().doTest("""
             import java.util.*;
             @interface Ann{ String value(); }
             @Ann(value = C.VAL
             )class C {
                 static final String VAL = "xx";
             }""");
  }

  @Test
  public void testSameFile() {
    inspectionFixture.get().doTest("""
             class C {
               private static class Err {
                 /*Access can be 'private'*/public/**/ boolean isVisible() { return true; }
               }
               boolean f = new Err().isVisible();
             }""");
  }

  @Test
  public void testSameFileInheritance() {
    inspectionFixture.get().doTest("class C {\n" +
           "  private static class Err {\n" +
           "    /*Access can be package-private*/public/**/ boolean notVisible() { return true; }\n" +
           "  }\n"+
           "  boolean f = new Err(){}.notVisible();\n" + //call on anonymous class!
           "}");
    inspectionFixture.get().doTest("class C {\n" +
           "  private static class Err {\n" +
           "    /*Access can be 'private'*/public/**/ static boolean notVisible() { return true; }\n" +
           "  }\n"+
           "  boolean f = new Err(){}.notVisible();\n" + //call on anonymous class!
           "}");
  }

  @Test
  public void testSiblingInheritance() {
    inspectionFixture.get().doTest("""
             abstract class Impl {
               public void doSomething() {
                 run();
               }
             
               public void run() {}
             }
             class Concrete extends Impl implements Runnable {
             }""");
  }

  @Test
  public void testAccessFromSubclass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testQualifiedAccessFromSubclass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testQualifiedAccessFromSubclassSamePackage() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testDoNotSuggestPrivateInAnonymousClassIfPrivatesForInnersIsOff() {
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myVisibilityInspection.SUGGEST_PRIVATE_FOR_INNERS = false;

    inspectionFixture.get().doTest("""
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

  @Test
  public void testDoNotSuggestPrivateIfInExtendsOrImplements() {
    inspectionFixture.get().doTest("""
             abstract class C implements Comparable<C.Inner> {
               static class Inner {
               }
             }""");
  }

  @Test
  public void testDoNotSuggestPrivateForAbstractIDEA151875() {
    inspectionFixture.get().doTest("""
             class C {
               abstract static class Inner {
                 abstract void foo();
               }
               void f(Inner i) {
                 i.foo();
               }
             }""");
  }

  @Test
  public void testDoNotSuggestPrivateForInnerStaticSubclass() {
    inspectionFixture.get().doTest("""
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

  @Test
  public void testStupidTwoPublicClassesInTheSamePackage() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testInterfaceIsImplementedByLambda() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testInnerClassIsUnusedButItsMethodsAre() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testInnerClassIsExposedInMethodSignature() {
    inspectionFixture.get().doTest("""
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

  @Test
  public void testClassIsExposedInMethodSignature() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testNestedEnumWithReferenceByName() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testEnumMemberCanBePrivate() {
    inspectionFixture.get().doTest("enum E {" +
           "  A;" +
           "  void x() {" +
           "    y();" +
           "  }" +
           "  /*Access can be 'private'*/public/**/ void y() {" +
           "  }" +
           "}");
  }

  @Test
  public void testRecord() {
    inspectionFixture.get().doTest("""
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

  @Test
  public void testRecordConstructor() {
    inspectionFixture.get().doTest("""
             class InnerRecordHolder {
                 private record InnerRecord() {
                     /*Access can be 'private'*/public/**/ InnerRecord {
                     }
                 }
             }
             """);
    myVisibilityInspection.SUGGEST_PRIVATE_FOR_INNERS = false;
    inspectionFixture.get().doTest("""
             class InnerRecordHolder {
                 private record InnerRecord() {
                     /*Access can be package-private*/public/**/ InnerRecord {
                     }
                 }
             }
             """);
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    inspectionFixture.get().doTest("""
             class InnerRecordHolder {
                 private record InnerRecord() {
                     public InnerRecord {
                     }
                 }
             }
             """);
  }

  @Test
  public void testSuggestPackagePrivateForTopLevelClassSetting() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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

  @Test
  public void testSuggestPackagePrivateForEntryPoint() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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
    }, myFixture.getTestRootDisposable());
    myFixture.enableInspections(myVisibilityInspection.getSharedLocalInspectionTool());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testMinimalVisibilityForNonEntryPOint() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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
    }, myFixture.getTestRootDisposable());
    myFixture.enableInspections(myVisibilityInspection.getSharedLocalInspectionTool());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testSuggestPackagePrivateForImplicitWrittenFields() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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
    }, myFixture.getTestRootDisposable());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  @Test
  @SuppressWarnings("FieldMayBeStatic")
  public void testSuggestForConstants() {
    myVisibilityInspection.SUGGEST_FOR_CONSTANTS = true;
    inspectionFixture.get().doTest("""
             class SuggestForConstants {
                 /*Access can be 'private'*/public/**/ static final String MY_CONSTANT = "a";
                 private final String myField = MY_CONSTANT;}""");
  }

  @Test
  @SuppressWarnings("FieldMayBeStatic")
  public void testDoNotSuggestForConstants() {
    myVisibilityInspection.SUGGEST_FOR_CONSTANTS = false;
    inspectionFixture.get().doTest("""
             class DoNotSuggestForConstants {
                 public static final String MY_CONSTANT = "a";
                 private final String myField = MY_CONSTANT;}""");
  }

  @Test
  public void testSuggestPackagePrivateForMembersOfEvenPackagePrivateClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
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
  
  @Test
  public void testImplicitClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("Implicit.java", """
      int field = 5;
      <warning descr="Access can be 'private'">protected</warning> int protectedField = 6;
      
      void member(int f) {
      }
      
      <warning descr="Access can be 'private'">protected</warning> void protectedMember(int f) {
      }
      
      void main() {
        member(field);
        protectedMember(protectedField);
      }
      """);
    myFixture.checkHighlighting();
  }

  private void addJavaFile(String relativePath, @Language("JAVA") String text) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addFileToProject(relativePath, text);
  }
}