// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Functions;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author anna
 */
public abstract class TypeMigrationTestBase extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData";
  }

  protected void doTestAnonymousClassTypeParameters(@NotNull String assignmentVariableName,
                                                    PsiType toType) {
    doTestAnonymousClassTypeParameters(assignmentVariableName, "Test", toType);
  }

  protected void doTestAnonymousClassTypeParameters(@NotNull final String assignmentVariableName,
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

    start(provider, className);
  }

  protected void doTestFieldType(@NonNls String fieldName, PsiType toType) {
    doTestFieldType(fieldName, "Test", toType);
  }

  protected void doTestFieldType(@NonNls final String fieldName, String className, final PsiType migrationType) {
    doTestFieldsType(className, migrationType, fieldName);
  }

  protected void doTestFieldsType(@NotNull String className, @NotNull final PsiType migrationType, final String... fieldNames) {
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
          .peek(TestCase::assertNotNull)
          .toArray(PsiElement[]::new);
      }
    };

    start(provider, className);
  }

  protected void doTestMethodType(@NonNls final String methodName, final PsiType migrationType) {
    doTestMethodType(methodName, "Test", migrationType);
  }

  protected void doTestMethodType(@NonNls final String methodName,
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

    start(provider, className);
  }

  protected void doTestFirstParamType(@NonNls final String methodName, final PsiType migrationType) {
    doTestFirstParamType(methodName, "Test", migrationType);
  }

  protected void doTestFirstParamType(@NonNls final String methodName, String className, final PsiType migrationType) {
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

    start(provider, className);
  }

  public void start(final RulesProvider provider) {
    start(provider, "Test");
  }

  public void start(final RulesProvider provider, final String className) {
    doTest(() -> this.performAction(className, provider));
  }

  private void performAction(String className, RulesProvider provider) throws Exception {
    PsiClass aClass = myFixture.findClass(className);
    final PsiElement[] migrationElements = provider.victims(aClass);
    final PsiType migrationType = provider.migrationType(migrationElements[0]);
    final TypeMigrationRules rules = new TypeMigrationRules(getProject());
    rules.setBoundScope(new LocalSearchScope(aClass.getContainingFile()));
    final TestTypeMigrationProcessor pr = new TestTypeMigrationProcessor(getProject(), migrationElements, migrationType, rules);

    final UsageInfo[] usages = pr.findUsages();
    final String report = pr.getLabeler().getMigrationReport();

    WriteCommandAction.runWriteCommandAction(null, () -> pr.performRefactoring(usages));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting());

    myFixture.getTempDirFixture().createFile(className + ".items", report);
  }

  interface RulesProvider {
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_6;
  }
}