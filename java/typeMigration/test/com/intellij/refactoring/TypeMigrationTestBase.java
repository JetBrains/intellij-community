/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 * @author anna
 * Date: 30-Apr-2008
 */
public abstract class TypeMigrationTestBase extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData";
  }

  protected void doTestFieldType(@NonNls String fieldName, PsiType fromType, PsiType toType) {
    doTestFieldType(fieldName, "Test", fromType, toType);
  }

  protected void doTestFieldType(@NonNls final String fieldName, String className, final PsiType rootType, final PsiType migrationType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public TypeMigrationRules provide() throws Exception {
        final TypeMigrationRules rules = new TypeMigrationRules(rootType);
        rules.setMigrationRootType(migrationType);
        return rules;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        final PsiField field = aClass.findFieldByName(fieldName, false);
        assert field != null : fieldName + " not found in " + aClass;
        return field;
      }
    };

    start(provider, className);
  }

  protected void doTestMethodType(@NonNls final String methodName, final PsiType rootType, final PsiType migrationType) {
    doTestMethodType(methodName, "Test", rootType, migrationType);
  }

  protected void doTestMethodType(@NonNls final String methodName, @NonNls String className, final PsiType rootType, final PsiType migrationType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public TypeMigrationRules provide() throws Exception {
        final TypeMigrationRules rules = new TypeMigrationRules(rootType);
        rules.setMigrationRootType(migrationType);
        return rules;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        return aClass.findMethodsByName(methodName, false)[0];
      }
    };

    start(provider, className);
  }

  protected void doTestFirstParamType(@NonNls final String methodName, final PsiType rootType, final PsiType migrationType) {
    doTestFirstParamType(methodName, "Test", rootType, migrationType);
  }

  protected void doTestFirstParamType(@NonNls final String methodName, String className, final PsiType rootType, final PsiType migrationType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public TypeMigrationRules provide() throws Exception {
        final TypeMigrationRules rules = new TypeMigrationRules(rootType);
        rules.setMigrationRootType(migrationType);
        return rules;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        return aClass.findMethodsByName(methodName, false)[0].getParameterList().getParameters()[0];
      }
    };

    start(provider, className);
  }

  public void start(final RulesProvider provider) {
    start(provider, "Test");
  }

  public void start(final RulesProvider provider, final String className) {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        TypeMigrationTestBase.this.performAction(className, rootDir.getName(), provider);
      }
    });
  }

  private void performAction(String className, String rootDir, RulesProvider provider) throws Exception {
    PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));

    assertNotNull("Class " + className + " not found", aClass);

    final TypeMigrationRules rules = provider.provide();
    rules.setBoundScope(new LocalSearchScope(aClass.getContainingFile()));
    final TestTypeMigrationProcessor pr = new TestTypeMigrationProcessor(getProject(), provider.victims(aClass), rules);

    final UsageInfo[] usages = pr.findUsages();
    final String report = pr.getLabeler().getMigrationReport();

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      public void run() {
        pr.performRefactoring(usages);
      }
    });


    String itemName = className + ".items";
    String patternName = getTestDataPath() + getTestRoot() + getTestName(true) + "/after/" + itemName;

    File patternFile = new File(patternName);

    if (!patternFile.exists()) {
      PrintWriter writer = new PrintWriter(new FileOutputStream(patternFile));
      try {
        writer.print(report);
        writer.close();
      }
      finally {
        writer.close();
      }

      System.out.println("Pattern not found, file " + patternName + " created.");

      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(patternFile);
    }

    File graFile = new File(FileUtil.getTempDirectory() + File.separator + rootDir + File.separator + itemName);

    PrintWriter writer = new PrintWriter(new FileOutputStream(graFile));
    try {
      writer.print(report);
      writer.close();
    }
    finally {
      writer.close();
    }

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(graFile);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  interface RulesProvider {
    TypeMigrationRules provide() throws Exception;

    PsiElement victims(PsiClass aClass);
  }

  private static class TestTypeMigrationProcessor extends TypeMigrationProcessor {
    public TestTypeMigrationProcessor(final Project project, final PsiElement root, final TypeMigrationRules rules) {
      super(project, root, rules);
    }

    @NotNull
    @Override
    public UsageInfo[] findUsages() {
      return super.findUsages();
    }

    @Override
    public void performRefactoring(@NotNull final UsageInfo[] usages) {
      super.performRefactoring(usages);
    }
  }
}