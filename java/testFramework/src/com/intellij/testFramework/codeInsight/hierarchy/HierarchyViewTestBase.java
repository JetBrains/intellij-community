package com.intellij.testFramework.codeInsight.hierarchy;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.util.*;

/**
 * Checks tree structure for Type Hierarchy (Ctrl+H), Call Hierarchy (Ctrl+Alt+H), Method Hierarchy (Ctrl+Shift+H).
 */
public abstract class HierarchyViewTestBase extends CodeInsightTestCase {

  protected abstract String getBasePath();

  protected void doHierarchyTest(final Computable<HierarchyTreeStructure> treeStructureComputable, final String... fileNames)
    throws Exception {
    final String[] relFilePaths = new String[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      relFilePaths[i] = "/" + getBasePath() + "/" + fileNames[i];
    }
    configureByFiles(null, relFilePaths);

    final String verificationFilePath = getTestDataPath() + "/" + getBasePath() + "/" + getTestName(false) + "_verification.xml";
    checkHierarchyTreeStructure(treeStructureComputable.compute(), JDOMUtil.loadDocument(new File(verificationFilePath)));
  }

  private static void checkHierarchyTreeStructure(final HierarchyTreeStructure treeStructure, final Document document) {
    checkNodeDescriptorRecursively(treeStructure, (HierarchyNodeDescriptor)treeStructure.getRootElement(), document.getRootElement());
  }

  private static void checkNodeDescriptorRecursively(final HierarchyTreeStructure treeStructure,
                                                     final HierarchyNodeDescriptor descriptor,
                                                     final Element expectedElement) {
    descriptor.update();
    checkBaseNode(treeStructure, descriptor, expectedElement);
    checkContent(descriptor, expectedElement);
    checkChildren(treeStructure, descriptor, expectedElement);
  }

  private static void checkBaseNode(final HierarchyTreeStructure treeStructure,
                                    final HierarchyNodeDescriptor descriptor,
                                    final Element expectedElement) {
    final String baseAttrValue = expectedElement.getAttributeValue("base");
    final HierarchyNodeDescriptor baseDescriptor = treeStructure.getBaseDescriptor();
    final boolean mustBeBase = "true".equalsIgnoreCase(baseAttrValue);
    assertTrue("Incorrect base node", mustBeBase ? baseDescriptor == descriptor : baseDescriptor != descriptor);
  }

  private static void checkContent(final HierarchyNodeDescriptor descriptor, final Element expectedElement) {
    assertEquals(expectedElement.getAttributeValue("text"), descriptor.getHighlightedText().getText());
  }

  private static void checkChildren(final HierarchyTreeStructure treeStructure,
                                    final HierarchyNodeDescriptor descriptor,
                                    final Element element) {
    final Object[] children = treeStructure.getChildElements(descriptor);
    //noinspection unchecked
    final List<Element> expectedChildren = new ArrayList<Element>(element.getChildren());
    assertEquals("Children of " + descriptor.getHighlightedText().getText(), expectedChildren.size(), children.length);

    Arrays.sort(children, new Comparator<Object>() {
      public int compare(final Object first, final Object second) {
        return ((HierarchyNodeDescriptor)first).getHighlightedText().getText()
          .compareTo(((HierarchyNodeDescriptor)second).getHighlightedText().getText());
      }
    });

    Collections.sort(expectedChildren, new Comparator<Element>() {
      public int compare(final Element first, final Element second) {
        return first.getAttributeValue("text").compareTo(second.getAttributeValue("text"));
      }
    });

    //noinspection unchecked
    final Iterator<Element> iterator = expectedChildren.iterator();
    for (Object child : children) {
      checkNodeDescriptorRecursively(treeStructure, ((HierarchyNodeDescriptor)child), iterator.next());
    }
  }

}


