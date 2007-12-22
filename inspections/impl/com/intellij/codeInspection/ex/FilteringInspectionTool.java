package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.codeInspection.reference.RefJavaVisitor;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public abstract class FilteringInspectionTool extends InspectionTool {
  public abstract RefFilter getFilter();
  private HashMap<String, Set<RefEntity>> myPackageContents = new HashMap<String, Set<RefEntity>>();

  private HashMap<String, Set<RefEntity>> myOldPackageContents = null;

  private Set<RefEntity> myIgnoreElements = new HashSet<RefEntity>();
 
  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefEntity>>();
    getContext().getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;//dead code doesn't work with refModule | refPackage
        RefJavaElement refElement = (RefJavaElement)refEntity;
        if (!(getContext().getUIOptions().FILTER_RESOLVED_ITEMS && myIgnoreElements.contains(refElement)) && refElement.isValid() && getFilter().accepts(refElement)) {
          String packageName = RefJavaUtil.getInstance().getPackageName(refEntity);
          Set<RefEntity> content = myPackageContents.get(packageName);
          if (content == null) {
            content = new HashSet<RefEntity>();
            myPackageContents.put(packageName, content);
          }
          content.add(refEntity);
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
      if (!myPackageContents.isEmpty()) return true;
    }
    return isOldProblemsIncluded() && myOldPackageContents.size() > 0;
  }

  private boolean containsOnlyDiff(final HashMap<String, Set<RefEntity>> packageContents) {
    for (String packageName : packageContents.keySet()) {
      final Set<RefEntity> refElements = packageContents.get(packageName);
      if (refElements != null){
        for (RefEntity refElement : refElements) {
          if (getElementStatus(refElement) != FileStatus.NOT_CHANGED){
            return true;
          }
        }
      }
    }
    return false;
  }

  public Map<String, Set<RefEntity>> getPackageContent() {
    return myPackageContents;
  }

  public Map<String, Set<RefEntity>> getOldPackageContent() {
    return myOldPackageContents;
  }

  public void ignoreCurrentElement(RefEntity refEntity) {
    if (refEntity == null) return;
    myIgnoreElements.add(refEntity);
  }

  public void amnesty(RefEntity refEntity) {
    myIgnoreElements.remove(refEntity);    
  }

  public void cleanup() {
    super.cleanup();
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldPackageContents == null){
        myOldPackageContents = new HashMap<String, Set<RefEntity>>();
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

  public boolean isElementIgnored(final RefEntity element) {
    for (RefEntity entity : myIgnoreElements) {
      if (Comparing.equal(entity, element)) {
        return true;
      }
    }
    return false;
  }


  public FileStatus getElementStatus(final RefEntity element) {
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

  public Collection<RefEntity> getIgnoredRefElements() {
    return myIgnoreElements;
  }

  private static Set<RefEntity> collectRefElements(HashMap<String, Set<RefEntity>> packageContents) {
    Set<RefEntity> allAvailable = new HashSet<RefEntity>();
    for (Set<RefEntity> elements : packageContents.values()) {
      allAvailable.addAll(elements);
    }
    return allAvailable;
  }
}
