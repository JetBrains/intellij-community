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
package com.intellij.ide.fileStructure;

import com.intellij.JavaTestUtil;
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.testFramework.FileStructureTestBase;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaFileStructureTestCase extends FileStructureTestBase {
  private boolean myShowAnonymousByDefault;
  
  protected abstract String getTestDataFolderName();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myShowAnonymousByDefault = PropertiesComponent.getInstance().getBoolean(getAnonymousPropertyName());
  }

  @Override
  protected void configureDefault() {
    super.configureDefault();
    if (getTestName(false).contains("Anonymous")) {
      setShowAnonymous(true);
    }
  }

  @Override
  protected String getFileExtension() {
    return "java";
  }

  public void setShowAnonymous(boolean show) {
    myPopupFixture.getPopup().setTreeActionState(JavaAnonymousClassesNodeProvider.class, show);
    myPopupFixture.update();
  }

  public void setShowParents(boolean show) {
    myPopupFixture.getPopup().setTreeActionState(JavaInheritedMembersNodeProvider.class, show);
    myPopupFixture.update();
  }

  @Override
  public void tearDown() throws Exception {
    PropertiesComponent.getInstance().setValue(getAnonymousPropertyName(), myShowAnonymousByDefault);
    super.tearDown();
  }

  private static String getAnonymousPropertyName() {
    return TreeStructureUtil.getPropertyName(JavaAnonymousClassesNodeProvider.JAVA_ANONYMOUS_PROPERTY_NAME);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/fileStructure/" + getTestDataFolderName();
  }
}
