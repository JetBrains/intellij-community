/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author cdr
 */
public class RedundantSuppressInspectionBase extends GlobalInspectionTool {
  private BidirectionalMap<String, QuickFix> myQuickFixes;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.RedundantSuppressInspection");

  public boolean IGNORE_ALL;

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
        if (!globalContext.shouldCheck(refClass, RedundantSuppressInspectionBase.this)) return;
        CommonProblemDescriptor[] descriptors = checkElement(refClass, manager);
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
  private CommonProblemDescriptor[] checkElement(@NotNull RefClass refEntity, @NotNull InspectionManager manager) {
    final PsiClass psiClass = refEntity.getElement();
    if (psiClass == null) return null;
    return checkElement(psiClass, manager);
  }

  public CommonProblemDescriptor[] checkElement(@NotNull final PsiElement psiElement, @NotNull final InspectionManager manager) {
    final Map<PsiElement, Collection<String>> suppressedScopes = new THashMap<>();
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
        String idsString = JavaSuppressionUtil.getSuppressedInspectionIdsIn(owner);
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
    // have to visit all file from scratch since inspections can be written in any pervasive way including checkFile() overriding
    Map<InspectionToolWrapper, String> suppressedTools = new THashMap<>();
    InspectionToolWrapper[] toolWrappers = getInspectionTools(psiElement, manager);
    for (Collection<String> ids : suppressedScopes.values()) {
      for (Iterator<String> iterator = ids.iterator(); iterator.hasNext(); ) {
        final String shortName = iterator.next().trim();
        for (InspectionToolWrapper toolWrapper : toolWrappers) {
          if (toolWrapper instanceof LocalInspectionToolWrapper &&
              (((LocalInspectionToolWrapper)toolWrapper).getTool().getID().equals(shortName) ||
               shortName.equals(((LocalInspectionToolWrapper)toolWrapper).getTool().getAlternativeID()))) {
            if (((LocalInspectionToolWrapper)toolWrapper).isUnfair()) {
              iterator.remove();
              break;
            }
            else {
              suppressedTools.put(toolWrapper, shortName);
            }
          }
          else if (toolWrapper.getShortName().equals(shortName)) {
            //ignore global unused as it won't be checked anyway
            if (toolWrapper instanceof LocalInspectionToolWrapper ||
                toolWrapper instanceof GlobalInspectionToolWrapper && !((GlobalInspectionToolWrapper)toolWrapper).getTool().isGraphNeeded()) {
              suppressedTools.put(toolWrapper, shortName);
            }
            else {
              iterator.remove();
              break;
            }
          }
        }
      }
    }

    PsiFile file = psiElement.getContainingFile();
    final AnalysisScope scope = new AnalysisScope(file);

    final GlobalInspectionContextBase globalContext = createContext(file);
    globalContext.setCurrentScope(scope);
    final RefManagerImpl refManager = (RefManagerImpl)globalContext.getRefManager();
    refManager.inspectionReadActionStarted();
    final List<ProblemDescriptor> result;
    try {
      result = new ArrayList<>();
      for (InspectionToolWrapper toolWrapper : suppressedTools.keySet()) {
        String toolId = suppressedTools.get(toolWrapper);
        toolWrapper.initialize(globalContext);
        final Collection<CommonProblemDescriptor> descriptors;
        if (toolWrapper instanceof LocalInspectionToolWrapper) {
          LocalInspectionToolWrapper local = (LocalInspectionToolWrapper)toolWrapper;
          if (local.isUnfair()) continue; //cant't work with passes other than LocalInspectionPass
          List<ProblemDescriptor> results = local.getTool().processFile(file, manager);
          descriptors = new ArrayList<>(results);
        }
        else if (toolWrapper instanceof GlobalInspectionToolWrapper) {
          final GlobalInspectionToolWrapper global = (GlobalInspectionToolWrapper)toolWrapper;
          GlobalInspectionTool globalTool = global.getTool();
          //when graph is needed, results probably depend on outer files so absence of results on one file (in current context) doesn't guarantee anything
          if (globalTool.isGraphNeeded()) continue;
          descriptors = new ArrayList<>();
          globalContext.getRefManager().iterate(new RefVisitor() {
            @Override public void visitElement(@NotNull RefEntity refEntity) {
              CommonProblemDescriptor[] descriptors1 = global.getTool().checkElement(refEntity, scope, manager, globalContext, new ProblemDescriptionsProcessor() {});
              if (descriptors1 != null) {
                ContainerUtil.addAll(descriptors, descriptors1);
              }
            }
          });
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
            PsiElement annotation = JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId);
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
            if (myQuickFixes == null) myQuickFixes = new BidirectionalMap<>();
            final String key = toolId + (problemLine != null ? ";" + problemLine : "");
            QuickFix fix = myQuickFixes.get(key);
            if (fix == null) {
              fix = new RemoveSuppressWarningAction(toolId, problemLine);
              myQuickFixes.put(key, fix);
            }
            PsiElement identifier = null;
            if (!(suppressedScope instanceof PsiMember)) {
              identifier = suppressedScope;
            }
            else if (psiMember instanceof PsiMethod) {
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

  protected GlobalInspectionContextBase createContext(PsiFile file) {
    return new GlobalInspectionContextBase(file.getProject());
  }

  protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, @NotNull InspectionManager manager) {
    return InspectionProjectProfileManager.getInstance(manager.getProject()).getCurrentProfile().getModifiableModel()
      .getInspectionTools(psiElement);
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
