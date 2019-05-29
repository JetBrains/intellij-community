// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.platform.templates.RemoteTemplatesFactory;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.MultiMap;

/**
 * @author Dmitry Avdeev
 */
public class RemoteTemplatesTest extends NewProjectWizardTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("new.project.load.remote.templates").setValue(true, getTestRootDisposable());
  }

  public void testParsing() throws Exception {
    MultiMap<String, ArchivedProjectTemplate> map = RemoteTemplatesFactory.createFromText(
      "<templates>\n" +
      "  <template>\n" +
      "    <name>Facelets Demo</name>\n" +
      "    <description><![CDATA[\n" +
      "    Demonstrates IDEA support for Facelets technology\n" +
      "    ]]>\n" +
      "    </description>\n" +
      "    <path>facelets.zip</path>\n" +
      "    <moduleType>JAVA_MODULE</moduleType>\n" +
      "  </template>\n" +
      "  <template>\n" +
      "    <name>Incompatible</name>\n" +
      "    <description>Incompatible</description>\n" +
      "    <path>incompatible.zip</path>\n" +
      "    <moduleType>JAVA_MODULE</moduleType>\n" +
      "    <requiredPlugin>unknown.plugin</requiredPlugin>\n" +
      "  </template>\n" +
      "</templates>");
    assertEquals(1, map.size());

    ProjectTemplate facelets = map.values().iterator().next();
    assertEquals("Facelets Demo", facelets.getName());
    assertEquals("Demonstrates IDEA support for Facelets technology", facelets.getDescription());
    ModuleBuilder builder = (ModuleBuilder)facelets.createModuleBuilder();
    assertEquals(JavaModuleType.getModuleType(), builder.getModuleType());
  }

  public void testHelloWorld() throws Exception {
    createSdk("foo", JavaSdk.getInstance());
    createProjectFromTemplate(JavaModuleType.JAVA_GROUP, "Java Hello World", null);
  }

  public void testLanguageLevel() throws Exception {
    LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    createSdk("foo", JavaSdk.getInstance());
    Project project = createProjectFromTemplate(JavaModuleType.JAVA_GROUP, "Java Hello World", null);
    LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
    assertEquals(LanguageLevel.JDK_1_8, level);
    assertFalse(LanguageLevelProjectExtension.getInstance(project).isDefault());
  }

  public void testDefaultSdkLanguageLevel() throws Exception {
    LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject()).setDefault(Boolean.TRUE);
    createSdk("foo", JavaSdk.getInstance());
    Project project = createProjectFromTemplate(JavaModuleType.JAVA_GROUP, "Java Hello World", null);
    assertTrue(LanguageLevelProjectExtension.getInstance(project).isDefault());
  }

  public void testCharset() throws Exception {
    EncodingProjectManager manager = EncodingProjectManager.getInstance(ProjectManager.getInstance().getDefaultProject());
    String old = manager.getDefaultCharsetName();
    manager.setDefaultCharsetName("UTF-16");
    try {
      createSdk("foo", JavaSdk.getInstance());
      Project project = createProjectFromTemplate(JavaModuleType.JAVA_GROUP, "Java Hello World", null);
      assertEquals("UTF-16", EncodingProjectManager.getInstance(project).getDefaultCharsetName());
    }
    finally {
      manager.setDefaultCharsetName(old);
    }
  }
}
