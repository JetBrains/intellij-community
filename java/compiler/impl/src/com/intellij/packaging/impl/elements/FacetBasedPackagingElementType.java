// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class FacetBasedPackagingElementType<E extends PackagingElement<?>, F extends Facet> extends PackagingElementType<E> {
  private final FacetTypeId<F> myFacetType;

  /**
   * @deprecated This constructor is meant to provide the binary compatibility with the external plugins.
   * Please use the constructor that accepts a messagePointer for {@link PackagingElementType#myPresentableName}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected FacetBasedPackagingElementType(@NotNull @NonNls String id,
                                           @NotNull @Nls(capitalization = Nls.Capitalization.Title) String presentableName,
                                           FacetTypeId<F> facetType) {
    this(id, () -> presentableName, facetType);
  }

  protected FacetBasedPackagingElementType(@NotNull @NonNls String id,
                                           @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName,
                                           FacetTypeId<F> facetType) {
    super(id, presentableName);
    myFacetType = facetType;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return !getFacets(context).isEmpty();
  }

  @Override
  public Icon getCreateElementIcon() {
    return FacetTypeRegistry.getInstance().findFacetType(myFacetType).getIcon();
  }

  @NotNull
  @Override
  public List<? extends E> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact, @NotNull CompositePackagingElement<?> parent) {
    final List<F> facets = getFacets(context);
    ChooseFacetsDialog dialog = new ChooseFacetsDialog(context.getProject(), facets, getDialogTitle(), getDialogDescription());
    if (dialog.showAndGet()) {
      final List<E> elements = new ArrayList<>();
      for (F facet : dialog.getChosenElements()) {
        elements.add(createElement(context.getProject(), facet));
      }
      return elements;
    }
    return Collections.emptyList();
  }

  private List<F> getFacets(ArtifactEditorContext context) {
    final Module[] modules = context.getModulesProvider().getModules();
    final List<F> facets = new ArrayList<>();
    for (Module module : modules) {
      facets.addAll(context.getFacetsProvider().getFacetsByType(module, myFacetType));
    }
    return facets;
  }

  protected abstract E createElement(Project project, F facet);

  protected abstract @NlsContexts.DialogTitle String getDialogTitle();

  protected abstract @NlsContexts.Label String getDialogDescription();

  protected abstract @NlsSafe String getItemText(F item);

  private final class ChooseFacetsDialog extends ChooseElementsDialog<F> {
    private ChooseFacetsDialog(Project project, List<? extends F> items, @NlsContexts.DialogTitle String title, @NlsContexts.Label String description) {
      super(project, items, title, description, true);
    }

    @Override
    protected String getItemText(F item) {
      return FacetBasedPackagingElementType.this.getItemText(item);
    }

    @Override
    protected Icon getItemIcon(F item) {
      return FacetTypeRegistry.getInstance().findFacetType(myFacetType).getIcon();
    }
  }
}
