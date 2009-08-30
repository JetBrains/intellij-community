package com.intellij.packaging.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.DelegatedSourceItemPresentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.ModuleSourceItemGroup;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.elements.PackagingElementOutputKind;
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
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull PackagingEditorContext editorContext, @NotNull Artifact artifact,
                                                                  @Nullable PackagingSourceItem parent) {
    if (parent instanceof ModuleSourceItemGroup) {
      final Module module = ((ModuleSourceItemGroup)parent).getModule();
      final Set<F> facets = new HashSet<F>(editorContext.getFacetsProvider().getFacetsByType(module, myFacetTypeId));
      ArtifactUtil.processPackagingElements(artifact, myElementType, new Processor<E>() {
        public boolean process(E e) {
          F facet = getFacet(e);
          if (facet != null) {
            facets.remove(facet);
          }
          return true;
        }
      }, editorContext, true);

      if (!facets.isEmpty()) {
        return Collections.singletonList(new FacetBasedSourceItem<F>(this, facets.iterator().next()));
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

  protected abstract PackagingElement<?> createElement(PackagingEditorContext context, F facet);

  private static class FacetBasedSourceItem<F extends Facet> extends PackagingSourceItem {
    private final FacetBasedPackagingSourceItemsProvider<F, ?> myProvider;
    private final F myFacet;

    public FacetBasedSourceItem(FacetBasedPackagingSourceItemsProvider<F, ?> provider, F facet) {
      myProvider = provider;
      myFacet = facet;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FacetBasedSourceItem && myFacet.equals(((FacetBasedSourceItem)obj).myFacet);
    }

    @Override
    public int hashCode() {
      return myFacet.hashCode();
    }

    @Override
    public SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context) {
      return new DelegatedSourceItemPresentation(myProvider.createPresentation(myFacet));
    }

    @NotNull
    @Override
    public List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context) {
      return Collections.singletonList(myProvider.createElement(context, myFacet));
    }

    @NotNull
    @Override
    public PackagingElementOutputKind getKindOfProducedElements() {
      return myProvider.getKindOfProducedElements();
    }
  }

}
