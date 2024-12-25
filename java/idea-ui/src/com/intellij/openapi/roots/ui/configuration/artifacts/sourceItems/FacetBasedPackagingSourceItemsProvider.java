// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class FacetBasedPackagingSourceItemsProvider<F extends Facet, E extends PackagingElement<?>> extends PackagingSourceItemsProvider {
  private final FacetTypeId<F> myFacetTypeId;
  private final PackagingElementType<E> myElementType;

  protected FacetBasedPackagingSourceItemsProvider(FacetTypeId<F> facetTypeId, PackagingElementType<E> elementType) {
    myFacetTypeId = facetTypeId;
    myElementType = elementType;
  }

  @Override
  public @NotNull Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext, @NotNull Artifact artifact,
                                                                           @Nullable PackagingSourceItem parent) {
    if (parent instanceof ModuleSourceItemGroup) {
      final Module module = ((ModuleSourceItemGroup)parent).getModule();
      final Set<F> facets = new HashSet<>(editorContext.getFacetsProvider().getFacetsByType(module, myFacetTypeId));
      ArtifactUtil.processPackagingElements(artifact, myElementType, e -> {
        F facet = getFacet(e);
        if (facet != null) {
          facets.remove(facet);
        }
        return true;
      }, editorContext, true);

      if (!facets.isEmpty()) {
        return Collections.singletonList(new FacetBasedSourceItem<>(this, facets.iterator().next()));
      }
    }
    return Collections.emptyList();
  }

  protected PackagingElementOutputKind getKindOfProducedElements() {
    return PackagingElementOutputKind.OTHER;
  }

  protected abstract @Nullable F getFacet(E element);

  protected abstract TreeNodePresentation createPresentation(F facet);

  protected abstract PackagingElement<?> createElement(ArtifactEditorContext context, F facet);

  protected static class FacetBasedSourceItem<F extends Facet> extends PackagingSourceItem {
    private final FacetBasedPackagingSourceItemsProvider<? super F, ?> myProvider;
    private final F myFacet;

    public FacetBasedSourceItem(FacetBasedPackagingSourceItemsProvider<? super F, ?> provider, F facet) {
      myProvider = provider;
      myFacet = facet;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FacetBasedSourceItem && myFacet.equals(((FacetBasedSourceItem<?>)obj).myFacet)
             && myProvider.equals(((FacetBasedSourceItem)obj).myProvider);
    }

    @Override
    public int hashCode() {
      return myFacet.hashCode() + 31*myProvider.hashCode();
    }

    @Override
    public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
      return new DelegatedSourceItemPresentation(myProvider.createPresentation(myFacet));
    }

    @Override
    public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
      return Collections.singletonList(myProvider.createElement(context, myFacet));
    }

    @Override
    public @NotNull PackagingElementOutputKind getKindOfProducedElements() {
      return myProvider.getKindOfProducedElements();
    }
  }

}
