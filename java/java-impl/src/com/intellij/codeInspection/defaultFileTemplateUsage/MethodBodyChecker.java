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
package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Alexey
 */
public class MethodBodyChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.MethodBodyChecker");

  @Nullable
  private static PsiMethod getTemplateMethod(PsiType returnType, List<HierarchicalMethodSignature> superSignatures, final PsiClass aClass) {
    Project project = aClass.getProject();

    if (!(returnType instanceof PsiPrimitiveType)) {
      returnType = PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    }
    try {
      final FileTemplate template = getMethodFileTemplate(superSignatures, true);
      if (template == null) return null;
      final String fileTemplateName = template.getName();
      String methodName = superSignatures.isEmpty() ? "" : superSignatures.get(0).getName();
      String key = returnType.getCanonicalText() + "+" + methodName + "+"+fileTemplateName;
      final Map<String, PsiMethod> cache = getTemplatesCache(aClass);
      PsiMethod method = cache.get(key);
      if (method == null) {
        method = JavaPsiFacade.getInstance(project).getElementFactory().createMethod("x", returnType);
        setupMethodBody(superSignatures, method, aClass, true);
        cache.put(key, method);
      }
      return method;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  private static final Key<Map<String, PsiMethod>> CACHE_KEY = new Key<Map<String, PsiMethod>>("MethodBodyChecker templates cache");

  private static Map<String, PsiMethod> getTemplatesCache(PsiClass aClass) {
    Map<String, PsiMethod> cache = aClass.getUserData(CACHE_KEY);
    if (cache == null) {
      cache = ((UserDataHolderEx)aClass).putUserDataIfAbsent(CACHE_KEY, new ConcurrentHashMap<String, PsiMethod>());
    }
    return cache;
  }

  static void checkMethodBody(final PsiMethod method,
                              final InspectionManager manager,
                              final Collection<ProblemDescriptor> problemDescriptors, boolean onTheFly) {
    PsiType returnType = method.getReturnType();
    if (method.isConstructor() || returnType == null) return;
    PsiCodeBlock body = method.getBody();
    if (body == null) return;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null || aClass.isInterface()) return;
    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    final PsiMethod superMethod = superSignatures.isEmpty() ? null : superSignatures.get(0).getMethod();

    final PsiMethod templateMethod = getTemplateMethod(returnType, superSignatures, aClass);
    if (templateMethod == null) return;

    final PsiCodeBlock templateBody = templateMethod.getBody();
    if (templateBody == null) return;

    if (PsiEquivalenceUtil.areElementsEquivalent(body, templateBody, new Comparator<PsiElement>(){
      public int compare(final PsiElement element1, final PsiElement element2) {
        // templates may be different on super method name                              
        if (element1 == superMethod && (element2 == templateMethod || element2 == null)) return 0;
        return 1;
      }
    }, true)) {
      Pair<? extends PsiElement, ? extends PsiElement> range = DefaultFileTemplateUsageInspection.getInteriorRange(body);
      final String description = InspectionsBundle.message("default.file.template.description");
      ProblemDescriptor problem = manager.createProblemDescriptor(range.first, range.second, description,
                                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly,
                                                                  createMethodBodyQuickFix(method));
      problemDescriptors.add(problem);
    }
  }

  @Nullable
  private static FileTemplate getMethodFileTemplate(final List<HierarchicalMethodSignature> superSignatures,
                                                    final boolean useDefaultTemplate) {
    FileTemplateManager templateManager = FileTemplateManager.getInstance();
    FileTemplate template;
    if (superSignatures.isEmpty()) {
      String name = JavaTemplateUtil.TEMPLATE_FROM_USAGE_METHOD_BODY;
      template = useDefaultTemplate ? templateManager.getDefaultTemplate(name) : templateManager.getCodeTemplate(name);
    }
    else {
      PsiMethod superMethod = superSignatures.get(0).getMethod();
      String name = superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                    JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
      template = useDefaultTemplate ? templateManager.getDefaultTemplate(name) : templateManager.getCodeTemplate(name);
    }
    return template;
  }

  private static final String NEW_METHOD_BODY_TEMPLATE_NAME = FileTemplateManager.getInstance().getDefaultTemplate(JavaTemplateUtil.TEMPLATE_FROM_USAGE_METHOD_BODY).getName();
  @Nullable
  private static FileTemplate setupMethodBody(final List<HierarchicalMethodSignature> superSignatures,
                                              final PsiMethod templateMethod,
                                              final PsiClass aClass,
                                              final boolean useDefaultTemplate) throws IncorrectOperationException {
    FileTemplate template = getMethodFileTemplate(superSignatures, useDefaultTemplate);
    if (template == null) return null;
    if (NEW_METHOD_BODY_TEMPLATE_NAME.equals(template.getName())) {
      CreateFromUsageUtils.setupMethodBody(templateMethod, aClass, template);
    }
    else {
      PsiMethod superMethod = superSignatures.get(0).getMethod();
      OverrideImplementUtil.setupMethodBody(templateMethod, superMethod, aClass,template);
    }
    return template;
  }

  @Nullable
  private static LocalQuickFix[] createMethodBodyQuickFix(final PsiMethod method) {
    PsiType returnType = method.getReturnType();
    PsiClass aClass = method.getContainingClass();
    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    FileTemplate template;
    try {
      PsiMethod templateMethod = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createMethod("x", returnType);
      template = setupMethodBody(superSignatures, templateMethod, aClass, false);
    }
    catch (IncorrectOperationException e) {
      return null;
    }

    final ReplaceWithFileTemplateFix replaceWithFileTemplateFix = new ReplaceWithFileTemplateFix() {
      public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
        PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        if (method == null) return;
        PsiType returnType = method.getReturnType();
        if (method.isConstructor() || returnType == null) return;
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        if (!CodeInsightUtil.preparePsiElementsForWrite(body)) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
        try {
          PsiMethod templateMethod = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createMethod("x", returnType);
          setupMethodBody(superSignatures, templateMethod, aClass, false);
          final PsiCodeBlock templateBody = templateMethod.getBody();
          if (templateBody == null) return;

          PsiElement newBody = body.replace(templateBody);
          CodeStyleManager.getInstance(aClass.getManager()).reformat(newBody);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
    LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceWithFileTemplateFix);
    if (template != null && template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceWithFileTemplateFix, editFileTemplateFix};
  }
}
