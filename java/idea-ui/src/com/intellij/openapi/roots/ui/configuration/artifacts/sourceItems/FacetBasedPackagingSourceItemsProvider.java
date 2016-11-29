/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class FacetBasedPackagingSourceItemsProvider<F extends Facet, E extends PackagingElement<?>> extends PackagingSourceItemsProvider {
  private final FacetTypeId<F> myFacetTypeId;
  private final PackagingElementType<E> myElementType;

  protected FacetBasedPackagingSourceItemsProvider(FacetTypeId<F> facetTypeId, PackagingElementType<E> elementType) {
    myFacetTypeId = facetTypeId;
    myElementType = elementType;
  }

  @NotNull
  @Override
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext, @NotNull Artifact artifact,
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

  @Nullable
  protected abstract F getFacet(E element);

  protected abstract TreeNodePresentation createPresentation(F facet);

  protected abstract PackagingElement<?> createElement(ArtifactEditorContext context, F facet);

  protected static class FacetBasedSourceItem<F extends Facet> extends PackagingSourceItem {
    private final FacetBasedPackagingSourceItemsProvider<F, ?> myProvider;
    private final F myFacet;

    public FacetBasedSourceItem(FacetBasedPackagingSourceItemsProvider<F, ?> provider, F facet) {
      myProvider = provider;
      myFacet = facet;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FacetBasedSourceItem && myFacet.equals(((FacetBasedSourceItem)obj).myFacet)
             && myProvider.equals(((FacetBasedSourceItem)obj).myProvider);
    }

    @Override
    public int hashCode() {
      return myFacet.hashCode() + 31*myProvider.hashCode();
    }

    @Override
    public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
      return new DelegatedSourceItemPresentation(myProvider.createPresentation(myFacet));
    }

    @NotNull
    @Override
    public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
      return Collections.singletonList(myProvider.createElement(context, myFacet));
    }

    @NotNull
    @Override
    public PackagingElementOutputKind getKindOfProducedElements() {
      return myProvider.getKindOfProducedElements();
    }
  }

}
