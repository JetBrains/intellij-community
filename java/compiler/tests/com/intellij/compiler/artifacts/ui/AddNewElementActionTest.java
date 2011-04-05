package com.intellij.compiler.artifacts.ui;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;

/**
 * @author nik
 */
public class AddNewElementActionTest extends ArtifactEditorTestCase {

  public void testSimple() throws Exception {
    addModule("mod", null);
    createEditor(addArtifact(root()));
    myArtifactEditor.addNewPackagingElement(ProductionModuleOutputElementType.ELEMENT_TYPE);
    assertLayout("<root>\n" +
                 " module:mod");
  }

  public void testAddToDirectory() throws Exception {
    addModule("mod", null);
    createEditor(addArtifact(root().dir("dir")));
    selectNode("dir");
    myArtifactEditor.addNewPackagingElement(ProductionModuleOutputElementType.ELEMENT_TYPE);
    assertLayout("<root>\n" +
                 " dir/\n" +
                 "  module:mod");
  }
  
  public void testAddToDirectoryInIncludedArtifact() throws Exception {
    addModule("mod", null);
    Artifact included = addArtifact("included", root().dir("dir"));
    createEditor(addArtifact(root().artifact(included)), true);
    selectNode("dir");
    myArtifactEditor.addNewPackagingElement(ProductionModuleOutputElementType.ELEMENT_TYPE);
    assertLayout("<root>\n" +
                 " artifact:included\n" +
                 " dir/\n" +
                 "  module:mod");
  }

  public void testDoNotAddIntoIncludedArchive() throws Exception {
    addModule("mod", null);
    final Artifact included = addArtifact("included", root().archive("x.jar"));
    createEditor(addArtifact(root().artifact(included)), true);
    selectNode("x.jar");
    addNewElement(ProductionModuleOutputElementType.ELEMENT_TYPE, true);
    assertLayout("<root>\n" +
                 " artifact:included");
  }

  private void addNewElement(final ProductionModuleOutputElementType elementType, final boolean confirmationExpected) {
    runAction(new Runnable() {
      @Override
      public void run() {
        myArtifactEditor.addNewPackagingElement(elementType);
      }
    }, confirmationExpected);
  }
}
