package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;

import java.util.*;

/**
 * @author max
 */
public abstract class FilteringInspectionTool extends InspectionTool {
  public abstract RefFilter getFilter();
  HashMap<String, Set<RefElement>> myPackageContents = new HashMap<String, Set<RefElement>>();
  Set<RefElement> myIgnoreElements = new HashSet<RefElement>();
  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    Set<String> packages = myPackageContents.keySet();
    for (Iterator<String> iterator = packages.iterator(); iterator.hasNext();) {
      String p = iterator.next();
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = myPackageContents.get(p);
      for(Iterator<RefElement> iterator1 = elements.iterator(); iterator1.hasNext(); ) {
        RefElement refElement = iterator1.next();
        addNodeToParent(refElement, pNode);
      }
      content.add(pNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  public void updateContent() {
    resetFilter();
    myPackageContents = new HashMap<String, Set<RefElement>>();
    getContext().getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefElement)) return;
        RefElement refElement = (RefElement) refEntity;
        if (!myIgnoreElements.contains(refElement) && refElement.isValid() && getFilter().accepts(refElement)) {
          String packageName = RefUtil.getInstance().getPackageName(refEntity);
          Set<RefElement> content = myPackageContents.get(packageName);
          if (content == null) {
            content = new HashSet<RefElement>();
            myPackageContents.put(packageName, content);
          }
          content.add((RefElement)refEntity);
        }
      }
    });
  }

  protected abstract void resetFilter();

  public boolean hasReportedProblems() {
    return myPackageContents.size() > 0;
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public void ignoreElement(RefEntity refEntity) {
    myIgnoreElements.add((RefElement)refEntity);
  }

  public void cleanup() {
    super.cleanup();
    myPackageContents.clear();
    myIgnoreElements.clear();
  }

  public boolean isGraphNeeded() {
    return true;
  }

  public boolean isElementIgnored(final RefElement element) {
    for (RefEntity entity : myIgnoreElements) {
      if (entity instanceof RefElement){
        final RefElement refElement = (RefElement)entity;
        if (Comparing.equal(refElement.getElement(), element.getElement())){
          return true;
        }
      }
    }
    return false;
  }
}
