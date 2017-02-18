/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.navigation;

import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNameModelEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ClassChooserTest extends LightCodeInsightFixtureTestCase {

  public void testSubclassModel() throws Exception {

    myFixture.addClass("class Foo extends Exception {}");
    myFixture.addClass("class Bar {}");
    PsiClass aClass =
      JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_LANG_EXCEPTION, GlobalSearchScope.allScope(getProject()));
    final Ref<ChooseByNameModel> ref = new Ref<>();
    TreeJavaClassChooserDialog dialog =
      new TreeJavaClassChooserDialog("hey", getProject(), GlobalSearchScope.projectScope(getProject()), null, aClass, null, false) {
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
    Disposer.register(getTestRootDisposable(), dialog.getDisposable());

    ChooseByNameModelEx model = (ChooseByNameModelEx)ref.get();
    CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<>();
    model.processNames(processor, false);
    List<String> results = (List<String>)processor.getResults();
    assertEquals(1, results.size());
  }
}
