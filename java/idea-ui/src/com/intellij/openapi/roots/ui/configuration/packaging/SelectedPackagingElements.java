package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
*/
class SelectedPackagingElements {
  private final List<ContainerElement> myContainerElements = new ArrayList<ContainerElement>();
  private final Set<PackagingArtifact> myOwners = new HashSet<PackagingArtifact>();
  private final Collection<LibraryLink> myParentLibraryLinks;

  public SelectedPackagingElements(final PackagingTreeNode[] treeNodes) {
    BidirectionalMap<VirtualFile, LibraryLink> selectedFiles = new BidirectionalMap<VirtualFile, LibraryLink>();
    Set<LibraryLink> fullySelectedLibraries = new HashSet<LibraryLink>();
    for (PackagingTreeNode treeNode : treeNodes) {
      ContainerElement containerElement = treeNode.getContainerElement();
      if (containerElement == null) continue;

      if (treeNode instanceof LibraryFileNode) {
        LibraryFileNode node = (LibraryFileNode)treeNode;
        selectedFiles.put(node.getFile(), node.getLibraryLink());
      }
      else if (containerElement instanceof LibraryLink) {
        fullySelectedLibraries.add((LibraryLink)containerElement);
      }
      PackagingArtifact owner = treeNode.getOwner();
      if (owner != null && owner.getContainerElement() != null) {
        myOwners.add(owner);
      }
      else {
        myContainerElements.add(containerElement);
      }
    }

    for (LibraryLink libraryLink : selectedFiles.values()) {
      Library library = libraryLink.getLibrary();
      if (library != null) {
        VirtualFile[] roots = library.getFiles(OrderRootType.CLASSES);
        List<VirtualFile> files = selectedFiles.getKeysByValue(libraryLink);
        if (Comparing.haveEqualElements(files, Arrays.asList(roots))) {
          fullySelectedLibraries.add(libraryLink);
        }
      }
    }

    for (LibraryLink libraryLink : fullySelectedLibraries) {
      selectedFiles.removeValue(libraryLink);
    }
    myParentLibraryLinks = selectedFiles.values();
    ContainerUtil.removeDuplicates(myContainerElements);
  }

  public Collection<LibraryLink> getParentLibraryLinks() {
    return myParentLibraryLinks;
  }

  public List<ContainerElement> getContainerElements() {
    return myContainerElements;
  }

  public Set<PackagingArtifact> getOwners() {
    return myOwners;
  }

  public boolean showRemovingWarning(final PackagingEditorImpl editor) {
    Set<PackagingArtifact> owners = getOwners();
    Collection<LibraryLink> parentLibraryLinks = getParentLibraryLinks();
    if (!parentLibraryLinks.isEmpty()) {
      StringBuilder librariesNames = new StringBuilder();
      for (LibraryLink link : parentLibraryLinks) {
        if (librariesNames.length() > 0) librariesNames.append(", ");
        librariesNames.append('\'').append(link.getPresentableName()).append('\'');
      }
      String message = ProjectBundle.message("message.text.individial.files.cannot.be.removed.from.packaging.do.you.want.to.remove.the.whole.libraries",
                 librariesNames.toString(), parentLibraryLinks.size());
      int answer = Messages.showYesNoDialog(editor.getMainPanel(), message, ProjectBundle.message("dialog.title.packaging.remove.included"), null);
      if (answer != 0) {
        return false;
      }
    }

    if (!owners.isEmpty()) {
      String message;
      if (owners.size() == 1 && getContainerElements().isEmpty()) {
        PackagingArtifact artifact = owners.iterator().next();
        message = ProjectBundle.message("message.text.packaging.selected.item.belongs.to.0.do.you.want.to.exlude.1.from.2",
                                        artifact.getDisplayName(), artifact.getDisplayName(), editor.getRootArtifact().getDisplayName());
      }
      else {
        StringBuilder ownersBuffer = new StringBuilder();
        for (PackagingArtifact owner : owners) {
          if (ownersBuffer.length() > 0) {
            ownersBuffer.append(", ");
          }
          ownersBuffer.append(owner.getDisplayName());
        }
        message = ProjectBundle.message("message.text.packaging.do.you.want.to.exlude.0.from.1", ownersBuffer, editor.getRootArtifact().getDisplayName());
      }
      int answer = Messages.showYesNoDialog(editor.getMainPanel(), message, ProjectBundle.message("dialog.title.packaging.remove.included"), null);
      if (answer != 0) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public PackagingElementsToEditInfo getElementsToEdit(final PackagingEditorPolicy policy) {
    Set<ContainerElement> elements = new HashSet<ContainerElement>(myContainerElements);
    for (PackagingArtifact owner : myOwners) {
      ContainerElement element = owner.getContainerElement();
      if (element != null) {
        elements.add(element);
      }
    }
    if (elements.isEmpty()) {
      return null;
    }
    if (elements.size() == 1) {
      return new PackagingElementsToEditInfo(elements.iterator().next(), policy);
    }

    return new PackagingElementsToEditInfo(elements, policy);
  }

}
