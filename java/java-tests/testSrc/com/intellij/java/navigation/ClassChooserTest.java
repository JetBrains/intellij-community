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
package com.intellij.java.navigation;

import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNameModelEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonProcessors;
import com.intellij.util.indexing.FindSymbolParameters;
import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class ClassChooserTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSubclassModel() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class Foo extends Exception {}");
    myFixture.addClass("class Bar {}");
    PsiClass aClass =
      ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(myFixture.getProject()).findClass(CommonClassNames.JAVA_LANG_EXCEPTION, GlobalSearchScope.allScope(myFixture.getProject())));
    EdtTestUtil.runInEdtAndWait(() -> {
      final Ref<ChooseByNameModel> ref = new Ref<>();
      TreeJavaClassChooserDialog dialog =
        new TreeJavaClassChooserDialog("hey", myFixture.getProject(), GlobalSearchScope.projectScope(myFixture.getProject()), null, aClass, null, false) {
          @Override
          protected ChooseByNameModel createChooseByNameModel() {
            ChooseByNameModel model = super.createChooseByNameModel();
            ref.set(model);
            return model;
          }

          @Override
          public JRootPane getRootPane() {
            return new JRootPane();
          }
        };
      Disposer.register(myFixture.getTestRootDisposable(), dialog.getDisposable());

      ChooseByNameModelEx model = (ChooseByNameModelEx)ref.get();
      CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<>();
      model.processNames(processor, FindSymbolParameters.simple(myFixture.getProject(), false));
      List<String> results = (List<String>)processor.getResults();
      assertEquals(1, results.size());
    });
  }
}
