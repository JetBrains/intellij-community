/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:50:56 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class InspectionTool extends InspectionProfileEntry {
  private GlobalInspectionContextImpl myContext;
  public static String ourOutputPath;

  public void initialize(GlobalInspectionContextImpl context) {
    myContext = context;
  }

  public GlobalInspectionContextImpl getContext() {
    return myContext;
  }

  public RefManager getRefManager() {
    return myContext.getRefManager();
  }

  public abstract void runInspection(AnalysisScope scope, final InspectionManager manager);

  public abstract void exportResults(Element parentNode);

  public abstract boolean isGraphNeeded();
  @Nullable
  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    return null;
  }

  @NotNull
  public abstract JobDescriptor[] getJobDescriptors();

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    return false;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return getDefaultLevel() != HighlightDisplayLevel.DO_NOT_SHOW;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public final String getDescriptionFileName() {
    return getShortName() + ".html";
  }

  public final String getFolderName() {
    return getShortName();
  }

  public void cleanup() {
  }

  public void finalCleanup(){
    cleanup();
  }

  public abstract HTMLComposer getComposer();

  public abstract boolean hasReportedProblems();

  public abstract void updateContent();

  public abstract InspectionTreeNode[] getContents();

  public abstract Map<String, Set<RefElement>> getPackageContent();

  @Nullable
  public Set<RefModule> getModuleProblems(){
    return null;
  }

  public abstract void ignoreElement(RefEntity refElement);

  protected RefElementNode addNodeToParent(RefElement refElement, InspectionPackageNode packageNode){
    final Set<InspectionTreeNode> children = new HashSet<InspectionTreeNode>();
    TreeUtil.traverseDepth(packageNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        children.add((InspectionTreeNode)node);
        return true;
      }
    });
    RefElementNode nodeToAdd = new RefElementNode(refElement, this);
    boolean firstLevel = true;
    RefElementNode prevNode = null;
    while (true) {
      RefElementNode currentNode = firstLevel ? nodeToAdd : new RefElementNode(refElement, this);
      for (InspectionTreeNode node : children) {
        if (node instanceof RefElementNode){
          final RefElementNode refElementNode = (RefElementNode)node;
          if (Comparing.equal(refElementNode.getElement(), refElement)){
            if (firstLevel){
              return refElementNode;
            } else {
              refElementNode.add(prevNode);
              return nodeToAdd;
            }
          }
        }
      }
      if (!firstLevel) {
        currentNode.add(prevNode);
      }
      RefEntity owner = refElement.getOwner();
      if (!(owner instanceof RefElement)){
        packageNode.add(currentNode);
        return nodeToAdd;
      }
      refElement = (RefElement)owner;
      prevNode = currentNode;
      firstLevel = false;
    }
  }

  public abstract boolean isElementIgnored(final RefElement element);


  public abstract FileStatus getElementStatus(final RefElement element);

  protected static FileStatus calcStatus(boolean old, boolean current) {
    if (old) {
      if (!current) {
        return FileStatus.DELETED;
      }
    }
    else if (current) {
      return FileStatus.ADDED;
    }
    return FileStatus.NOT_CHANGED;
  }

  protected static boolean contains(RefElement element, Collection<RefEntity> entities){
    for (RefEntity refEntity : entities) {
      if (!(refEntity instanceof RefElement)) continue;
      if (Comparing.equal(((RefElement)refEntity).getElement(), element.getElement())){
        return true;
      }
    }
    return false;
  }

  protected HighlightSeverity getCurrentSeverity(RefElement element) {
    if (myContext != null) {
      final Set<Pair<InspectionTool, InspectionProfile>> tools = myContext.getTools().get(getShortName());
      for (Pair<InspectionTool, InspectionProfile> pair : tools) {
        if (pair.first == this) {
          return pair.second.getErrorLevel(HighlightDisplayKey.find(getShortName())).getSeverity();
        }
      }
    }
    /*final PsiElement psiElement = element.getElement();
    if (psiElement != null) {
      final InspectionProfile profile =
        InspectionProjectProfileManager.getInstance(getContext().getProject()).getInspectionProfile(psiElement);
      final HighlightDisplayLevel level = profile.getErrorLevel(HighlightDisplayKey.find(getShortName()));
      return level.getSeverity();
    }*/
    return HighlightSeverity.INFORMATION;
  }

  protected String getTextAttributeKey(HighlightSeverity severity, ProblemHighlightType highlightType) {
    if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      return HighlightInfoType.DEPRECATED.getAttributesKey().getExternalName();
    }
    else if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
      if (JavaDocReferenceInspection.SHORT_NAME.equals(getShortName())) {
        return HighlightInfoType.JAVADOC_WRONG_REF.getAttributesKey().getExternalName();
      }
      else {
        return HighlightInfoType.WRONG_REF.getAttributesKey().getExternalName();
      }
    }
    else if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      return HighlightInfoType.UNUSED_SYMBOL.getAttributesKey().getExternalName();
    }
    return SeverityRegistrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey().getExternalName();
  }

  public static void setOutputPath(final String output) {
    ourOutputPath = output;
  }
}