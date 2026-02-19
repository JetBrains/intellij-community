package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nls;

import java.util.Collections;
import java.util.List;

public class SdkProjectStructureElement extends ProjectStructureElement {
  private final Sdk mySdk;

  public SdkProjectStructureElement(StructureConfigurableContext context, Sdk sdk) {
    super(context);
    mySdk = getModifiableSdk(sdk);
  }

  private Sdk getModifiableSdk(Sdk sdk) {
    Sdk modifiableSdk = myContext.getModulesConfigurator().getProjectStructureConfigurable().getProjectJdksModel().getProjectSdks().get(sdk);
    return modifiableSdk != null? modifiableSdk : sdk;
  }

  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SdkProjectStructureElement)) return false;
    return mySdk.equals(((SdkProjectStructureElement)o).mySdk);

  }

  @Override
  public int hashCode() {
    return mySdk.hashCode();
  }

  @Override
  public String getPresentableName() {
    return mySdk.getName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getTypeName() {
    return ProjectBundle.message("sdk");
  }

  @Override
  public String getId() {
    return "sdk:" + mySdk.getName();
  }
}
