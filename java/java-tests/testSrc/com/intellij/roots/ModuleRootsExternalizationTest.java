// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 *  @author dsl
 */
public class ModuleRootsExternalizationTest extends ModuleTestCase {
  public void testEmptyModuleWrite() {
    ModuleRootManagerImpl moduleRootManager = createTempModuleRootManager();
    Element root = new Element("root");
    moduleRootManager.getState().writeExternal(root);
    assertThat(root.getText()).isEmpty();
  }

  private ModuleRootManagerImpl createTempModuleRootManager() {
    File tmpModule = getTempDir().createTempFile("tst", ModuleFileType.DOT_DEFAULT_EXTENSION, false);
    myFilesToDelete.add(tmpModule);
    final Module module = createModule(tmpModule);
    return (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
  }

  public void testContentWrite() throws IOException {
    File content = getTestRoot();
    File source = new File(content, "source");
    File testSource = new File(content, "testSource");
    File exclude = new File(content, "exclude");
    File classes = new File(content, "classes");
    File testClasses = new File(content, "testClasses");
    final VirtualFile contentFile = LocalFileSystem.getInstance().findFileByIoFile(content);
    assertNotNull(contentFile);
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
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);

    PsiTestUtil.addContentRoot(module, contentFile);
    PsiTestUtil.addSourceRoot(module, sourceFile);
    PsiTestUtil.addSourceRoot(module, testSourceFile, true);
    ModuleRootModificationUtil.setModuleSdk(module, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.addExcludedRoot(module, excludeFile);
    PsiTestUtil.setCompilerOutputPath(module, classesFile.getUrl(), false);
    PsiTestUtil.setCompilerOutputPath(module, testClassesFile.getUrl(), true);

    final Element element = new Element("root");
    moduleRootManager.getState().writeExternal(element);
    assertElementEquals(element,
                        "<root>" +
                        "<output url=\"file://$MODULE_DIR$/classes\" />" +
                        "<output-test url=\"file://$MODULE_DIR$/testClasses\" />" +
                        "<exclude-output />" +
                        "<content url=\"file://$MODULE_DIR$\">" +
                        "<sourceFolder url=\"file://$MODULE_DIR$/source\" isTestSource=\"false\" />" +
                        "<sourceFolder url=\"file://$MODULE_DIR$/testSource\" isTestSource=\"true\" />" +
                        "<excludeFolder url=\"file://$MODULE_DIR$/exclude\" />" +
                        "</content>" +
                        "<orderEntry type=\"jdk\" jdkName=\"java 1.7\" jdkType=\"JavaSDK\" />" +
                        "<orderEntry type=\"sourceFolder\" forTests=\"false\" />" +
                        "</root>",
                        module);
  }

  public void testModuleLibraries() throws IOException {
    File moduleFile = new File(getTestRoot(), "test.iml");
    Module module = createModule(moduleFile);
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    final LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();

    final Library unnamedLibrary = moduleLibraryTable.createLibrary();
    final File unnamedLibClasses = new File(getTestRoot(), "unnamedLibClasses");
    final VirtualFile unnamedLibClassesRoot = LocalFileSystem.getInstance().findFileByIoFile(unnamedLibClasses);
    final Library.ModifiableModel libraryModifiableModel = unnamedLibrary.getModifiableModel();
    libraryModifiableModel.addRoot(unnamedLibClassesRoot.getUrl(), OrderRootType.CLASSES);

    final Library namedLibrary = moduleLibraryTable.createLibrary("namedLibrary");
    final File namedLibClasses = new File(getTestRoot(), "namedLibClasses");
    final VirtualFile namedLibClassesRoot = LocalFileSystem.getInstance().findFileByIoFile(namedLibClasses);
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
    final Element element = new Element("root");
    moduleRootManager.getState().writeExternal(element);
    assertElementEquals(element,
                        "<root inherit-compiler-output=\"true\">" +
                        "<exclude-output />" +
                        "<orderEntry type=\"sourceFolder\" forTests=\"false\" />" +
                        "<orderEntry type=\"module-library\">" +
                        "<library>" +
                        "<CLASSES><root url=\"file://$MODULE_DIR$/unnamedLibClasses\" /></CLASSES>" +
                        "<JAVADOC />" +
                        "<SOURCES />" +
                        "</library>" +
                        "</orderEntry>" +
                        "<orderEntry type=\"module-library\">" +
                        "<library name=\"namedLibrary\">" +
                        "<CLASSES><root url=\"file://$MODULE_DIR$/namedLibClasses\" /></CLASSES>" +
                        "<JAVADOC />" +
                        "<SOURCES />" +
                        "</library>" +
                        "</orderEntry>" +
                        "</root>", module);
  }

  public void testCompilerOutputInheritance() throws IOException {
    File moduleFile = new File(getTestRoot(), "test.iml");
    Module module = createModule(moduleFile);
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    rootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);
    ApplicationManager.getApplication().runWriteAction(rootModel::commit);
    Element element = new Element("root");
    moduleRootManager.getState().writeExternal(element);
    assertElementEquals(element,
                        "<root inherit-compiler-output=\"true\">" +
                        "<exclude-output />" +
                        "<orderEntry type=\"sourceFolder\" forTests=\"false\" />" +
                        "</root>", module);
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

  public static void assertElementEquals(@NotNull Element element, @NotNull String value, @NotNull Module module) throws IOException {
    try {
      assertThat(JbXmlOutputter.collapseMacrosAndWrite(element, module)).isEqualTo(JDOMUtil.writeElement(JDOMUtil.load(value)));
    }
    catch (JDOMException e) {
      throw new IOException(e);
    }
  }

  private File getTestRoot() {
    File testRoot = new File(PathManagerEx.getTestDataPath());
    File moduleRootManagerRoot = new File(testRoot, "moduleRootManager");
    return new File(moduleRootManagerRoot, getTestName(true));
  }
}
