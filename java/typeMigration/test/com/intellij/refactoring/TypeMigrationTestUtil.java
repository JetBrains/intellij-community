// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The type migration tests on a {@link LightMultiFileFixture}.
 * <p>
 * Each helper migrates the elements of a class in the test directory and compares the result with the directory {@code after}.
 * The migration writes its report to the file {@code <class name>.items}, which the directory {@code after} contains too.
 * The migration and the comparison run on the EDT, as {@link LightMultiFileFixture#doTest} runs them.
 *
 * @author anna
 */
public final class TypeMigrationTestUtil {
  private TypeMigrationTestUtil() {
  }

  /**
   * Returns the test data directory of the module. The test classes add their own subdirectory.
   */
  public static @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData";
  }

  public static void doTestAnonymousClassTypeParameters(@NotNull LightMultiFileFixture multiFile,
                                                        @NotNull String assignmentVariableName,
                                                        PsiType toType) {
    doTestAnonymousClassTypeParameters(multiFile, assignmentVariableName, "Test", toType);
  }

  public static void doTestAnonymousClassTypeParameters(@NotNull LightMultiFileFixture multiFile,
                                                        final @NotNull String assignmentVariableName,
                                                        final String className,
                                                        final PsiType toType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return toType;
      }

      @Override
      public PsiElement victim(PsiClass aClass) {
        for (PsiLocalVariable variable : PsiTreeUtil.findChildrenOfType(aClass, PsiLocalVariable.class)) {
          if (assignmentVariableName.equals(variable.getName())) {
            final PsiAnonymousClass anonymousClass = PsiTreeUtil.findChildOfType(variable.getInitializer(), PsiAnonymousClass.class);
            assertNotNull(anonymousClass);
            return anonymousClass.getBaseClassReference().getParameterList();
          }
        }
        throw new RuntimeException(String.format("Local variable '%s' isn't found in class '%s'", assignmentVariableName, className));
      }
    };

    start(multiFile, provider, className);
  }

  public static void doTestFieldType(@NotNull LightMultiFileFixture multiFile, @NonNls String fieldName, PsiType toType) {
    doTestFieldType(multiFile, fieldName, "Test", toType);
  }

  public static void doTestFieldType(@NotNull LightMultiFileFixture multiFile,
                                     final @NonNls String fieldName,
                                     String className,
                                     final PsiType migrationType) {
    doTestFieldsType(multiFile, className, migrationType, fieldName);
  }

  public static void doTestFieldsType(@NotNull LightMultiFileFixture multiFile,
                                      @NotNull String className,
                                      final @NotNull PsiType migrationType,
                                      final String... fieldNames) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return migrationType;
      }

      @Override
      public PsiElement[] victims(PsiClass aClass) {
        return Arrays
          .stream(fieldNames)
          .map(n -> aClass.findFieldByName(n, false))
          .peek(Assertions::assertNotNull)
          .toArray(PsiElement[]::new);
      }
    };

    start(multiFile, provider, className);
  }

  public static void doTestMethodType(@NotNull LightMultiFileFixture multiFile, final @NonNls String methodName, final PsiType migrationType) {
    doTestMethodType(multiFile, methodName, "Test", migrationType);
  }

  public static void doTestMethodType(@NotNull LightMultiFileFixture multiFile,
                                      final @NonNls String methodName,
                                      @NonNls String className,
                                      final PsiType migrationType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return migrationType;
      }

      @Override
      public PsiElement victim(PsiClass aClass) {
        return aClass.findMethodsByName(methodName, false)[0];
      }
    };

    start(multiFile, provider, className);
  }

  public static void doTestFirstParamType(@NotNull LightMultiFileFixture multiFile,
                                          final @NonNls String methodName,
                                          final PsiType migrationType) {
    doTestFirstParamType(multiFile, methodName, "Test", migrationType);
  }

  public static void doTestFirstParamType(@NotNull LightMultiFileFixture multiFile,
                                          final @NonNls String methodName,
                                          String className,
                                          final PsiType migrationType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return migrationType;
      }

      @Override
      public PsiElement victim(PsiClass aClass) {
        return aClass.findMethodsByName(methodName, false)[0].getParameterList().getParameters()[0];
      }
    };

    start(multiFile, provider, className);
  }

  public static void start(@NotNull LightMultiFileFixture multiFile, final RulesProvider provider) {
    start(multiFile, provider, "Test");
  }

  public static void start(@NotNull LightMultiFileFixture multiFile, final RulesProvider provider, final String className) {
    multiFile.doTest(() -> performAction(multiFile.getFixture(), className, provider));
  }

  private static void performAction(LightJavaCodeInsightTestFixture myFixture, String className, RulesProvider provider) throws Exception {
    PsiClass aClass = myFixture.findClass(className);
    final PsiElement[] migrationElements = provider.victims(aClass);
    final PsiType migrationType = provider.migrationType(migrationElements[0]);
    final TypeMigrationRules rules = new TypeMigrationRules(myFixture.getProject());
    rules.setBoundScope(new LocalSearchScope(aClass.getContainingFile()));
    final TestTypeMigrationProcessor pr = new TestTypeMigrationProcessor(myFixture.getProject(), migrationElements, migrationType, rules);

    final UsageInfo[] usages = pr.findUsages();
    final String report = pr.getLabeler().getMigrationReport();

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> pr.performRefactoring(usages));

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting());

    //wait for WA to be actually executed (on EDT):
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    myFixture.getTempDirFixture().createFile(className + ".items", report);
  }

  /**
   * Gives the elements to migrate in the class of the test and the type to migrate them to.
   */
  public interface RulesProvider {
    PsiType migrationType(PsiElement context);

    default PsiElement victim(PsiClass aClass) {
      fail("You need to override one of victim(PsiClass) or victims(PsiClass) methods");
      return null;
    }

    default PsiElement[] victims(PsiClass aClass) {
      return new PsiElement[] {victim(aClass)};
    }
  }

  private static class TestTypeMigrationProcessor extends TypeMigrationProcessor {
    TestTypeMigrationProcessor(final Project project, final PsiElement[] roots, final PsiType migrationType, final TypeMigrationRules rules) {
      super(project, roots, Functions.constant(migrationType), rules, true);
    }
  }
}
