// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.facet.Facet;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class FacetProjectStructureElement extends ProjectStructureElement {
  private final Facet myFacet;

  public FacetProjectStructureElement(@NotNull StructureConfigurableContext context, @NotNull Facet facet) {
    super(context);
    myFacet = facet;
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableText() {
    return JavaUiBundle.message("facet.project.structure.display.text", myFacet.getName(), myFacet.getModule().getName());
  }

  @Override
  public String getPresentableName() {
    return myFacet.getName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getTypeName() {
    return JavaUiBundle.message("facet.title");
  }

  @Override
  public String getId() {
    return "facet:" + FacetPointersManager.constructId(myFacet);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FacetProjectStructureElement && myFacet.equals(((FacetProjectStructureElement)obj).myFacet);
  }

  @Override
  public int hashCode() {
    return myFacet.hashCode();
  }
}
