// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;

public final class JavacQuirksInspectionMultiModuleTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.addJdkVersion(LanguageLevel.JDK_25);
  }

  public void testMissedTransitiveDepTypeAnnotations() throws IOException {
    Sdk sdk = ModuleRootManager.getInstance(getModule()).getSdk();
    com.intellij.openapi.module.Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    com.intellij.openapi.module.Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    Module mod3 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod3", myFixture.getTempDirFixture().findOrCreateDir("mod3"));
    ModuleRootModificationUtil.setModuleSdk(mod1, sdk);
    ModuleRootModificationUtil.setModuleSdk(mod2, sdk);
    ModuleRootModificationUtil.setModuleSdk(mod3, sdk);
    ModuleRootModificationUtil.addDependency(mod2, mod1);
    ModuleRootModificationUtil.addDependency(mod3, mod2);
    myFixture.addFileToProject("mod1/Baz.java", "public class Baz<T> {}");
    myFixture.addFileToProject("mod2/Anno.java", """
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Target;
      
      @Target(ElementType.TYPE_USE)
      public @interface Anno {
      }
      """);
    myFixture.addFileToProject("mod2/Bar.java", """
      import java.util.List;
      
      public class Bar {
        private Baz<@Anno ? extends String> baz;
      
        private void test(List<@Anno Baz> list,
                          List<@Anno Baz<String>> list2) {}
      }
      """);
    PsiFile file = myFixture.addFileToProject("mod3/Foo.java", """
      public class Foo {
        public Foo() {
          <warning descr="Java compiler may require dependency from 'mod3' to 'mod1' to process type annotations of 'Bar.baz'"><warning descr="Java compiler may require dependency from 'mod3' to 'mod1' to process type annotations of parameter 'list2' of 'Bar.test'">Bar</warning></warning> bar;
        }
      }
      """);
    myFixture.configureFromExistingVirtualFile(PsiUtilCore.getVirtualFile(file));
    myFixture.enableInspections(new JavacQuirksInspection());
    IdeaTestUtil.withLevel(mod3, LanguageLevel.JDK_25, myFixture::checkHighlighting);
  }
}
