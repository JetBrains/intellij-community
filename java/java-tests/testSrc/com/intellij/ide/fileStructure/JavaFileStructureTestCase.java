/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.testFramework.FileStructureTestBase;
import com.intellij.testFramework.IdeaTestCase;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaFileStructureTestCase extends FileStructureTestBase {
  private boolean myShowAnonymousByDefault;

  protected JavaFileStructureTestCase() {
    IdeaTestCase.initPlatformPrefix();
  }

  protected abstract String getTestDataFolderName();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myShowAnonymousByDefault = PropertiesComponent.getInstance().getBoolean(getAnonymousPropertyName(), false);
    if (getTestName(false).contains("Anonymous")) {
      setShowAnonymous(true);
    }
  }

  @Override
  protected String getFileExtension() {
    return "java";
  }

  public void setShowAnonymous(boolean show) throws Exception {
    myPopup.setTreeActionState(JavaAnonymousClassesNodeProvider.class, show);
    update();
  }

  public void setShowParents(boolean show) throws Exception {
    myPopup.setTreeActionState(JavaInheritedMembersNodeProvider.class, show);
    update();
  }

  @Override
  public void tearDown() throws Exception {
    PropertiesComponent.getInstance().setValue(getAnonymousPropertyName(), Boolean.toString(myShowAnonymousByDefault));
    super.tearDown();
  }

  private static String getAnonymousPropertyName() {
    return FileStructurePopup.getPropertyName(JavaAnonymousClassesNodeProvider.JAVA_ANONYMOUS_PROPERTY_NAME);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/fileStructure/" + getTestDataFolderName();
  }
}
