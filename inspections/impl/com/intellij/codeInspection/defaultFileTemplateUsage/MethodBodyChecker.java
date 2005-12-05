package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplate;

import java.util.Collection;
import java.util.List;

/**
 * @author Alexey
 */
public class MethodBodyChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.MethodBodyChecker");
  static void checkMethodBody(final PsiMethod method,
                                      final InspectionManager manager,
                                      final Collection<ProblemDescriptor> problemDescriptors) {
    PsiType returnType = method.getReturnType();
    if (method.isConstructor() || returnType == null) return;
    PsiCodeBlock body = method.getBody();
    if (body == null) return;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    try {
      PsiMethod templateMethod = method.getManager().getElementFactory().createMethod("x", returnType);
      setupMethodBody(superSignatures, templateMethod, aClass, true);
      if (!PsiEquivalenceUtil.areElementsEquivalent(templateMethod.getBody(), body)) return;
      Pair<? extends PsiElement, ? extends PsiElement> range = DefaultFileTemplateUsageInspection.getInteriorRange(body);
      final String description = InspectionsBundle.message("default.file.template.description");
      ProblemDescriptor problem = manager.createProblemDescriptor(range.first, range.second,
                                                                  description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createMethodBodyQuickFix(method));
      problemDescriptors.add(problem);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static FileTemplate setupMethodBody(final List<HierarchicalMethodSignature> superSignatures,
                                      final PsiMethod templateMethod,
                                      final PsiClass aClass, final boolean useDefaultTemplate) throws IncorrectOperationException {
    FileTemplateManager templateManager = FileTemplateManager.getInstance();
    FileTemplate template;
    if (superSignatures.size() == 0) {
      String name = FileTemplateManager.TEMPLATE_FROM_USAGE_METHOD_BODY;
      template = useDefaultTemplate ? templateManager.getDefaultTemplate(name) : templateManager.getCodeTemplate(name);
      CreateFromUsageUtils.setupMethodBody(templateMethod, aClass, template);
    }
    else {
      PsiMethod superMethod = superSignatures.get(0).getMethod();
      String name = superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                    FileTemplateManager.TEMPLATE_IMPLEMENTED_METHOD_BODY : FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;
      template = useDefaultTemplate ? templateManager.getDefaultTemplate(name) : templateManager.getCodeTemplate(name);
      OverrideImplementUtil.setupMethodBody(templateMethod, superMethod, aClass,template);
    }
    return template;
  }

  private static LocalQuickFix[] createMethodBodyQuickFix(final PsiMethod method) {
    PsiType returnType = method.getReturnType();
    PsiClass aClass = method.getContainingClass();
    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    FileTemplate template;
    try {
      PsiMethod templateMethod = method.getManager().getElementFactory().createMethod("x", returnType);
      template = setupMethodBody(superSignatures, templateMethod, aClass, false);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
    final ReplaceWithFileTemplateFix replaceWithFileTemplateFix = new ReplaceWithFileTemplateFix() {
      public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiType returnType = method.getReturnType();
        if (method.isConstructor() || returnType == null) return;
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
        try {
          PsiMethod templateMethod = method.getManager().getElementFactory().createMethod("x", returnType);
          setupMethodBody(superSignatures, templateMethod, aClass, false);
          PsiElement newBody = method.getBody().replace(templateMethod.getBody());
          CodeStyleManager.getInstance(project).reformat(newBody);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
    LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceWithFileTemplateFix);
    if (template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceWithFileTemplateFix, editFileTemplateFix};
  }
}
