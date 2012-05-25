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
package com.intellij.project.convertors;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.MultiFileTestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;
import java.util.List;

/**
 *  @author dsl
 */
public class Convertor34Test extends MultiFileTestCase {
  @Override
  public void setUp() throws Exception {
    myDoCompare = false;
    super.setUp();
  }

  @Override
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


  private static PerformAction createPerformAction(final String projectName) {
    return new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final VirtualFile projectFile = rootDir.findChild(projectName + ProjectFileType.DOT_DEFAULT_EXTENSION);
        assertNotNull(rootDir.getPath(), projectFile);

        final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
        macros.addMacroExpand(PathMacrosImpl.PROJECT_DIR_MACRO_NAME, rootDir.getPath());
        macros.addMacroExpand(PathMacrosImpl.MODULE_DIR_MACRO_NAME, rootDir.getPath());

        final Document projectDocument = JDOMUtil.loadDocument(VfsUtilCore.loadText(projectFile));
        macros.substitute(projectDocument.getRootElement(), true);

        Convertor34.execute(projectDocument.getRootElement(), projectFile.getPath(), null);

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            LocalFileSystem.getInstance().refresh(false);
          }
        });
        final Document moduleDocument = loadDocument(rootDir, projectName + ModuleFileType.DOT_DEFAULT_EXTENSION);
        macros.substitute(moduleDocument.getRootElement(), true);
        final Document goldenProjectDocument = loadDocument(rootAfter, projectName + ProjectFileType.DOT_DEFAULT_EXTENSION);
        macros.substitute(goldenProjectDocument.getRootElement(), true);
        final Document goldenModuleDocument = loadDocument(rootAfter, projectName + ModuleFileType.DOT_DEFAULT_EXTENSION);
        macros.substitute(goldenModuleDocument.getRootElement(), true);

        assertConfigsEqual("ProjectModuleManager", goldenProjectDocument, projectDocument);
        assertConfigsEqual("NewModuleRootManager", goldenModuleDocument, moduleDocument);
      }
    };
  }

  private static PerformAction createPerformActionForLibraryTable() {
    return new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final VirtualFile libFile = rootDir.findChild("library.table.xml");
        assertNotNull(rootDir.getPath(), libFile);

        final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
        macros.addMacroExpand("DIR", rootDir.getPath());

        final Document oldTable = JDOMUtil.loadDocument(VfsUtilCore.loadText(libFile));
        macros.substitute(oldTable.getRootElement(), true);
        Convertor34.convertLibraryTable34(oldTable.getRootElement(), libFile.getPath());
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            LocalFileSystem.getInstance().refresh(false);
          }
        });
        final Document newTable = loadDocument(rootDir, "applicationLibraries.xml");
        final Document goldenNewTable = loadDocument(rootAfter, "applicationLibraries.xml");
        macros.substitute(goldenNewTable.getRootElement(), true);
        assertElementsEqual(goldenNewTable.getRootElement(), newTable.getRootElement());
      }
    };
  }

  private static Document loadDocument(VirtualFile parent, String name) throws IOException, JDOMException {
    final VirtualFile child = parent.findChild(name);
    assertNotNull(parent + "/" + name, child);
    return JDOMUtil.loadDocument(VfsUtilCore.loadText(child));
  }

  private static void assertConfigsEqual(String componentName, Document goldenDocument, Document document) {
    final Element goldenElement = getComponentElement(goldenDocument, componentName);
    final Element element = getComponentElement(document, componentName);
    assertElementsEqual(goldenElement, element);
  }

  private static void assertElementsEqual(final Element goldenElement, final Element element) {
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
