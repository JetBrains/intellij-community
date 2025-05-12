/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.util.ImportsUtil.getAllImplicitImports;

public final class ImportUtils {

  private ImportUtils() {}

  public static void addImportIfNeeded(@NotNull PsiClass aClass, @NotNull PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) {
      return;
    }
    final PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) {
      if (PsiTreeUtil.isAncestor(javaFile, aClass, true)) {
        return;
      }
    }
    else if (PsiTreeUtil.isAncestor(outerClass, context, true) && ClassUtils.isInsideClassBody(context, outerClass)) {
      return;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return;
    }
    final String containingPackageName = javaFile.getPackageName();
    final @NonNls String packageName = ClassUtil.extractPackageName(qualifiedName);

    if (containingPackageName.equals(packageName) || importList.findSingleClassImportStatement(qualifiedName) != null) {
      return;
    }
    OnDemandImportConflict conflict = null;
    ImplicitImportChecker implicitImportChecker = createImplicitImportChecker(javaFile);
    if (implicitImportChecker.isImplicitlyImported(qualifiedName, false)) {
      conflict = findOnDemandImportConflict(qualifiedName, javaFile);
      if (implicitImportChecker.isImplicitlyImported(qualifiedName, false, conflict)) {
        return;
      }
    }
    if (importList.findOnDemandImportStatement(packageName) != null) {
      if (conflict == null) {
        conflict = findOnDemandImportConflict(qualifiedName, javaFile);
      }
      if (!conflict.hasConflictForOnDemand()) return;
    }
    if (ContainerUtil.exists(importList.getImportModuleStatements(),
                             moduleStatement -> moduleStatement.findImportedPackage(packageName) != null)) {
      if (conflict == null) {
        conflict = findOnDemandImportConflict(qualifiedName, javaFile);
      }
      if (conflict== OnDemandImportConflict.NO_CONFLICTS) return;
    }
    if (hasExactImportConflict(qualifiedName, javaFile)) {
      return;
    }
    final PsiImportStatement importStatement = JavaPsiFacade.getElementFactory(importList.getProject()).createImportStatement(aClass);
    importList.add(importStatement);
  }

  private static boolean hasAccessibleMemberWithName(@NotNull PsiClass containingClass,
                                                     @NotNull String memberName, @NotNull PsiElement context) {
    final PsiField field = containingClass.findFieldByName(memberName, true);
    if (field != null && PsiUtil.isAccessible(field, context, null)) {
      return true;
    }
    final PsiMethod[] methods = containingClass.findMethodsByName(memberName, true);
    for (PsiMethod method : methods) {
      if (PsiUtil.isAccessible(method, context, null)) {
        return true;
      }
    }
    final PsiClass innerClass = containingClass.findInnerClassByName(memberName, true);
    return innerClass != null && PsiUtil.isAccessible(innerClass, context, null);
  }

  public static boolean nameCanBeImported(@NotNull String fqName, @NotNull PsiElement context) {
    final PsiFile containingFile = context.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile file)) {
      return false;
    }
    PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    while (containingClass != null) {
      final String shortName = ClassUtil.extractClassName(fqName);
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
      if (resolveHelper.resolveAccessibleReferencedVariable(shortName, context) != null) {
        return false;
      }
      final PsiClass[] innerClasses = containingClass.getAllInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (innerClass.hasModifierProperty(PsiModifier.PRIVATE) && !containingClass.equals(innerClass.getContainingClass())) {
          // private inner class from super class
          continue;
        }
        if (innerClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !ClassUtils.inSamePackage(innerClass, containingClass)) {
          // package local inner class from super class in a different package
          continue;
        }
        if (shortName.equals(innerClass.getName())) {
          return fqName.equals(innerClass.getQualifiedName());
        }
      }
      if (shortName.equals(containingClass.getName())) {
        return fqName.equals(containingClass.getQualifiedName());
      }
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
    }
    if (hasExactImportConflict(fqName, file)) {
      return false;
    }
    if (hasOnDemandImportConflict(fqName, file, true, true, true) &&
        !isAlreadyImported(file, fqName)
    ) {
      return false;
    }
    if (containsConflictingReference(file, fqName)) {
      return false;
    }
    if (containsConflictingClassName(fqName, file)) {
      return false;
    }
    return !containsConflictingTypeParameter(fqName, context);
  }

  /**
   * Checks if the class with the given fully qualified name is already imported in the specified Java file.
   *
   * @param file the Java file to check for the import.
   * @param fullyQualifiedName the fully qualified name of the class to check.
   * @return true if the class is already imported, false otherwise.
   */
  public static boolean isAlreadyImported(@NotNull PsiJavaFile file, @NotNull String fullyQualifiedName) {
    String className = extractClassName(file, fullyQualifiedName);

    Project project = file.getProject();
    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(project);

    PsiClass psiClass = resolveHelper.resolveReferencedClass(className, file);
    return psiClass != null && fullyQualifiedName.equals(psiClass.getQualifiedName());
  }

  private static @NotNull String extractClassName(@NotNull PsiJavaFile file, @NotNull String fullyQualifiedName) {
    for (PsiClass aClass : file.getClasses()) {
      String outerClassName = aClass.getQualifiedName();
      if (outerClassName != null && fullyQualifiedName.startsWith(outerClassName)) {
        return fullyQualifiedName.substring(outerClassName.lastIndexOf('.') + 1);
      }
    }

    return ClassUtil.extractClassName(fullyQualifiedName);
  }

  private static boolean containsConflictingTypeParameter(String fqName, PsiElement context) {
    final String shortName = ClassUtil.extractClassName(fqName);
    PsiElement parent = context.getParent();
    while (parent != null && !(parent instanceof PsiFile)) {
      if (parent instanceof PsiTypeParameterListOwner) {
        for (PsiTypeParameter parameter : ((PsiTypeParameterListOwner)parent).getTypeParameters()) {
          if (shortName.equals(parameter.getName())) {
            return true;
          }
        }
      }
      parent = parent.getParent();
    }
    return false;
  }

  private static boolean containsConflictingClassName(String fqName, PsiJavaFile file) {
    final String shortName = ClassUtil.extractClassName(fqName);
    final PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (shortName.equals(aClass.getName()) && !fqName.equals(aClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasExactImportConflict(String fqName, PsiJavaFile file) {
    final PsiImportList imports = file.getImportList();
    if (imports == null) {
      return false;
    }
    final PsiImportStatement[] importStatements = imports.getImportStatements();
    final String shortName = ClassUtil.extractClassName(fqName);
    final String dottedShortName = '.' + shortName;
    for (final PsiImportStatement importStatement : importStatements) {
      if (importStatement.isOnDemand()) {
        continue;
      }
      final String importName = importStatement.getQualifiedName();
      if (importName == null) {
        return false;
      }
      if (!importName.equals(fqName) && importName.endsWith(dottedShortName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Represents the conflicts that can occur with on-demand imports in a Java file.
   * This record captures whether there is a conflict with on-demand imports involving
   * class names and module imports.
   */
  public enum OnDemandImportConflict {
    NO_CONFLICTS(false, false),
    ON_DEMAND_IMPORT_CONFLICTS(true, false),
    MODULE_CONFLICTS(false, true),
    ON_DEMAND_AND_MODULE_CONFLICT(true, true);

    private final boolean myConflictForOnDemand;
    private final boolean myConflictForModules;

    OnDemandImportConflict(boolean conflictForOnDemand, boolean conflictForModules) {
      myConflictForOnDemand = conflictForOnDemand;
      myConflictForModules = conflictForModules;
    }

    /**
     * @return true if there is a conflict related to on-demand imports, false otherwise.
     */
    public boolean hasConflictForOnDemand() {
      return myConflictForOnDemand;
    }

    /**
     * @return true if there is a conflict related to module imports, false otherwise.
     */
    public boolean hasConflictForModules() {
      return myConflictForModules;
    }

    private static OnDemandImportConflict from(boolean conflictForOnDemand, boolean conflictForModules) {
      if (conflictForOnDemand && conflictForModules) {
        return ON_DEMAND_AND_MODULE_CONFLICT;
      }
      else if (conflictForOnDemand) {
        return ON_DEMAND_IMPORT_CONFLICTS;
      }
      else if (conflictForModules) {
        return MODULE_CONFLICTS;
      }
      else {
        return NO_CONFLICTS;
      }
    }
  }


  /**
   * Finds conflicts related to on-demand imports in the context of a given fully qualified name.
   * This method checks for potential conflicts with existing imports in the specified context,
   * considering both class-level on-demand imports and module-related imports.
   *
   * @param fqName the fully qualified name of the class or package to check for import conflicts
   * @param context the PSI element representing the context within which to check for import conflicts
   * @return an OnDemandImportConflicts object that contains information about whether there are
   *         conflicts for on-demand imports and module imports
   */
  public static @NotNull ImportUtils.OnDemandImportConflict findOnDemandImportConflict(@NotNull String fqName, @NotNull PsiElement context) {
    if(PsiUtil.isAvailable(JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS, context)) {
      boolean onDemandConflict = hasOnDemandImportConflict(fqName, context, false, true, false);
      boolean moduleConflict = hasOnDemandImportConflict(fqName, context, false, false, true);
      return OnDemandImportConflict.from(onDemandConflict, moduleConflict);
    }
    boolean demandImportConflict = hasOnDemandImportConflict(fqName, context, false, true, true);
    return OnDemandImportConflict.from(demandImportConflict, demandImportConflict) ;
  }

  /**
   * @deprecated use {@link ImportUtils#findOnDemandImportConflict(String, PsiElement)}
   */
  @Deprecated(forRemoval = true)
  public static boolean hasOnDemandImportConflict(@NotNull String fqName, @NotNull PsiElement context) {
    OnDemandImportConflict conflict = findOnDemandImportConflict(fqName, context);
    return conflict != OnDemandImportConflict.NO_CONFLICTS;
  }
  /**
   * @param strict if strict is true this method checks if the conflicting
   *               class which is imported is actually used in the file. If it isn't the
   *               on demand import can be overridden with an exact import for the fqName
   *               without breaking stuff.
   */
  private static boolean hasOnDemandImportConflict(@NotNull String fqName,
                                                   @NotNull PsiElement context,
                                                   boolean strict,
                                                   boolean considerOnDemand,
                                                   boolean considerModules) {
    final PsiFile containingFile = context.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile javaFile)) {
      return false;
    }
    final PsiImportList imports = javaFile.getImportList();
    if (imports == null) {
      return false;
    }
    final List<PsiImportStatementBase> importStatements =
      ContainerUtil.append(getAllImplicitImports(javaFile), imports.getAllImportStatements());
    ThreeState state = hasOnDemandImportConflictWithImports(javaFile, importStatements, fqName, strict, considerOnDemand, considerModules);
    if (state != ThreeState.UNSURE) return state.toBoolean();
    return considerOnDemand && hasDefaultImportConflict(fqName, javaFile);
  }

  /**
   * Checks if there is an on-demand import conflict between the fully qualified name (fqName)
   * and the specified import statements in the given Java file.
   *
   * @param javaFile the Java file to check for import conflicts.
   * @param importStatements the list of import statements to check against.
   * @param fqName the fully qualified name to check for conflicts.
   * @param checkOnDemand check conflicts for all on-demand statements, excluding module import statements.
   * @param checkModules check conflicts for module imports.
   * @return true if there is an on-demand import conflict, false otherwise.
   */
  public static boolean hasOnDemandImportConflictWithImports(@NotNull PsiJavaFile javaFile,
                                                             @NotNull List<? extends PsiImportStatementBase> importStatements,
                                                             @NotNull String fqName,
                                                             boolean checkOnDemand,
                                                             boolean checkModules) {
    return hasOnDemandImportConflictWithImports(javaFile, importStatements, fqName, false, checkOnDemand, checkModules) == ThreeState.YES;
  }

  private static ThreeState hasOnDemandImportConflictWithImports(@NotNull PsiJavaFile javaFile,
                                                                 @NotNull List<? extends PsiImportStatementBase> importStatements,
                                                                 @NotNull String fqName,
                                                                 boolean strict,
                                                                 boolean checkOnDemand,
                                                                 boolean checkModules) {
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    @Nullable PsiClass importedClass = JavaPsiFacade.getInstance(javaFile.getProject()).findClass(packageName, javaFile.getResolveScope());
    for (final PsiImportStatementBase importStatement : importStatements) {
      if (!importStatement.isOnDemand()) {
        continue;
      }
      if (checkModules && importStatement instanceof PsiImportModuleStatement moduleStatement) {
        //can't process, let's assume that we have conflict because it is safe
        if (DumbService.isDumb(javaFile.getProject())) return ThreeState.YES;
        Ref<Boolean> result = new Ref<>(null);
        PsiJavaModule module = moduleStatement.resolveTargetModule();
        if (module == null) return ThreeState.UNSURE;
        JavaModuleGraphUtil.JavaModuleScope scope = JavaModuleGraphUtil.JavaModuleScope.moduleWithTransitiveScope(module);
        if (scope == null) return ThreeState.UNSURE;
        PsiShortNamesCache.getInstance(javaFile.getProject()).processClassesWithName(shortName, currentClass -> {
          if (!currentClass.hasModifierProperty(PsiModifier.PUBLIC)) return true;
          if (currentClass.getContainingClass() != null) return true;
          String qualifiedName = currentClass.getQualifiedName();
          if (qualifiedName == null) return true;
          String currentPackage = ClassUtil.extractPackageName(qualifiedName);
          if (currentPackage == null) return true;
          if (currentPackage.equals(packageName)) return true;
          PsiPackageAccessibilityStatement aPackage = moduleStatement.findImportedPackage(currentPackage);
          if (aPackage == null || aPackage.getPackageReference() == null) return true;
          PsiElement resolvedPackage = aPackage.getPackageReference().resolve();
          if (resolvedPackage instanceof PsiPackage currentResolvedPackage) {
            ThreeState state = hasOnDemandImportConflictInPackage(currentResolvedPackage, shortName, javaFile, fqName, strict);
            if (state != ThreeState.UNSURE) {
              result.set(state.toBoolean());
              return false;
            }
          }
          return true;
        }, scope, null);
        if (result.get() != null) return ThreeState.fromBoolean(result.get());
      }
      if (!checkOnDemand) continue;
      final PsiJavaCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final String packageText = importReference.getText();
      if (packageText.equals(packageName)) {
        continue;
      }
      final PsiElement element = importReference.resolve();
      if (element instanceof PsiPackage aPackage) {
        ThreeState state = hasOnDemandImportConflictInPackage(aPackage, shortName, javaFile, fqName, strict);
        if (state != ThreeState.UNSURE) return state;
      }
      else if (element instanceof PsiClass aClass) {
        final PsiClass innerClass = aClass.findInnerClassByName(shortName, true);
        if (importStatement instanceof PsiImportStatement) {
          if (innerClass != null && PsiUtil.isAccessible(innerClass, javaFile, null)) {
            final String qualifiedName = innerClass.getQualifiedName();
            if (!fqName.equals(qualifiedName) && (!strict || containsConflictingReference(javaFile, qualifiedName))) {
              return ThreeState.YES;
            }
          }
        }
        else {
          if (innerClass != null && PsiUtil.isAccessible(innerClass, javaFile, null) &&
              innerClass.hasModifierProperty(PsiModifier.STATIC)) {
            final String qualifiedName = innerClass.getQualifiedName();
            if (!fqName.equals(qualifiedName) && (!strict || memberReferenced(innerClass, javaFile))) {
              return ThreeState.YES;
            }
          }
          final PsiField field = aClass.findFieldByName(shortName, true);
          if (field != null && PsiUtil.isAccessible(field, javaFile, null) && field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
              continue;
            }
            final String qualifiedName = containingClass.getQualifiedName() + '.' + field.getName();
            if (!fqName.equals(qualifiedName) && (!strict || memberReferenced(field, javaFile))) {
              return ThreeState.YES;
            }
          }
          final PsiMethod[] methods = aClass.findMethodsByName(shortName, true);
          for (PsiMethod method : methods) {
            if (!PsiUtil.isAccessible(method, javaFile, null) || !method.hasModifierProperty(PsiModifier.STATIC)) {
              continue;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
              continue;
            }
            final String qualifiedName = containingClass.getQualifiedName() + '.' + method.getName();
            boolean theSameMethod = fqName.equals(qualifiedName)
                                    ||
                                    //can reference through inheritances
                                    (!containingClass.isInterface() && importedClass != null &&
                                     importedClass.isInheritor(containingClass, true));
            if (theSameMethod) {
              break;
            }
            if (!strict || memberReferenced(method, javaFile)) {
              return ThreeState.YES;
            }
          }
        }
      }
    }
    return ThreeState.UNSURE;
  }

  private static ThreeState hasOnDemandImportConflictInPackage(@NotNull PsiPackage aPackage,
                                                               @NotNull String shortName,
                                                               @NotNull PsiFile containingFile,
                                                               @NotNull String fqName,
                                                               boolean strict) {
    if (!strict) {
      if (aPackage.findClassByShortName(shortName, containingFile.getResolveScope()).length > 0) {
        return ThreeState.YES;
      }
    }
    else {
      final PsiClass[] classes = aPackage.findClassByShortName(shortName, containingFile.getResolveScope());
      for (final PsiClass aClass : classes) {
        final String qualifiedClassName = aClass.getQualifiedName();
        if (qualifiedClassName == null || fqName.equals(qualifiedClassName)) {
          return ThreeState.UNSURE;
        }
        return ThreeState.fromBoolean(containsConflictingReference(containingFile, qualifiedClassName));
      }
    }
    return ThreeState.UNSURE;
  }

  private static boolean hasDefaultImportConflict(String fqName, PsiJavaFile file) {
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    final String filePackageName = file.getPackageName();
    if (filePackageName.equals(packageName)) {
      return false;
    }
    final Project project = file.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiPackage filePackage = psiFacade.findPackage(filePackageName);
    return filePackage != null && filePackage.hasClassWithShortName(shortName, file.getResolveScope());
  }

  /**
   * @return true, if a static import was created or already present. False, if a static import is not possible.
   */
  public static boolean addStaticImport(@NotNull String qualifierClass, @NonNls @NotNull String memberName, @NotNull PsiElement context) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (ClassUtils.isInsideClassBody(context, containingClass)) {
      if (InheritanceUtil.isInheritor(containingClass, qualifierClass)) {
        return true;
      }
      if (hasAccessibleMemberWithName(containingClass, memberName, context)) {
        return false;
      }
    }
    final PsiFile contextFile = context.getContainingFile();
    if (!(contextFile instanceof PsiJavaFile javaFile)) {
      return false;
    }
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final PsiImportStatementBase existingImportStatement = importList.findSingleImportStatement(memberName);
    if (existingImportStatement != null) {
      if (existingImportStatement instanceof PsiImportStaticStatement importStaticStatement) {
        if (!memberName.equals(importStaticStatement.getReferenceName())) {
          return false;
        }
        final PsiClass targetClass = importStaticStatement.resolveTargetClass();
        return targetClass != null && qualifierClass.equals(targetClass.getQualifiedName());
      }
      return false;
    }
    final PsiImportStaticStatement onDemandImportStatement = findOnDemandImportStaticStatement(importList, qualifierClass);
    if (onDemandImportStatement != null &&
        //check only on demands
        !hasOnDemandImportConflict(qualifierClass + '.' + memberName, javaFile, false, true, false)) {
      return true;
    }
    final Project project = context.getProject();
    final GlobalSearchScope scope = context.getResolveScope();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass aClass = psiFacade.findClass(qualifierClass, scope);
    if (aClass == null || !PsiUtil.isAccessible(aClass, contextFile, null) ||
        !hasAccessibleMemberWithName(aClass, memberName, contextFile)) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }
    final List<PsiImportStaticStatement> imports = getMatchingImports(importList, qualifiedName);
    final int onDemandCount = JavaFileCodeStyleFacade.forContext(contextFile).getNamesCountToUseImportOnDemand();
    final PsiElementFactory elementFactory = psiFacade.getElementFactory();
    if (imports.size() + 1 < onDemandCount) {
      importList.add(elementFactory.createImportStaticStatement(aClass, memberName));
    }
    else {
      for (PsiImportStaticStatement importStatement : imports) {
        importStatement.delete();
      }
      importList.add(elementFactory.createImportStaticStatement(aClass, "*"));
    }
    return true;
  }

  private static @Nullable PsiImportStaticStatement findOnDemandImportStaticStatement(PsiImportList importList, String qualifierClass) {
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    List<PsiImportStaticStatement> additionalOnDemandImports = new ArrayList<>();
    if (importList.getContainingFile() instanceof PsiJavaFile javaFile) {
      additionalOnDemandImports = ContainerUtil.filterIsInstance(getAllImplicitImports(javaFile), PsiImportStaticStatement.class);
    }
    for (PsiImportStaticStatement importStaticStatement : ContainerUtil.append(additionalOnDemandImports, importStaticStatements)) {
      if (!importStaticStatement.isOnDemand()) {
        continue;
      }
      final PsiJavaCodeReferenceElement importReference = importStaticStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final String text = importReference.getText();
      if (qualifierClass.equals(text)) {
        return importStaticStatement;
      }
    }
    return null;
  }

  private static List<PsiImportStaticStatement> getMatchingImports(@NotNull PsiImportList importList, @NotNull String className) {
    final List<PsiImportStaticStatement> imports = new ArrayList<>();
    List<PsiImportStaticStatement> additionalOnDemandImports = new ArrayList<>();
    if (importList.getContainingFile() instanceof PsiJavaFile javaFile) {
      additionalOnDemandImports = ContainerUtil.filterIsInstance(getAllImplicitImports(javaFile), PsiImportStaticStatement.class);
    }
    for (PsiImportStaticStatement staticStatement : ContainerUtil.append(additionalOnDemandImports,
                                                                         importList.getImportStaticStatements())) {
      final PsiClass psiClass = staticStatement.resolveTargetClass();
      if (psiClass == null) {
        continue;
      }
      if (!className.equals(psiClass.getQualifiedName())) {
        continue;
      }
      imports.add(staticStatement);
    }
    return imports;
  }

  public static boolean isStaticallyImported(@NotNull PsiMember member, @NotNull PsiElement context) {
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return false;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      return false;
    }
    final PsiFile psiFile = context.getContainingFile();
    if (!(psiFile instanceof PsiJavaFile javaFile)) {
      return false;
    }
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final String memberName = member.getName();
    if (memberName == null) {
      return false;
    }
    if (hasImplicitStaticImport(javaFile, memberClass.getQualifiedName() + "." + memberName)) return true;
    final PsiImportStatementBase existingImportStatement = importList.findSingleImportStatement(memberName);
    if (existingImportStatement instanceof PsiImportStaticStatement importStaticStatement) {
      final PsiClass importClass = importStaticStatement.resolveTargetClass();
      if (InheritanceUtil.isInheritorOrSelf(importClass, memberClass, true)) {
        return true;
      }
    }
    final String memberClassName = memberClass.getQualifiedName();
    if (memberClassName == null) {
      return false;
    }
    final PsiImportStaticStatement onDemandImportStatement = findOnDemandImportStaticStatement(importList, memberClassName);
    if (onDemandImportStatement != null) {
      if (!hasOnDemandImportConflict(memberClassName + '.' + memberName, javaFile, false, true, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasImplicitStaticImport(@NotNull PsiJavaFile file, @NotNull String name) {
    return createImplicitImportChecker(file).isImplicitlyImported(name, true);
  }


  @Contract("_ -> new")
  public static @NotNull ImportUtils.ImplicitImportChecker createImplicitImportChecker(@NotNull PsiJavaFile file) {
    return new ImplicitImportChecker(file);
  }

  /**
   * The ImplicitImportChecker class provides functionality to check if a given import is implicitly
   * imported in the context of a PsiJavaFile. It tracks both static member imports and package imports.
   */
  public static class ImplicitImportChecker {

    private final @NotNull Map<String, PsiImportStaticStatement> myStaticImportStatements = new HashMap<>();
    private final @NotNull Set<PsiImportModuleStatement> myModulesStatements = new HashSet<>();
    private final @NotNull Map<String, PsiImportStatement> myPackageStatements = new HashMap<>();

    private final @NotNull Map<ImplicitCacheItem, Boolean> myCache = new HashMap<>();
    private ImplicitImportChecker(@NotNull PsiJavaFile file) {
      for (PsiImportStatementBase anImport : getAllImplicitImports(file)) {
        if(anImport instanceof PsiImportStaticStatement staticStatement) {
          PsiJavaCodeReferenceElement importReference = staticStatement.getImportReference();
          if (importReference == null) continue;
          String referenceName = importReference.getQualifiedName();
          if (referenceName == null) continue;
          myStaticImportStatements.put(staticStatement.isOnDemand() ? referenceName : StringUtil.getPackageName(referenceName),
                                       staticStatement);
        }
        else if (anImport instanceof PsiImportModuleStatement moduleStatement) {
          myModulesStatements.add(moduleStatement);
        }else if(anImport instanceof PsiImportStatement packageStatement) {
          String qualifiedName = packageStatement.getQualifiedName();
          if(qualifiedName == null || !packageStatement.isOnDemand()) continue;
          myPackageStatements.put(qualifiedName, packageStatement);
        }
      }
    }

    record ImplicitCacheItem(String qName, boolean isStatic,
                             @NotNull ImportUtils.OnDemandImportConflict conflicts) { }

    public boolean isImplicitlyImported(String qName, boolean isStatic) {
      return isImplicitlyImported(qName, isStatic, OnDemandImportConflict.NO_CONFLICTS);
    }

    public boolean isImplicitlyImported(String qName, boolean isStatic,
                                        @NotNull ImportUtils.OnDemandImportConflict conflicts) {
      ImplicitCacheItem cacheItem = new ImplicitCacheItem(qName, isStatic, conflicts);
      Boolean result = myCache.get(cacheItem);
      if (result != null) return result;
      String packageOrClassName = StringUtil.getPackageName(qName);
      String className = ClassUtil.extractClassName(qName);
      if (!isStatic) {
        if (conflicts == OnDemandImportConflict.NO_CONFLICTS) {
          for (PsiImportModuleStatement psiImportModuleStatement : myModulesStatements) {
            PsiPackageAccessibilityStatement importedPackage = psiImportModuleStatement.findImportedPackage(packageOrClassName);
            if (importedPackage == null) continue;
            PsiJavaCodeReferenceElement reference = importedPackage.getPackageReference();
            if (reference == null) continue;
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiPackage psiPackage) {
              if (psiPackage.containsClassNamed(className)) {
                myCache.put(cacheItem, true);
                return true;
              }
            }
          }
        }
        if (!conflicts.hasConflictForOnDemand() && myPackageStatements.containsKey(packageOrClassName)) {
          myCache.put(cacheItem, true);
          return true;
        }
      }
      else {
        if (!conflicts.hasConflictForOnDemand()) {
          PsiImportStaticStatement psiImportStaticStatement = myStaticImportStatements.get(packageOrClassName);
          if (psiImportStaticStatement != null) {
            if (psiImportStaticStatement.isOnDemand()) {
              myCache.put(cacheItem, true);
              return true;
            }
            PsiJavaCodeReferenceElement reference = psiImportStaticStatement.getImportReference();
            if (reference == null) {
              myCache.put(cacheItem, false);
              return false;
            }
            String qualifiedName = reference.getQualifiedName();
            boolean equals = qName.equals(qualifiedName);
            myCache.put(cacheItem, equals);
            return equals;
          }
        }
      }
      myCache.put(cacheItem, false);
      return false;
    }
  }

  private static boolean memberReferenced(PsiMember member, PsiElement context) {
    final MemberReferenceVisitor visitor = new MemberReferenceVisitor(member);
    context.accept(visitor);
    return visitor.isReferenceFound();
  }

  public static boolean isReferenceCorrectWithoutQualifier(@NotNull PsiJavaCodeReferenceElement reference, @NotNull PsiMember member) {
    final String referenceName = reference.getReferenceName();
    if (referenceName == null) {
      return false;
    }
    final Project project = reference.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
    PsiElement newTarget = null;
    if (member instanceof PsiMethod) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)reference.getParent().copy();
      final PsiElement qualifier = methodCallExpression.getMethodExpression().getQualifier();
      assert qualifier != null;
      qualifier.delete();
      newTarget = methodCallExpression.resolveMethod();
    }
    else if (member instanceof PsiField) {
      newTarget = resolveHelper.resolveAccessibleReferencedVariable(referenceName, reference);
    }
    else if (member instanceof PsiClass) {
      newTarget = resolveHelper.resolveReferencedClass(referenceName, reference);
    }
    return member.equals(newTarget);
  }

  public static boolean isAlreadyStaticallyImported(PsiJavaCodeReferenceElement reference) {
    if (reference instanceof PsiMethodReferenceExpression) return false;
    PsiJavaCodeReferenceElement qualifier = ObjectUtils.tryCast(reference.getQualifier(), PsiJavaCodeReferenceElement.class);
    if (qualifier == null) return false;
    if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) return false;
    if (GenericsUtil.isGenericReference(reference, qualifier)) return false;
    final PsiMember member = ObjectUtils.tryCast(reference.resolve(), PsiMember.class);
    if (member == null) return false;
    if (!(qualifier.resolve() instanceof PsiClass)) return false;
    return isStaticallyImported(member, reference) && isReferenceCorrectWithoutQualifier(reference, member);
  }

  private static class MemberReferenceVisitor extends JavaRecursiveElementWalkingVisitor {
    private final PsiMember[] members;
    private boolean referenceFound;

    MemberReferenceVisitor(PsiMember member) {
      members = new PsiMember[]{member};
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      if (referenceFound) {
        return;
      }
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      for (PsiMember member : members) {
        if (member.equals(target)) {
          referenceFound = true;
          return;
        }
      }
    }

    boolean isReferenceFound() {
      return referenceFound;
    }
  }

  /**
   * @return true, if the element contains a reference to a different class than fullyQualifiedName but which has the same class name
   */
  private static boolean containsConflictingReference(PsiFile element, String fullyQualifiedName) {
    final Map<String, Boolean> cachedValue =
      CachedValuesManager.getCachedValue(element, () -> new CachedValueProvider.Result<>(Collections.synchronizedMap(new HashMap<>()),
                                                                                         PsiModificationTracker.MODIFICATION_COUNT));
    Boolean conflictingRef = cachedValue.get(fullyQualifiedName);
    if (conflictingRef != null) {
      return conflictingRef.booleanValue();
    }

    final ConflictingClassReferenceVisitor visitor = new ConflictingClassReferenceVisitor(fullyQualifiedName);
    element.accept(visitor);
    conflictingRef = visitor.isConflictingReferenceFound();
    cachedValue.put(fullyQualifiedName, conflictingRef);

    return conflictingRef.booleanValue();
  }

  private static class ConflictingClassReferenceVisitor extends JavaRecursiveElementWalkingVisitor {

    private final String name;
    private final String fullyQualifiedName;
    private boolean referenceFound;

    ConflictingClassReferenceVisitor(String fullyQualifiedName) {
      name = ClassUtil.extractClassName(fullyQualifiedName);
      this.fullyQualifiedName = fullyQualifiedName;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (referenceFound) return;
      super.visitElement(element);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      if (referenceFound) {
        return;
      }
      super.visitReferenceElement(reference);

      if (reference.getQualifier() != null) return;

      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass aClass) || element instanceof PsiTypeParameter) {
        return;
      }
      final String testClassName = aClass.getName();
      final String testClassQualifiedName = aClass.getQualifiedName();
      if (testClassQualifiedName == null || testClassName == null ||
          testClassQualifiedName.equals(fullyQualifiedName) || !testClassName.equals(name)) {
        return;
      }
      referenceFound = true;
    }

    boolean isConflictingReferenceFound() {
      return referenceFound;
    }
  }
}