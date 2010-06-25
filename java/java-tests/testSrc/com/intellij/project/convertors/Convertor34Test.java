package com.intellij.project.convertors;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.ModuleFileType;
import org.jdom.Document;
import org.jdom.Element;

import java.util.List;

/**
 *  @author dsl
 */
public class Convertor34Test extends MultiFileTestCase {
  public Convertor34Test() {
    myDoCompare = false;
  }

  protected String getTestRoot() {
    return "/moduleRootManager/convertor34/";
  }

  public void testTest1() throws Exception { doTest(createPerformAction("prj")); }

  public void testTest2() throws Exception { doTest(createPerformAction("prj2")); }

  public void testTest3() throws Exception { doTest(createPerformAction("prj3")); }

  public void testTest4() throws Exception { doTest(createPerformAction("prj4")); }

  public void testScr24517() throws Exception { doTest(createPerformAction("se.mdh.html")); }

  public void testLibraryTable() throws Exception { doTest(createPerformActionForLibraryTable());}

  public void testScr25298() throws Exception { doTest(createPerformActionForLibraryTable());}


  private PerformAction createPerformAction(final String projectName) {
    return new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final VirtualFile projectFile = rootDir.findChild(projectName + ProjectFileType.DOT_DEFAULT_EXTENSION);
        final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
        macros.addMacroExpand(PathMacrosImpl.PROJECT_DIR_MACRO_NAME, rootDir.getPath());
        macros.addMacroExpand(PathMacrosImpl.MODULE_DIR_MACRO_NAME, rootDir.getPath());

        final Document projectDocument = JDOMUtil.loadDocument(
          VfsUtil.loadText(projectFile));
        macros.substitute(projectDocument.getRootElement(), true);

        Convertor34.execute(projectDocument.getRootElement(), projectFile.getPath(), null);

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            LocalFileSystem.getInstance().refresh(false);
          }
        });
        final VirtualFile moduleFile = rootDir.findChild(projectName + ModuleFileType.DOT_DEFAULT_EXTENSION);
        assertNotNull(moduleFile);
        final Document moduleDocument = JDOMUtil.loadDocument(
          VfsUtil.loadText(moduleFile));
        macros.substitute(moduleDocument.getRootElement(), true);
        final Document goldenProjectDocument = JDOMUtil.loadDocument(
          VfsUtil.loadText(rootAfter.findChild(projectName + ProjectFileType.DOT_DEFAULT_EXTENSION))
        );
        macros.substitute(goldenProjectDocument.getRootElement(), true);
        final Document goldenModuleDocument = JDOMUtil.loadDocument(
          VfsUtil.loadText(rootAfter.findChild(projectName + ModuleFileType.DOT_DEFAULT_EXTENSION))
        );
        macros.substitute(goldenModuleDocument.getRootElement(), true);

        assertConfigsEqual("ProjectModuleManager", goldenProjectDocument, projectDocument);
        assertConfigsEqual("NewModuleRootManager", goldenModuleDocument, moduleDocument);
      }
    };
  }

  private PerformAction createPerformActionForLibraryTable() {
    return new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final VirtualFile libFile = rootDir.findChild("library.table.xml");
        final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
        macros.addMacroExpand("DIR", rootDir.getPath());

        final Document oldTable = JDOMUtil.loadDocument(
          VfsUtil.loadText(libFile));
        macros.substitute(oldTable.getRootElement(), true);
        Convertor34.convertLibraryTable34(oldTable.getRootElement(), libFile.getPath());
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            LocalFileSystem.getInstance().refresh(false);
          }
        });
        final VirtualFile applicationLibraries = rootDir.findChild("applicationLibraries.xml");
        assertNotNull(applicationLibraries);
        final Document newTable = JDOMUtil.loadDocument(
          VfsUtil.loadText(applicationLibraries));
        final Document goldenNewTable = JDOMUtil.loadDocument(
          VfsUtil.loadText(rootAfter.findChild("applicationLibraries.xml")));
        macros.substitute(goldenNewTable.getRootElement(), true);
        assertElementsEqual(goldenNewTable.getRootElement(), newTable.getRootElement());
      }
    };
  }

  private void assertConfigsEqual(String componentName, Document goldenDocument, Document document) {
    final Element goldenElement = getComponentElement(goldenDocument, componentName);
    final Element element = getComponentElement(document, componentName);
    assertElementsEqual(goldenElement, element);
  }

  private void assertElementsEqual(final Element goldenElement, final Element element) {
    assertEquals(JDOMUtil.createOutputter("\n").outputString(goldenElement),
                 JDOMUtil.createOutputter("\n").outputString(element));
  }

  private static Element getComponentElement(Document root, String componentName) {
    final List children = root.getRootElement().getChildren("component");
    for (Object aChildren : children) {
      Element element = (Element)aChildren;
      if (componentName.equals(element.getAttributeValue("name"))) return element;
    }
    assertTrue(componentName + " not found", false);
    return null;
  }
}
