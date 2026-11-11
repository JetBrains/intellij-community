// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class MakeExternalAnnotationExplicitTest {
  private static final DefaultLightProjectDescriptor DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      for (VirtualFile root : model.getContentRoots()) {
        try {
          root.createChildDirectory(null, "anno");
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      extension.setExternalAnnotationUrls(ContainerUtil.map2Array(model.getContentRootUrls(),
                                                                  ArrayUtil.EMPTY_STRING_ARRAY, root -> root + "/anno"));
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testMakeExplicit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
        class Test {
            public String <caret>foo() {
                return "xyz";
            }
        }
        """);
    PsiMethod method = ReadAction.computeBlocking(() -> ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0]);
    EdtTestUtil.runInEdtAndWait(() -> ExternalAnnotationsManager.getInstance(myFixture.getProject()).annotateExternally(method, CommonClassNames.JAVA_LANG_OVERRIDE, myFixture.getFile(), null));
    IntentionAction action = myFixture.findSingleIntention("Insert '@Override'");
    assertEquals("""
                   class Test {
                       @Override
                       public String foo() {
                           return "xyz";
                       }
                   }
                                      
                   ----------
                   <root>
                   </root>""", myFixture.getIntentionPreviewText(action));
    myFixture.launchAction(action);
    myFixture.checkResult("""
                            class Test {
                                @Override
                                public String foo() {
                                    return "xyz";
                                }
                            }
                            """);
  }
}
