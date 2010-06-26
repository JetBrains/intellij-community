package com.intellij.roots;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.ide.highlighter.ModuleFileType;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 *  @author dsl
 */
public class ModuleRootsExternalizationTest extends ModuleTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ExternalizationTest");

  public void testEmptyModuleWrite() throws Exception {
    try {
      ModuleRootManagerImpl moduleRootManager = createTempModuleRootManager();
      Element root = new Element("root");
      moduleRootManager.getState().writeExternal(root);
      assertEquals(root.getText(), "");
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private ModuleRootManagerImpl createTempModuleRootManager() throws IOException {
    File tmpModule = File.createTempFile("tst", ModuleFileType.DOT_DEFAULT_EXTENSION);
    myFilesToDelete.add(tmpModule);
    final Module module = createModule(tmpModule);
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    return moduleRootManager;
  }

  public void testContentWrite() throws Exception {
    File content = getTestRoot();
    File source = new File(content, "source");
    File testSource = new File(content, "testSource");
    File exclude = new File(content, "exclude");
    File classes = new File(content, "classes");
    File testClasses = new File(content, "testClasses");
    final VirtualFile contentFile = LocalFileSystem.getInstance().findFileByIoFile(content);
    assertTrue(contentFile != null);
    final VirtualFile sourceFile = LocalFileSystem.getInstance().findFileByIoFile(source);
    assertTrue(sourceFile != null);
    final VirtualFile testSourceFile = LocalFileSystem.getInstance().findFileByIoFile(testSource);
    assertTrue(testSourceFile != null);
    final VirtualFile excludeFile = LocalFileSystem.getInstance().findFileByIoFile(exclude);

    assertTrue(excludeFile != null);
    final VirtualFile classesFile = LocalFileSystem.getInstance().findFileByIoFile(classes);

    assertTrue(classesFile != null);
    final VirtualFile testClassesFile = LocalFileSystem.getInstance().findFileByIoFile(testClasses);

    assertTrue(testClassesFile != null);

    final File moduleFile = new File(content, "test.iml");
    final Module module = createModule(moduleFile);
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    final Runnable action = new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        final ContentEntry contentEntry = rootModel.addContentEntry(contentFile);
        final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
        compilerModuleExtension.setCompilerOutputPath(classesFile);
        compilerModuleExtension.setCompilerOutputPathForTests(testClassesFile);
        compilerModuleExtension.inheritCompilerOutputPath(false);
        rootModel.setSdk(JavaSdkImpl.getMockJdk("java 1.4"));
        contentEntry.addSourceFolder(sourceFile, false);
        contentEntry.addSourceFolder(testSourceFile, true);
        contentEntry.addExcludeFolder(excludeFile);
        rootModel.commit();
      }
    };

    ApplicationManager.getApplication().runWriteAction(action);


    final Element element = new Element("root");
    moduleRootManager.getState().writeExternal(element);
    assertElementEquals(element,
                        "<root inherit-compiler-output=\"false\">" +
                        "<output url=\"file://$MODULE_DIR$/classes\" />" +
                        "<output-test url=\"file://$MODULE_DIR$/testClasses\" />" +
                        "<exclude-output />" +
                        "<content url=\"file://$MODULE_DIR$\">" +
                        "<sourceFolder url=\"file://$MODULE_DIR$/source\" isTestSource=\"false\" />" +
                        "<sourceFolder url=\"file://$MODULE_DIR$/testSource\" isTestSource=\"true\" />" +
                        "<excludeFolder url=\"file://$MODULE_DIR$/exclude\" />" +
                        "</content>" +
                        "<orderEntry type=\"jdk\" jdkName=\"java 1.4\" jdkType=\"JavaSDK\" />" +
                        "<orderEntry type=\"sourceFolder\" forTests=\"false\" />" +
                        "</root>",
                        module);
  }

  public void testModuleLibraries() throws Exception {
    File moduleFile = new File(getTestRoot(), "test.iml");
    Module module = createModule(moduleFile);
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    final LibraryTable moduleLibraryTable = rootModel.getModuleLibraryTable();

    final Library unnamedLibrary = moduleLibraryTable.createLibrary();
    final File unnamedLibClasses = new File(getTestRoot(), "unnamedLibClasses");
    final VirtualFile unnamedLibClassesRoot = LocalFileSystem.getInstance().findFileByIoFile(unnamedLibClasses);
    final Library.ModifiableModel libraryModifyableModel = unnamedLibrary.getModifiableModel();
    libraryModifyableModel.addRoot(unnamedLibClassesRoot.getUrl(), OrderRootType.CLASSES);

    final Library namedLibrary = moduleLibraryTable.createLibrary("namedLibrary");
    final File namedLibClasses = new File(getTestRoot(), "namedLibClasses");
    final VirtualFile namedLibClassesRoot = LocalFileSystem.getInstance().findFileByIoFile(namedLibClasses);
    final Library.ModifiableModel namedLibraryModel = namedLibrary.getModifiableModel();
    namedLibraryModel.addRoot(namedLibClassesRoot.getUrl(), OrderRootType.CLASSES);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        libraryModifyableModel.commit();
        namedLibraryModel.commit();
      }
    });

    final Iterator libraryIterator = moduleLibraryTable.getLibraryIterator();
    assertTrue(libraryIterator.next().equals(unnamedLibrary));
    assertTrue(libraryIterator.next().equals(namedLibrary));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
    final Element element = new Element("root");
    moduleRootManager.getState().writeExternal(element);
    assertElementEquals(element,
                        "<root inherit-compiler-output=\"true\">" +
                        "<exclude-output />" +
                        "<orderEntry type=\"sourceFolder\" forTests=\"false\" />" +
                        "<orderEntry type=\"module-library\">" +
                        "<library>" +
                        "<CLASSES><root url=\"file://$MODULE_DIR$/unnamedLibClasses\" /></CLASSES>" +
                        "<JAVADOC /><SOURCES />" +
                        "</library>" +
                        "</orderEntry>" +
                        "<orderEntry type=\"module-library\">" +
                        "<library name=\"namedLibrary\">" +
                        "<CLASSES><root url=\"file://$MODULE_DIR$/namedLibClasses\" /></CLASSES>" +
                        "<JAVADOC /><SOURCES />" +
                        "</library>" +
                        "</orderEntry>" +
                        "</root>", module);
  }

  public void testCompilerOutputInheritance() throws Exception {
    File moduleFile = new File(getTestRoot(), "test.iml");
    Module module = createModule(moduleFile);
    final ModuleRootManagerImpl moduleRootManager =
      (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    rootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
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

  public static void assertElementEquals(final Element element, String value, Module module) {
    PathMacroManager.getInstance(module).collapsePaths(element);
    assertEquals(value, new XMLOutputter().outputString(element));
  }

  private File getTestRoot() {
    File testRoot = new File(PathManagerEx.getTestDataPath());
    File moduleRootManagerRoot = new File(testRoot, "moduleRootManager");
    File thisTestRoot = new File(moduleRootManagerRoot, getTestName(true));
    return thisTestRoot;
  }
}
