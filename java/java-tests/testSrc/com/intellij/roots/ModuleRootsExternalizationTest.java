// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 *  @author dsl
 */
public class ModuleRootsExternalizationTest extends JavaModuleTestCase {
  public void testEmptyModuleWrite() {
    ModuleRootManager moduleRootManager = createTempModuleRootManager();
    if (moduleRootManager instanceof ModuleRootManagerImpl) {
      Element root = new Element("root");
      ((ModuleRootManagerImpl)moduleRootManager).getState().writeExternal(root);
      assertThat(root.getText()).isEmpty();
    }
  }

  private ModuleRootManager createTempModuleRootManager() {
    File tmpModule = getTempDir().createTempFile("tst", ModuleFileType.DOT_DEFAULT_EXTENSION, false);
    Module module = createModule(tmpModule);
    return ModuleRootManager.getInstance(module);
  }

  public void testContentWrite() throws IOException, JDOMException {
    File content = new File(getProject().getBasePath());
    File source = new File(content, "source");
    File testSource = new File(content, "testSource");
    File exclude = new File(content, "exclude");
    File classes = new File(content, "classes");
    File testClasses = new File(content, "testClasses");
    FileUtil.createDirectory(source);
    FileUtil.createDirectory(testSource);
    FileUtil.createDirectory(exclude);
    FileUtil.createDirectory(classes);
    FileUtil.createDirectory(testClasses);
    final VirtualFile contentFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(content);
    assertNotNull(contentFile);
    refreshRecursively(contentFile);
    final VirtualFile sourceFile = LocalFileSystem.getInstance().findFileByIoFile(source);
    assertNotNull(sourceFile);
    final VirtualFile testSourceFile = LocalFileSystem.getInstance().findFileByIoFile(testSource);
    assertNotNull(testSourceFile);
    final VirtualFile excludeFile = LocalFileSystem.getInstance().findFileByIoFile(exclude);

    assertNotNull(excludeFile);
    final VirtualFile classesFile = LocalFileSystem.getInstance().findFileByIoFile(classes);

    assertNotNull(classesFile);
    final VirtualFile testClassesFile = LocalFileSystem.getInstance().findFileByIoFile(testClasses);

    assertNotNull(testClassesFile);

    final File moduleFile = new File(content, "test.iml");
    final Module module = createModule(moduleFile);

    PsiTestUtil.addContentRoot(module, contentFile);
    PsiTestUtil.addSourceRoot(module, sourceFile);
    PsiTestUtil.addSourceRoot(module, testSourceFile, true);
    ModuleRootModificationUtil.setModuleSdk(module, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.addExcludedRoot(module, excludeFile);
    PsiTestUtil.setCompilerOutputPath(module, classesFile.getUrl(), false);
    PsiTestUtil.setCompilerOutputPath(module, testClassesFile.getUrl(), true);

    StoreUtil.saveDocumentsAndProjectSettings(myProject);

    assertEquals(
      "<component name=\"NewModuleRootManager\">\n" +
      "  <output url=\"file://$MODULE_DIR$/classes\" />\n" +
      "  <output-test url=\"file://$MODULE_DIR$/testClasses\" />\n" +
      "  <exclude-output />\n" +
      "  <content url=\"file://$MODULE_DIR$\">\n" +
      "    <sourceFolder url=\"file://$MODULE_DIR$/source\" isTestSource=\"false\" />\n" +
      "    <sourceFolder url=\"file://$MODULE_DIR$/testSource\" isTestSource=\"true\" />\n" +
      "    <excludeFolder url=\"file://$MODULE_DIR$/exclude\" />\n" +
      "  </content>\n" +
      "  <orderEntry type=\"jdk\" jdkName=\"java 1.7\" jdkType=\"JavaSDK\" />\n" +
      "  <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
      "</component>",
      JDOMUtil.writeElement(JDOMUtil.load(moduleFile).getChild("component"))
    );
  }

  public void testJavaExternalPaths() throws JDOMException, IOException {
    File content = new File(getProject().getBasePath());
    VirtualFile contentRoot = refreshAndFindFile(content);
    File javadocDir = new File(content, "javadoc");
    File annotationsDir = new File(content, "annotations");
    FileUtil.createDirectory(javadocDir);
    FileUtil.createDirectory(annotationsDir);
    String javadocUrl = refreshAndFindFile(javadocDir).getUrl();
    String annotationsUrl = refreshAndFindFile(annotationsDir).getUrl();
    File moduleFile = new File(content, "test.iml");
    Module module = createModule(moduleFile);
    ModuleRootModificationUtil.updateModel(module, (model) -> {
      JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(new String[]{annotationsUrl});
      extension.setJavadocUrls(new String[]{javadocUrl});
    });

    StoreUtil.saveDocumentsAndProjectSettings(myProject);

    assertEquals(
      "<component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
      "  <exclude-output />\n" +
      "  <annotation-paths>\n" +
      "    <root url=\"file://$MODULE_DIR$/annotations\" />\n" +
      "  </annotation-paths>\n" +
      "  <javadoc-paths>\n" +
      "    <root url=\"file://$MODULE_DIR$/javadoc\" />\n" +
      "  </javadoc-paths>\n" +
      "  <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
      "</component>",
      JDOMUtil.writeElement(JDOMUtil.load(moduleFile).getChild("component"))
    );
  }

  private static Collection<JpsModuleSourceRootPropertiesSerializer<?>> findSerializers(Collection<JpsModuleSourceRootType<?>> rootTypes) {
    final Set<JpsModuleSourceRootType<?>> typesSet = rootTypes instanceof Set ? (Set<JpsModuleSourceRootType<?>>)rootTypes : new HashSet<>(rootTypes);
    Set<JpsModuleSourceRootPropertiesSerializer<?>> result = new HashSet<>();
    for (JpsModelSerializerExtension ext : JpsModelSerializerExtension.getExtensions()) {
      result.addAll(
        ext.getModuleSourceRootPropertiesSerializers().stream().filter(serializer -> typesSet.contains(serializer.getType())).collect(Collectors.toSet())
      );
    }
    return result;
  }

  public void testModuleLibraries() throws IOException, JDOMException {
    File moduleFile = new File(myProject.getBasePath(), "test.iml");
    Module module = createModule(moduleFile);
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    final LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();

    final Library unnamedLibrary = moduleLibraryTable.createLibrary();
    final File unnamedLibClasses = new File(myProject.getBasePath(), "unnamedLibClasses");
    FileUtil.createDirectory(unnamedLibClasses);
    final VirtualFile unnamedLibClassesRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(unnamedLibClasses);
    final Library.ModifiableModel libraryModifiableModel = unnamedLibrary.getModifiableModel();
    libraryModifiableModel.addRoot(unnamedLibClassesRoot.getUrl(), OrderRootType.CLASSES);

    final Library namedLibrary = moduleLibraryTable.createLibrary("namedLibrary");
    final File namedLibClasses = new File(myProject.getBasePath(), "namedLibClasses");
    FileUtil.createDirectory(namedLibClasses);
    final VirtualFile namedLibClassesRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(namedLibClasses);
    final Library.ModifiableModel namedLibraryModel = namedLibrary.getModifiableModel();
    namedLibraryModel.addRoot(namedLibClassesRoot.getUrl(), OrderRootType.CLASSES);

    ApplicationManager.getApplication().runWriteAction(() -> {
      libraryModifiableModel.commit();
      namedLibraryModel.commit();
    });

    final Iterator libraryIterator = moduleLibraryTable.getLibraryIterator();
    assertEquals(libraryIterator.next(), unnamedLibrary);
    assertEquals(libraryIterator.next(), namedLibrary);

    ApplicationManager.getApplication().runWriteAction(rootModel::commit);
    StoreUtil.saveDocumentsAndProjectSettings(myProject);

    assertEquals(
      "<component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
      "  <exclude-output />\n" +
      "  <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
      "  <orderEntry type=\"module-library\">\n" +
      "    <library>\n" +
      "      <CLASSES>\n" +
      "        <root url=\"file://$MODULE_DIR$/unnamedLibClasses\" />\n" +
      "      </CLASSES>\n" +
      "      <JAVADOC />\n" +
      "      <SOURCES />\n" +
      "    </library>\n" +
      "  </orderEntry>\n" +
      "  <orderEntry type=\"module-library\">\n" +
      "    <library name=\"namedLibrary\">\n" +
      "      <CLASSES>\n" +
      "        <root url=\"file://$MODULE_DIR$/namedLibClasses\" />\n" +
      "      </CLASSES>\n" +
      "      <JAVADOC />\n" +
      "      <SOURCES />\n" +
      "    </library>\n" +
      "  </orderEntry>\n" +
      "</component>",
      JDOMUtil.writeElement(JDOMUtil.load(moduleFile).getChild("component"))
    );
  }

  public void testCompilerOutputInheritance() throws IOException, JDOMException {
    File moduleFile = new File(myProject.getBasePath(), "test.iml");
    Module module = createModule(moduleFile);
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    rootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);
    ApplicationManager.getApplication().runWriteAction(rootModel::commit);
    StoreUtil.saveDocumentsAndProjectSettings(myProject);

    assertEquals(
      "<component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n" +
      "  <exclude-output />\n" +
      "  <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
      "</component>",
      JDOMUtil.writeElement(JDOMUtil.load(moduleFile).getChild("component"))
    );
  }

  public void testMacroSubstituteWin() {
    final ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    final String path = "jar://C:/idea/lib/forms_rt.jar!/";
    map.put("jar://C:/", "jar://$MODULE_DIR$/../../");

    final String substituted = map.substitute(path, false);
    assertEquals("jar://$MODULE_DIR$/../../idea/lib/forms_rt.jar!/", substituted);
  }

  public void testExpandMacro1() {
    final ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand("MACRO", "C:/idea");

    final String expanded = map.substitute("jar://$MACRO$/lib/forms_rt.jar!/", false);
    assertEquals("jar://C:/idea/lib/forms_rt.jar!/", expanded);
  }

  public void testExpandMacro2() {
    final ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand("MACRO", "forms_rt.jar!/");

    final String expanded = map.substitute("jar://C:/idea/lib/$MACRO$", false);
    assertEquals("jar://C:/idea/lib/forms_rt.jar!/", expanded);
  }

  public void testExpandMacro3() {
    final ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand("MACRO1", "C:/idea");
    map.addMacroExpand("MACRO2", "forms_rt.jar!/");

    final String expanded = map.substitute("jar://$MACRO1$/lib/$MACRO2$", false);
    assertEquals("jar://C:/idea/lib/forms_rt.jar!/", expanded);
  }

  public void testExpandMacroNoExpand() {
    final ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand("MACRO", "C:/idea");

    final String expanded = map.substitute("jar://C:/idea$/lib/forms_rt.jar!/", false);
    assertEquals("jar://C:/idea$/lib/forms_rt.jar!/", expanded);
  }

  public void testExpandMacroNoExpand2() {
    final ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand("MACRO", "C:/idea");

    final String expanded = map.substitute("jar://C:/idea/lib/forms_rt.jar!/$", false);
    assertEquals("jar://C:/idea/lib/forms_rt.jar!/$", expanded);
  }

  public void testExpandMacroNoExpand3() {
    final ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand("MACRO", "C:/idea");

    final String expanded = map.substitute("jar://$UNKNOWN$/lib/forms_rt.jar!/", false);
    assertEquals("jar://$UNKNOWN$/lib/forms_rt.jar!/", expanded);
  }

  public void testMacroSubstitute2() {
    final ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    final String path = "jar://C:/idea/lib/forms_rt.jar!/";
    map.put("jar://C:/id", "*SUBST*");

    final String substituted = map.substitute(path, false);
    assertEquals(path, substituted);
  }
}
