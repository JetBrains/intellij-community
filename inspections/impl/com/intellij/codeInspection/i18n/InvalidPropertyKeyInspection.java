/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.ExtensionPoints;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
public class InvalidPropertyKeyInspection extends LocalInspectionTool {

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return CodeInsightBundle.message("inspection.unresolved.property.key.reference.name");
  }

  @NotNull
  public String getShortName() {
    return "UnresolvedPropertyKey";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkElement(method, manager);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer, manager);
      if (descriptors != null) {
        result.addAll(Arrays.asList(descriptors));
      }
    }

    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) return checkElement(initializer, manager);

    if (field instanceof PsiEnumConstant) {
      return checkElement(((PsiEnumConstant)field).getArgumentList(), manager);
    }
    return null;
  }

  @Nullable private static ProblemDescriptor[] checkElement(PsiElement element, final InspectionManager manager) {
    UnresolvedPropertyVisitor visitor = new UnresolvedPropertyVisitor(manager);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, boolean isOnTheFly) {
    final Object[] fileCheckingInspections = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INVALID_PROPERTY_KEY_INSPECTION_TOOL).getExtensions();
    for(Object obj: fileCheckingInspections) {
      FileCheckingInspection inspection = (FileCheckingInspection) obj;
      ProblemDescriptor[] descriptors = inspection.checkFile(file, manager, isOnTheFly);
      if (descriptors != null) {
        return descriptors;
      }
    }

    return null;
  }

  private static class UnresolvedPropertyVisitor extends PsiRecursiveElementVisitor {
    private InspectionManager myManager;
    private List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();


    public UnresolvedPropertyVisitor(final InspectionManager manager) {
      myManager = manager;
    }

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argList = aClass.getArgumentList();
      if (argList != null) {
        argList.accept(this);
      }
    }

    public void visitClass(PsiClass aClass) {}

    public void visitField(PsiField field) {}

    public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String key = (String)value;
      if (isComputablePropertyExpression(expression)) return;
      Ref<String> resourceBundleName = new Ref<String>();
      if (!I18nUtil.isValidPropertyReference(expression, key, resourceBundleName)) {
        final String description = CodeInsightBundle.message("inspection.unresolved.property.key.reference.message", key);
        final String bundleName = resourceBundleName.get();
        final List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(bundleName, expression);
        final ProblemDescriptor problem = myManager.createProblemDescriptor(expression,
                                                                            description,
                                                                            new CreatePropertyFix(expression, key, propertiesFiles),
                                                                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        myProblems.add(problem);
      }
      else if (expression.getParent() instanceof PsiNameValuePair) {
        PsiNameValuePair nvp = (PsiNameValuePair) expression.getParent();
        if (Comparing.equal(nvp.getName(), AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
          PropertiesReferenceManager manager = expression.getProject().getComponent(PropertiesReferenceManager.class);
          Module module = ModuleUtil.findModuleForPsiElement(expression);
          if (module != null) {
            List<PropertiesFile> propFiles = manager.findPropertiesFiles(module, key);
            if (propFiles.isEmpty()) {
              final String description = CodeInsightBundle.message("inspection.invalid.resource.bundle.reference", key);
              final ProblemDescriptor problem = myManager.createProblemDescriptor(expression,
                                                                                  description,
                                                                                  (LocalQuickFix)null,
                                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
              myProblems.add(problem);
            }
          }
        }
      }
    }

    private static boolean isComputablePropertyExpression(PsiExpression expression) {
      while (expression != null && expression.getParent() instanceof PsiParenthesizedExpression) expression = (PsiExpression)expression.getParent();
      return expression != null && expression.getParent() instanceof PsiExpression;
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }
}
