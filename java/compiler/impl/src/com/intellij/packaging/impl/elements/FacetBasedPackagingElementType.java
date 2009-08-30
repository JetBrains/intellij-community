package com.intellij.packaging.impl.elements;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseElementsDialog;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.PackagingEditorContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class FacetBasedPackagingElementType<E extends PackagingElement<?>, F extends Facet> extends PackagingElementType<E> {
  private final FacetTypeId<F> myFacetType;

  protected FacetBasedPackagingElementType(@NotNull @NonNls String id, @NotNull String presentableName, FacetTypeId<F> facetType) {
    super(id, presentableName);
    myFacetType = facetType;
  }

  @Override
  public boolean canCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact) {
    return !getFacets(context).isEmpty();
  }

  @Override
  public Icon getCreateElementIcon() {
    return FacetTypeRegistry.getInstance().findFacetType(myFacetType).getIcon();
  }

  @NotNull
  @Override
  public List<? extends E> chooseAndCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact, @NotNull CompositePackagingElement<?> parent) {
    final Project project = context.getProject();
    final List<F> facets = getFacets(context);
    ChooseFacetsDialog dialog = new ChooseFacetsDialog(context.getProject(), facets, getDialogTitle(), getDialogDescription());
    dialog.show();
    if (dialog.isOK()) {
      final List<E> elements = new ArrayList<E>();
      for (F facet : dialog.getChosenElements()) {
        elements.add(createElement(project, facet));
      }
      return elements;
    }
    return Collections.emptyList();
  }

  private List<F> getFacets(PackagingEditorContext context) {
    final Module[] modules = context.getModulesProvider().getModules();
    final List<F> facets = new ArrayList<F>();
    for (Module module : modules) {
      facets.addAll(context.getFacetsProvider().getFacetsByType(module, myFacetType));
    }
    return facets;
  }

  protected abstract E createElement(Project project, F facet);

  protected abstract String getDialogTitle();

  protected abstract String getDialogDescription();

  protected abstract String getItemText(F item);

  @Nullable
  protected Icon getIcon(F item) {
    return FacetTypeRegistry.getInstance().findFacetType(myFacetType).getIcon();
  }

  private class ChooseFacetsDialog extends ChooseElementsDialog<F> {
    private ChooseFacetsDialog(Project project, List<? extends F> items, String title, String description) {
      super(project, items, title, description);
    }

    @Override
    protected String getItemText(F item) {
      return FacetBasedPackagingElementType.this.getItemText(item);
    }

    @Override
    protected Icon getItemIcon(F item) {
      return FacetBasedPackagingElementType.this.getIcon(item);
    }
  }
}
