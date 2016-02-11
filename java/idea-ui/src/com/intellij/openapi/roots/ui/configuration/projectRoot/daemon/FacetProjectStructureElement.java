/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.facet.Facet;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
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
  public String getPresentableText() {
    return "Facet '" + myFacet.getName() + "' in module '" + myFacet.getModule().getName() + "'";
  }

  @Override
  public String getPresentableName() {
    return myFacet.getName();
  }

  @Override
  public String getTypeName() {
    return "Facet";
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
