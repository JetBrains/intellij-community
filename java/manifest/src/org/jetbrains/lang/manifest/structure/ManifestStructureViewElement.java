package org.jetbrains.lang.manifest.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.ManifestFile;
import org.jetbrains.lang.manifest.psi.Section;
import org.jetbrains.lang.manifest.psi.impl.HeaderImpl;

import java.util.List;

public class ManifestStructureViewElement implements StructureViewTreeElement, SortableTreeElement {

  private final NavigatablePsiElement element;

  public ManifestStructureViewElement(NavigatablePsiElement element) {
    this.element = element;
  }

  @Override
  public Object getValue() {
    return element;
  }

  @Override
  public void navigate(boolean requestFocus) {
    element.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return element.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return element.canNavigateToSource();
  }

  @NotNull
  @Override
  public String getAlphaSortKey() {
    String name = element.getName();
    return name != null ? name : "";
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    ItemPresentation presentation = element.getPresentation();
    return presentation != null ? presentation : new PresentationData();
  }

  @Override
  public TreeElement @NotNull [] getChildren() {
    if (element instanceof ManifestFile) {
      List<Section> sections = PsiTreeUtil.getChildrenOfTypeAsList(element, Section.class);
      List<Header> headers =
        sections.stream().map(section -> PsiTreeUtil.getChildrenOfTypeAsList(section, Header.class)).flatMap(list -> list.stream())
          .toList();
      List<ManifestStructureViewElement> treeElements =
        ContainerUtil.map(headers, header -> new ManifestStructureViewElement((HeaderImpl)header));
      return treeElements.toArray(TreeElement.EMPTY_ARRAY);
    }
    return EMPTY_ARRAY;
  }
}
