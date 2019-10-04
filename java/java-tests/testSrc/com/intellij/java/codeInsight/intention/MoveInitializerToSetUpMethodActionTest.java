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
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class MoveInitializerToSetUpMethodActionTest extends LightIntentionActionTestCase {
  private final static DefaultLightProjectDescriptor ourProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit4");
      PsiTestUtil.addLibrary(model, "JUnit4", "", ArrayUtil.toStringArray(junit4Library.getClassesPaths()));
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourProjectDescriptor;
  }


  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/moveInitializerToSetUp";
  }
}
