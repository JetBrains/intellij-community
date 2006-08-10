package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class RedundantSuppressInspection extends GlobalInspectionTool{
  public String getGroupDisplayName() {
    return GroupNames.GENERAL_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.suppression.name");
  }

  @NonNls
  public String getShortName() {
    return "RedundantSuppression";
  }


  public void runInspection(final AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitClass(RefClass refClass) {
        if (globalContext.isSuppressed(refClass, getShortName())) return;
        CommonProblemDescriptor[] descriptors = checkElement(refClass, manager, globalContext.getProject());
        if (descriptors != null) {
          problemDescriptionsProcessor.addProblemElement(refClass, descriptors);
        }
      }
    });
  }

  @Nullable
  private static CommonProblemDescriptor[] checkElement(RefClass refEntity, InspectionManager manager, final Project project) {
    final PsiElement psiElement = refEntity.getElement();
    final Map<PsiElement, Collection<String>> suppressedScopes = new THashMap<PsiElement, Collection<String>>();
    psiElement.accept(new PsiRecursiveElementVisitor() {
      public void visitModifierList(PsiModifierList list) {
        super.visitModifierList(list);
        final PsiElement parent = list.getParent();
        if (parent instanceof PsiModifierListOwner && !(parent instanceof PsiClass)) {
          checkElement(parent);
        }
      }

      public void visitComment(PsiComment comment) {
        checkElement(comment);
      }

      public void visitClass(PsiClass aClass) {
        if (aClass == psiElement) {
          super.visitClass(aClass);
          checkElement(aClass);
        }
      }


      private void checkElement(final PsiElement owner) {
        String idsString = InspectionManagerEx.getSuppressedInspectionIdsIn(owner);
        if (idsString != null && idsString.length() != 0) {
          List<String> ids = StringUtil.split(idsString, ",");
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
    final ModifiableModel model = InspectionProjectProfileManager.getInstance(manager.getProject()).getInspectionProfile(psiElement).getModifiableModel();
    InspectionProfileWrapper profile = new InspectionProfileWrapper((InspectionProfile)model);
    profile.init(manager.getProject());
    Collection<InspectionTool> suppressedTools = new THashSet<InspectionTool>();

    InspectionTool[] tools = profile.getInspectionTools();
    for (Collection<String> ids : suppressedScopes.values()) {
      for (String id : ids) {
        String shortName = id.trim();
        for (InspectionTool tool : tools) {
          if (tool.getShortName().equals(shortName)) {
            suppressedTools.add(tool);
          }
        }
      }
    }

    GlobalInspectionContextImpl globalContext = ((InspectionManagerEx)InspectionManager.getInstance(project)).createNewGlobalContext(false);
    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (InspectionTool tool : suppressedTools) {
      String toolId = tool.getShortName();
      tool.initialize(globalContext);
      Collection<CommonProblemDescriptor> descriptors;
      if (tool instanceof LocalInspectionToolWrapper) {
        LocalInspectionToolWrapper local = (LocalInspectionToolWrapper)tool;
        if (local.getTool() instanceof UnfairLocalInspectionTool) continue; //cant't work with passes other than LocalInspectionPass
        local.processFile(psiElement.getContainingFile(), false, manager);
        descriptors = local.getProblemDescriptors();
      }
      else if (tool instanceof GlobalInspectionToolWrapper) {
        GlobalInspectionToolWrapper global = (GlobalInspectionToolWrapper)tool;
        global.processFile(new AnalysisScope(psiElement.getContainingFile()), manager, globalContext, false);
        descriptors = global.getProblemDescriptors();
      }
      else {
        continue;
      }
      for (PsiElement suppressedScope : suppressedScopes.keySet()) {
        Collection<String> suppressedIds = suppressedScopes.get(suppressedScope);
        if (!suppressedIds.contains(toolId)) continue;
        boolean hasErrorInsideSuppressedScope = false;
        for (CommonProblemDescriptor descriptor : descriptors) {
          if (!(descriptor instanceof ProblemDescriptor)) continue;
          PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
          if (element == null) continue;
          PsiElement annotation = InspectionManagerEx.getElementToolSuppressedIn(element, toolId);
          if (annotation != null && PsiTreeUtil.isAncestor(suppressedScope, annotation, false)) {
            hasErrorInsideSuppressedScope = true;
            break;
          }
        }
        if (!hasErrorInsideSuppressedScope) {
          PsiElement element = suppressedScope instanceof PsiComment
                               ? PsiTreeUtil.skipSiblingsForward(suppressedScope, PsiWhiteSpace.class)
                               : suppressedScope.getFirstChild();
          PsiElement annotation = InspectionManagerEx.getElementToolSuppressedIn(element, toolId);
          if (annotation != null && annotation.isValid()) {
            String description = InspectionsBundle.message("inspection.redundant.suppression.description");
            LocalQuickFix fix = new RemoveSuppressWarningAction(toolId, annotation);
            ProblemDescriptor descriptor = manager.createProblemDescriptor(annotation, description, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            result.add(descriptor);
          }
        }
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }


  public boolean isGraphNeeded() {
    return true;
  }
}
