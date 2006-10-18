package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public abstract class FilteringInspectionTool extends InspectionTool {
  public abstract RefFilter getFilter();
  HashMap<String, Set<RefElement>> myPackageContents = new HashMap<String, Set<RefElement>>();

  private HashMap<String, Set<RefElement>> myOldPackageContents = null;

  Set<RefElement> myIgnoreElements = new HashSet<RefElement>();
  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    buildTreeNode(content, myPackageContents);
    if (isOldProblemsIncluded(getContext())){
      buildTreeNode(content, myOldPackageContents);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  private boolean isOldProblemsIncluded(final GlobalInspectionContextImpl context) {
    return context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN && myOldPackageContents != null;
  }

  private void buildTreeNode(final List<InspectionTreeNode> content,
                             final HashMap<String, Set<RefElement>> packageContents) {
    final GlobalInspectionContextImpl context = getContext();
    Set<String> packages = packageContents.keySet();
    for (String p : packages) {
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = packageContents.get(p);
      for (RefElement refElement : elements) {
        if (context != null && context.getUIOptions().SHOW_ONLY_DIFF && getElementStatus(refElement) == FileStatus.NOT_CHANGED) continue;
        if (packageContents != myPackageContents) {
          final Set<RefElement> currentElements = myPackageContents.get(p);
          if (currentElements != null){
            Set<RefEntity> currentEntities = new HashSet<RefEntity>(currentElements);
            if (contains(refElement, currentEntities)) continue;
          }
        }
        addNodeToParent(refElement, pNode);
      }
      if (pNode.getChildCount() > 0) content.add(pNode);
    }
  }

  public void updateContent() {
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

  public boolean hasReportedProblems() {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_ONLY_DIFF){
      return containsOnlyDiff(myPackageContents) ||
             myOldPackageContents != null && containsOnlyDiff(myOldPackageContents);
    } else {
      if (myPackageContents.size() > 0) return true;
    }
    return isOldProblemsIncluded(context) && myOldPackageContents.size() > 0;
  }

  private boolean containsOnlyDiff(final HashMap<String, Set<RefElement>> packageContents) {
    for (String packageName : packageContents.keySet()) {
      final Set<RefElement> refElements = packageContents.get(packageName);
      if (refElements != null){
        for (RefElement refElement : refElements) {
          if (getElementStatus(refElement) != FileStatus.NOT_CHANGED){
            return true;
          }
        }
      }
    }
    return false;
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public void ignoreElement(RefEntity refEntity) {
    myIgnoreElements.add((RefElement)refEntity);
  }

  public void cleanup() {
    super.cleanup();
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldPackageContents == null){
        myOldPackageContents = new HashMap<String, Set<RefElement>>();
      }
      myOldPackageContents.clear();
      myOldPackageContents.putAll(myPackageContents);
    } else {
      myOldPackageContents = null;
    }
    myPackageContents.clear();
    myIgnoreElements.clear();
  }


  public void finalCleanup() {
    super.finalCleanup();
    myOldPackageContents = null;
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


  public FileStatus getElementStatus(final RefElement element) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldPackageContents != null){
        final boolean old = contains(element, collectRefElements(myOldPackageContents));
        final boolean current = contains(element, collectRefElements(myPackageContents));
        return calcStatus(old, current);
      }
      return FileStatus.ADDED;
    }
    return FileStatus.NOT_CHANGED;
  }

  private static Set<RefEntity> collectRefElements(HashMap<String, Set<RefElement>> packageContents) {
    Set<RefEntity> allAvailable = new java.util.HashSet<RefEntity>();
    for (Set<RefElement> elements : packageContents.values()) {
      allAvailable.addAll(elements);
    }
    return allAvailable;
  }
}
