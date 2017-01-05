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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 23, 2001
 * Time: 10:31:03 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;

import java.util.Comparator;

public class InspectionResultsViewComparator implements Comparator {
  private static final Logger LOG = Logger.getInstance(InspectionResultsViewComparator.class);

  public boolean areEqual(Object o1, Object o2) {
    return o1.getClass().equals(o2.getClass()) && compare(o1, o2) == 0;
  }

  @Override
  public int compare(Object o1, Object o2) {
    InspectionTreeNode node1 = (InspectionTreeNode)o1;
    InspectionTreeNode node2 = (InspectionTreeNode)o2;

    if (node1 instanceof InspectionSeverityGroupNode && node2 instanceof InspectionSeverityGroupNode) {
      final InspectionSeverityGroupNode groupNode1 = (InspectionSeverityGroupNode)node1;
      final InspectionSeverityGroupNode groupNode2 = (InspectionSeverityGroupNode)node2;
      return -SeverityRegistrar.getSeverityRegistrar(groupNode1.getProject()).compare(groupNode1.getSeverityLevel().getSeverity(), groupNode2.getSeverityLevel().getSeverity());
    }
    if (node1 instanceof InspectionSeverityGroupNode) return -1;
    if (node2 instanceof InspectionSeverityGroupNode) return 1;

    if (node1 instanceof InspectionGroupNode && node2 instanceof InspectionGroupNode) {
      return ((InspectionGroupNode)node1).getGroupTitle().compareToIgnoreCase(((InspectionGroupNode)node2).getGroupTitle());
    }
    if (node1 instanceof InspectionGroupNode) return -1;
    if (node2 instanceof InspectionGroupNode) return 1;

    if (node1 instanceof InspectionNode && node2 instanceof InspectionNode)
      return InspectionsConfigTreeComparator.getDisplayTextToSort(node1.toString())
        .compareToIgnoreCase(InspectionsConfigTreeComparator.getDisplayTextToSort(node2.toString()));
    if (node1 instanceof InspectionNode) return -1;
    if (node2 instanceof InspectionNode) return 1;

    if (node1 instanceof InspectionModuleNode && node2 instanceof InspectionModuleNode) {
      return Comparing.compare(node1.toString(), node2.toString());
    }
    if (node1 instanceof InspectionModuleNode) return -1;
    if (node2 instanceof InspectionModuleNode) return 1;

    if (node1 instanceof InspectionPackageNode && node2 instanceof InspectionPackageNode) {
      return ((InspectionPackageNode)node1).getPackageName().compareToIgnoreCase(((InspectionPackageNode)node2).getPackageName());
    }
    if (node1 instanceof InspectionPackageNode) return -1;
    if (node2 instanceof InspectionPackageNode) return 1;

    if (node1 instanceof RefElementNode && node2 instanceof RefElementNode){   //sort by filename and inside file by start offset
      return compareEntities(((RefElementNode)node1).getElement(), ((RefElementNode)node2).getElement());
    }
    if (node1 instanceof ProblemDescriptionNode && node2 instanceof ProblemDescriptionNode) {
      final CommonProblemDescriptor descriptor1 = ((ProblemDescriptionNode)node1).getDescriptor();
      final CommonProblemDescriptor descriptor2 = ((ProblemDescriptionNode)node2).getDescriptor();
      if (descriptor1 instanceof ProblemDescriptor && descriptor2 instanceof ProblemDescriptor) {
        int diff = ((ProblemDescriptor)descriptor1).getLineNumber() - ((ProblemDescriptor)descriptor2).getLineNumber();
        if (diff != 0) {
          return diff;
        }
        diff = ((ProblemDescriptor)descriptor1).getHighlightType().compareTo(((ProblemDescriptor)descriptor2).getHighlightType());
        if (diff != 0) {
          return diff;
        }
        diff = PsiUtilCore.compareElementsByPosition(((ProblemDescriptor)descriptor1).getStartElement(),
                                                     ((ProblemDescriptor)descriptor2).getStartElement());
        if (diff != 0) {
          return diff;
        }
        diff = PsiUtilCore.compareElementsByPosition(((ProblemDescriptor)descriptor2).getEndElement(),
                                                     ((ProblemDescriptor)descriptor1).getEndElement());
        if (diff != 0) return diff;

        final TextRange range1 = ((ProblemDescriptor)descriptor1).getTextRangeInElement();
        final TextRange range2 = ((ProblemDescriptor)descriptor2).getTextRangeInElement();
        if (range1 != null && range2 != null) {
          diff = range1.getStartOffset() - range2.getStartOffset();
          if (diff != 0) return diff;
          diff = range1.getEndOffset() - range2.getEndOffset();
          if (diff != 0) return diff;
        }
      }
      if (descriptor1 != null && descriptor2 != null) {
        return descriptor1.getDescriptionTemplate().compareToIgnoreCase(descriptor2.getDescriptionTemplate());
      }
      if (descriptor1 == null) return descriptor2 == null ? 0 : -1;
      return 1;
    }

    if (node1 instanceof RefElementNode && node2 instanceof ProblemDescriptionNode) {
      final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)node2).getDescriptor();
      if (descriptor instanceof ProblemDescriptor) {
        return compareEntity(((RefElementNode)node1).getElement(), ((ProblemDescriptor)descriptor).getPsiElement());
      }
      return compareEntities(((RefElementNode)node1).getElement(), ((ProblemDescriptionNode)node2).getElement());
    }

    if (node2 instanceof RefElementNode && node1 instanceof ProblemDescriptionNode) {
      final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)node1).getDescriptor();
      if (descriptor instanceof ProblemDescriptor) {
        return -compareEntity(((RefElementNode)node2).getElement(), ((ProblemDescriptor)descriptor).getPsiElement());
      }
      return -compareEntities(((RefElementNode)node2).getElement(), ((ProblemDescriptionNode)node1).getElement());
    }
    if (node1 instanceof InspectionRootNode && node2 instanceof InspectionRootNode) {
      //TODO Dmitry Batkovich: optimization, because only one root node is existed
      return 0;
    }

    LOG.error("node1: " + node1 + ", node2: " + node2);
    return 0;
  }

  private static int compareEntity(final RefEntity entity, final PsiElement element) {
    if (entity instanceof RefElement) {
      final PsiElement psiElement = ((RefElement)entity).getElement();
      if (psiElement != null && element != null) {
        return PsiUtilCore.compareElementsByPosition(psiElement, element);
      }
      if (element == null) return psiElement == null ? 0 : 1;
    }
    if (element instanceof PsiQualifiedNamedElement) {
      return StringUtil.compare(entity.getQualifiedName(), ((PsiQualifiedNamedElement)element).getQualifiedName(), true);
    }
    if (element instanceof PsiNamedElement) {
      return StringUtil.compare(entity.getName(), ((PsiNamedElement)element).getName(), true);
    }
    return -1;
  }

  private static int compareEntities(final RefEntity entity1, final RefEntity entity2) {
    if (entity1 instanceof RefElement && entity2 instanceof RefElement) {
      final SmartPsiElementPointer p1 = ((RefElement)entity1).getPointer();
      final SmartPsiElementPointer p2 = ((RefElement)entity2).getPointer();
      if (p1 != null && p2 != null) {
        final VirtualFile file1 = p1.getVirtualFile();
        final VirtualFile file2 = p2.getVirtualFile();
        if (file1 != null && Comparing.equal(file1, file2) && file1.isValid()) {
          final int positionComparing = PsiUtilCore.compareElementsByPosition(((RefElement)entity1).getElement(), ((RefElement)entity2).getElement());
          if (positionComparing != 0) {
            return positionComparing;
          }
        }
      }
    }
    if (entity1 instanceof RefFile && entity2 instanceof RefFile) {
      final VirtualFile file1 = ((RefFile)entity1).getPointer().getVirtualFile();
      final VirtualFile file2 = ((RefFile)entity2).getPointer().getVirtualFile();
      if (file1 != null && file2 != null) {
        if (file1.equals(file2)) return 0;
        final int cmp = compareEntitiesByName(entity1, entity2);
        if (cmp != 0) return cmp;
        return file1.hashCode() - file2.hashCode();
      }
    }
    if (entity1 != null && entity2 != null) {
      return compareEntitiesByName(entity1, entity2);
    }
    if (entity1 != null) return -1;
    return entity2 != null ? 1 : 0;
  }

  private static int compareEntitiesByName(RefEntity entity1, RefEntity entity2) {
    final int nameComparing = entity1.getName().compareToIgnoreCase(entity2.getName());
    if (nameComparing != 0) {
      return nameComparing;
    }
    return entity1.getQualifiedName().compareToIgnoreCase(entity2.getQualifiedName());
  }

  private static class InspectionResultsViewComparatorHolder {
    private static final InspectionResultsViewComparator ourInstance = new InspectionResultsViewComparator();
  }

  public static InspectionResultsViewComparator getInstance() {
    return InspectionResultsViewComparatorHolder.ourInstance;
  }
}
