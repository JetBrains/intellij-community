package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ModuleElementPresentation extends TreeNodePresentation {
  private final String myName;
  private final ArtifactEditorContext myContext;
  private final Module myModule;

  public ModuleElementPresentation(@NotNull String name, @Nullable Module module, ArtifactEditorContext context) {
    myModule = module;
    myName = name;
    myContext = context;
  }

  public String getPresentableName() {
    return myName;
  }

  @Override
  public boolean canNavigateToSource() {
    return myModule != null;
  }

  @Override
  public Object getSourceObject() {
    return myModule;
  }

  @Override
  public void navigateToSource() {
    myContext.selectModule(myModule);
  }

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    if (myModule != null) {
      presentationData.setOpenIcon(myModule.getModuleType().getNodeIcon(true));
      presentationData.setClosedIcon(myModule.getModuleType().getNodeIcon(false));
    }
    presentationData.addText(getNodeText(),
                             myModule != null ? mainAttributes : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  protected String getNodeText() {
    return CompilerBundle.message("node.text.0.compile.output", myName);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.MODULE;
  }
}
