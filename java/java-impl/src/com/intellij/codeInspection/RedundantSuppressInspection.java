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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefJavaVisitor;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.BidirectionalMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author cdr
 */
public class RedundantSuppressInspection extends GlobalInspectionTool{
  private BidirectionalMap<String, QuickFix> myQuickFixes = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.RedundantSuppressInspection");

  public boolean IGNORE_ALL = false;

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.suppression.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantSuppression";
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore @SuppressWarning(\"ALL\")", this, "IGNORE_ALL");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_ALL) {
      super.writeSettings(node);
    }
  }

  @Override
  public void runInspection(@NotNull final AnalysisScope scope,
                            @NotNull final InspectionManager manager,
                            @NotNull final GlobalInspectionContext globalContext,
                            @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitClass(@NotNull RefClass refClass) {
        if (!globalContext.shouldCheck(refClass, RedundantSuppressInspection.this)) return;
        CommonProblemDescriptor[] descriptors = checkElement(refClass, manager, globalContext.getProject());
        if (descriptors != null) {
          for (CommonProblemDescriptor descriptor : descriptors) {
            if (descriptor instanceof ProblemDescriptor) {
              final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
              final PsiMember member = PsiTreeUtil.getParentOfType(psiElement, PsiMember.class);
              final RefElement refElement = globalContext.getRefManager().getReference(member);
              if (refElement != null) {
                problemDescriptionsProcessor.addProblemElement(refElement, descriptor);
                continue;
              }
            }
            problemDescriptionsProcessor.addProblemElement(refClass, descriptor);
          }
        }
      }
    });
  }

  @Nullable
  private CommonProblemDescriptor[] checkElement(@NotNull RefClass refEntity, @NotNull InspectionManager manager, @NotNull Project project) {
    final PsiClass psiClass = refEntity.getElement();
    if (psiClass == null) return null;
    return checkElement(psiClass, manager, project);
  }

  public CommonProblemDescriptor[] checkElement(@NotNull final PsiElement psiElement, @NotNull InspectionManager manager, @NotNull Project project) {
    final Map<PsiElement, Collection<String>> suppressedScopes = new THashMap<PsiElement, Collection<String>>();
    psiElement.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitModifierList(PsiModifierList list) {
        super.visitModifierList(list);
        final PsiElement parent = list.getParent();
        if (parent instanceof PsiModifierListOwner && !(parent instanceof PsiClass)) {
          checkElement(parent);
        }
      }

      @Override public void visitComment(PsiComment comment) {
        checkElement(comment);
      }

      @Override public void visitClass(PsiClass aClass) {
        if (aClass == psiElement) {
          super.visitClass(aClass);
          checkElement(aClass);
        }
      }


      private void checkElement(final PsiElement owner) {
        String idsString = SuppressManager.getInstance().getSuppressedInspectionIdsIn(owner);
        if (idsString != null && !idsString.isEmpty()) {
          List<String> ids = StringUtil.split(idsString, ",");
          if (IGNORE_ALL && (ids.contains(SuppressionUtil.ALL) || ids.contains(SuppressionUtil.ALL.toLowerCase()))) return;
          Collection<String> suppressed = suppressedScopes.get(owner);
          if (suppressed == null) {
            suppressed = ids;
          }
          else {
            for (String id : ids) {
              if (!suppressed.contains(id)) {
                suppressed.add(id);
              }
            }
          }
          suppressedScopes.put(owner, suppressed);
        }
      }
    });

    if (suppressedScopes.values().isEmpty()) return null;
    // have to visit all file from scratch since inspections can be written in any perversive way including checkFile() overriding
    Collection<InspectionToolWrapper> suppressedTools = new THashSet<InspectionToolWrapper>();
    InspectionToolWrapper[] toolWrappers = getInspectionTools(psiElement, manager);
    for (Collection<String> ids : suppressedScopes.values()) {
      for (Iterator<String> iterator = ids.iterator(); iterator.hasNext(); ) {
        final String shortName = iterator.next().trim();
        for (InspectionToolWrapper toolWrapper : toolWrappers) {
          if (toolWrapper instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)toolWrapper).getTool().getID().equals(shortName)) {
            if (((LocalInspectionToolWrapper)toolWrapper).isUnfair()) {
              iterator.remove();
              break;
            }
            else {
              suppressedTools.add(toolWrapper);
            }
          }
          else if (toolWrapper.getShortName().equals(shortName)) {
            //ignore global unused as it won't be checked anyway
            if (toolWrapper instanceof LocalInspectionToolWrapper || toolWrapper instanceof GlobalInspectionToolWrapper) {
              suppressedTools.add(toolWrapper);
            }
            else {
              iterator.remove();
              break;
            }
          }
        }
      }
    }

    final AnalysisScope scope = new AnalysisScope(psiElement.getContainingFile());
    final InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    GlobalInspectionContextImpl globalContext = inspectionManagerEx.createNewGlobalContext(false);
    globalContext.setCurrentScope(scope);
    final RefManagerImpl refManager = (RefManagerImpl)globalContext.getRefManager();
    refManager.inspectionReadActionStarted();
    final List<ProblemDescriptor> result;
    try {
      result = new ArrayList<ProblemDescriptor>();
      for (InspectionToolWrapper toolWrapper : suppressedTools) {
        String toolId = toolWrapper instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)toolWrapper).getTool().getID() : toolWrapper.getShortName();
        toolWrapper.initialize(globalContext);
        Collection<CommonProblemDescriptor> descriptors;
        if (toolWrapper instanceof LocalInspectionToolWrapper) {
          LocalInspectionToolWrapper local = (LocalInspectionToolWrapper)toolWrapper;
          if (local.isUnfair()) continue; //cant't work with passes other than LocalInspectionPass
          local.processFile(psiElement.getContainingFile(), false, manager);
          descriptors = local.getProblemDescriptors();
        }
        else if (toolWrapper instanceof GlobalInspectionToolWrapper) {
          GlobalInspectionToolWrapper global = (GlobalInspectionToolWrapper)toolWrapper;
          if (global.getTool().isGraphNeeded()) {
            refManager.findAllDeclarations();
          }
          global.processFile(scope, manager, globalContext, false);
          descriptors = global.getProblemDescriptors();
        }
        else {
          continue;
        }
        for (PsiElement suppressedScope : suppressedScopes.keySet()) {
          Collection<String> suppressedIds = suppressedScopes.get(suppressedScope);
          if (!suppressedIds.contains(toolId)) continue;
          for (CommonProblemDescriptor descriptor : descriptors) {
            if (!(descriptor instanceof ProblemDescriptor)) continue;
            PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
            if (element == null) continue;
            PsiElement annotation = SuppressManager.getInstance().getElementToolSuppressedIn(element, toolId);
            if (annotation != null && PsiTreeUtil.isAncestor(suppressedScope, annotation, false) || annotation == null && !PsiTreeUtil.isAncestor(suppressedScope, element, false)) {
              suppressedIds.remove(toolId);
              break;
            }
          }
        }
      }
      for (PsiElement suppressedScope : suppressedScopes.keySet()) {
        Collection<String> suppressedIds = suppressedScopes.get(suppressedScope);
        for (String toolId : suppressedIds) {
          PsiMember psiMember;
          String problemLine = null;
          if (suppressedScope instanceof PsiMember) {
            psiMember = (PsiMember)suppressedScope;
          }
          else {
            psiMember = PsiTreeUtil.getParentOfType(suppressedScope, PsiDocCommentOwner.class);
            final PsiStatement statement = PsiTreeUtil.getNextSiblingOfType(suppressedScope, PsiStatement.class);
            problemLine = statement != null ? statement.getText() : null;
          }
          if (psiMember != null && psiMember.isValid()) {
            String description = InspectionsBundle.message("inspection.redundant.suppression.description");
            if (myQuickFixes == null) myQuickFixes = new BidirectionalMap<String, QuickFix>();
            final String key = toolId + (problemLine != null ? ";" + problemLine : "");
            QuickFix fix = myQuickFixes.get(key);
            if (fix == null) {
              fix = new RemoveSuppressWarningAction(toolId, problemLine);
              myQuickFixes.put(key, fix);
            }
            PsiElement identifier = null;
            if (psiMember instanceof PsiMethod) {
              identifier = ((PsiMethod)psiMember).getNameIdentifier();
            }
            else if (psiMember instanceof PsiField) {
              identifier = ((PsiField)psiMember).getNameIdentifier();
            }
            else if (psiMember instanceof PsiClass) {
              identifier = ((PsiClass)psiMember).getNameIdentifier();
            }
            if (identifier == null) {
              identifier = psiMember;
            }
            result.add(
              manager.createProblemDescriptor(identifier, description, (LocalQuickFix)fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                              false));
          }
        }
      }
    }
    finally {
      refManager.inspectionReadActionFinished();
      globalContext.close(true);
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, InspectionManager manager) {
    ModifiableModel model = InspectionProjectProfileManager.getInstance(manager.getProject()).getInspectionProfile().getModifiableModel();
    InspectionProfileWrapper profile = new InspectionProfileWrapper((InspectionProfile)model);
    profile.init(manager.getProject());

    return (InspectionToolWrapper[])profile.getInspectionTools(psiElement);
  }


  @Override
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return myQuickFixes != null ? myQuickFixes.get(hint) : new RemoveSuppressWarningAction(hint);
  }


  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    if (myQuickFixes != null) {
      final List<String> list = myQuickFixes.getKeysByValue(fix);
      if (list != null) {
        LOG.assertTrue(list.size() == 1);
        return list.get(0);
      }
    }
    return null;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }
}
