/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.java.ide.fileStructure;

import com.intellij.JavaTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.FileStructureTestFixture;
import com.intellij.testFramework.FileStructureTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaFileStructureTestCase {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  protected CodeInsightTestFixture myFixture;
  protected FileStructureTestFixture myPopupFixture;
  protected String myTestMethodName;
  private boolean myShowAnonymousByDefault;

  protected abstract String getTestDataFolderName();

  @BeforeEach
  void setUpFileStructure(TestInfo testInfo) {
    myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(getTestDataPath());
    myPopupFixture = new FileStructureTestFixture(myFixture);
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    myShowAnonymousByDefault = PropertiesComponent.getInstance().getBoolean(getAnonymousPropertyName());
  }

  protected void checkTree() {
    checkTree(null);
  }

  protected void checkTree(@Nullable String filter) {
    FileStructureTestUtil.configureDefault(myFixture, myTestMethodName, "java");
    EdtTestUtil.runInEdtAndWait(() -> {
      configurePopup();
      FileStructureTestUtil.checkConfiguredTree(myFixture, myPopupFixture, myTestMethodName, filter, true);
    });
  }

  protected void configurePopup() {
    if (FileStructureTestUtil.getTestName(myTestMethodName).contains("Anonymous")) {
      setShowAnonymous(true);
    }
  }

  public void setShowAnonymous(boolean show) {
    myPopupFixture.getPopup().setTreeActionState(JavaAnonymousClassesNodeProvider.ID, show);
    myPopupFixture.updateAndSelectCurrent();
  }

  public void setShowParents(boolean show) {
    myPopupFixture.getPopup().setTreeActionState(InheritedMembersNodeProvider.ID, show);
    myPopupFixture.updateAndSelectCurrent();
  }

  @AfterEach
  void tearDownFileStructure() {
    try {
      PropertiesComponent.getInstance().setValue(getAnonymousPropertyName(), myShowAnonymousByDefault);
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myPopupFixture));
    }
  }

  private static String getAnonymousPropertyName() {
    return TreeStructureUtil.getPropertyName(JavaAnonymousClassesNodeProvider.JAVA_ANONYMOUS_PROPERTY_NAME);
  }

  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/fileStructure/" + getTestDataFolderName();
  }
}
