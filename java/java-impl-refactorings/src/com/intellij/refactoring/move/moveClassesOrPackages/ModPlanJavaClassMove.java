// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.model.SymbolPointer;
import com.intellij.modcommand.FutureVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPointersKt;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl.RenamedClassForm;
import com.intellij.psi.impl.source.resolve.RenamedClassView;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.ModPlanOptimizeImportsListener;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanRefactoring;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions;
import com.intellij.refactoring.modplan.move.ModPlanMoveProvider.Analysis;
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Java files that one request of the Java move moves, and the analysis of their move. A file moves as a whole, and the classes of the
 * file move with it.
 */
final class ModPlanJavaClassMove {
  private final @NotNull List<PsiJavaFile> myWholeFiles;

  private ModPlanJavaClassMove(@NotNull List<PsiJavaFile> wholeFiles) {
    myWholeFiles = wholeFiles;
  }

  /** @return the move of the subjects; null if a subject is gone or is not a Java file */
  static @Nullable ModPlanJavaClassMove of(@NotNull List<SymbolPointer> subjects) {
    var wholeFiles = new ArrayList<PsiJavaFile>();
    for (var subject : subjects) {
      if (!(subject.element() instanceof PsiJavaFile file)) return null;
      wholeFiles.add(file);
    }
    return new ModPlanJavaClassMove(List.copyOf(wholeFiles));
  }

  /** @return the file subjects, which move as a whole */
  @NotNull List<PsiJavaFile> wholeFiles() {
    return myWholeFiles;
  }

  /**
   * Gives the edits and the conflicts of the move of the whole files to {@code targetPackage}.
   * <p>
   * The edits come in this order:
   * <ol>
   *   <li>the package statements of the moved files;</li>
   *   <li>the edits of the imports;</li>
   *   <li>the replacement of the non-code usages and the binding of the references to the moved classes;</li>
   *   <li>the bindings of the moved files to the classes of their old package that stay.</li>
   * </ol>
   * The package statements come first, so that the copy of a moved file has its new package when the other edits write it.
   * A short reference in a moved file to a top-level class of its old package that stays gets an import: the new package lacks it.
   * The file keeps the reference as it is if it has an import on demand of the old package.
   * A short reference to another class gets an import as well if the target package has a class with the same simple name.
   * With {@link ModPlanMoveOptions#SEARCH_FOR_REFERENCES} off, the edits cover the package statements and the moved classes only.
   * <p>
   * The edits of the imports decide what the copies cannot decide, as a copy does not see the moved classes at their new places.
   * The examples move {@code a.Foo} to the package {@code c}.
   * A single import of the moved class in a file of the target package goes away. The copy keeps an import that does not resolve.
   * <pre>{@code
   * package c; import a.Foo; class Bar { Foo f; }
   * // move a.Foo to c
   * package c; class Bar { Foo f; }
   * }</pre>
   * An import on demand of the target package stays if a short reference to the moved class needs it.
   * The copy takes it for redundant.
   * <pre>{@code
   * package q; import c.*; class Use { a.Foo f; }
   * // move a.Foo to c
   * package q; import c.*; class Use { Foo f; }
   * }</pre>
   * An import on demand of the old package goes away if only the moved classes used it.
   * The copy still resolves them through it.
   * <pre>{@code
   * package q; import a.*; class Use { Foo f; }
   * // move a.Foo to c
   * package q; import c.Foo; class Use { Foo f; }
   * }</pre>
   * A class of the target package takes a short reference of the moved file if the file does not import the class of the reference.
   * <pre>{@code
   * package a; import x.*; class Foo { Other o; }  // c.Other exists
   * // move a.Foo to c
   * package c; import x.Other; class Foo { Other o; }
   * }</pre>
   * <p>
   * The search and the conflicts are the ones of {@link MoveClassesOrPackagesProcessor}, see
   * {@link MoveClassesOrPackagesUtil#findUsagesInElements}, {@link MoveClassesOrPackagesUtil#collectConflicts} and
   * {@link MoveClassesOrPackagesUtil#extractAffectedUsages}.
   * <p>
   * The analysis refuses with {@link ModPlanAnalysisResult.RefusedReason#NOT_APPLICABLE} a target with no source root.
   * It refuses with {@link ModPlanAnalysisResult.RefusedReason#NOT_SUPPORTED} a foreign usage.
   * It refuses the same way a reference that the write cannot find again in the copy.
   * It refuses the same way a move that changes {@code module-info.java}, and a usage whose referenced element is not a moved class.
   * It refuses the same way a move to a new directory if an {@code exports} or {@code opens} names the package of a moved class.
   * A foreign usage is one for which {@link ModPlanJavaMoveUtil#isForeignUsage} is true.
   */
  @NotNull Analysis analyze(@NotNull String targetPackage, @NotNull VirtualFile targetDirectory, @NotNull ModPlanRequest request) {
    var project = request.project();
    var manager = PsiManager.getInstance(project);
    var sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(existingAncestorOf(targetDirectory));
    if (sourceRoot == null) return refusal(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, null);
    var target = new PackageWrapper(manager, targetPackage);
    var existingTarget = targetDirectory instanceof FutureVirtualFile ? null : manager.findDirectory(targetDirectory);
    var destination = existingTarget != null ? new SingleSourceRootMoveDestination(target, existingTarget)
                                             : new AutocreatingSingleSourceRootMoveDestination(target, sourceRoot);
    var movedClasses = new ArrayList<PsiClass>();
    for (var file : myWholeFiles) {
      movedClasses.addAll(Arrays.asList(file.getClasses()));
    }
    var movedElements = movedClasses.toArray(PsiElement.EMPTY_ARRAY);

    var found = usagesOf(movedElements, target, request);
    var usages = new ArrayList<>(found.allUsages());
    var detected = new MultiMap<PsiElement, String>();
    var moduleDetector = ModuleInfoUsageDetector.createModifyUsageInstance(project, movedElements, destination);
    MoveClassesOrPackagesUtil.collectConflicts(usages, detected, movedElements, target, destination, moduleDetector);
    if (ContainerUtil.exists(usages, usage -> usage instanceof ModifyModuleStatementUsageInfo) ||
        !moduleDetector.createUsageInfosForNewlyCreatedDirs().isEmpty() ||
        existingTarget == null && namesPackageOfMovedClass(movedClasses)) {
      return refusal(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, myWholeFiles.getFirst());
    }
    var affectedUsages = MoveClassesOrPackagesUtil.extractAffectedUsages(usages, found.usagesToSkip());
    return editsOf(affectedUsages, movedClasses, targetPackage, ModPlanJavaMoveUtil.conflictsOf(detected));
  }

  private static @NotNull MoveClassesOrPackagesUtil.UsagesContext usagesOf(PsiElement @NotNull [] movedElements,
                                                                          @NotNull PackageWrapper target,
                                                                          @NotNull ModPlanRequest request) {
    var optionValues = request.optionValues();
    if (!optionValues.get(ModPlanMoveOptions.SEARCH_FOR_REFERENCES)) {
      return new MoveClassesOrPackagesUtil.UsagesContext(List.of(), List.of());
    }
    var found = MoveClassesOrPackagesUtil.findUsagesInElements(movedElements, request.searchScope().get(),
                                                               optionValues.get(ModPlanMoveOptions.SEARCH_IN_COMMENTS),
                                                               optionValues.get(ModPlanMoveOptions.SEARCH_FOR_TEXT), target);
    var kept = ContainerUtil.filter(found.allUsages(), usage -> {
      var element = usage.getElement();
      if (element == null) return false;
      var place = SmartPointersKt.createSmartFileRange(element, usage.getRangeInElement());
      return place == null || !ModPlanRefactoring.isExcluded(request, place);
    });
    return new MoveClassesOrPackagesUtil.UsagesContext(kept, found.usagesToSkip());
  }

  private @NotNull Analysis editsOf(UsageInfo @NotNull [] usages,
                                    @NotNull List<PsiClass> movedClasses,
                                    @NotNull String targetPackage,
                                    @NotNull ModPlanConflicts detectedConflicts) {
    var views = new HashMap<PsiElement, RenamedClassView>();
    for (var aClass : movedClasses) {
      views.put(aClass, RenamedClassView.moved(aClass, targetPackage, myWholeFiles));
    }
    var edits = new ArrayList<ModPlanEdit>();
    for (var file : myWholeFiles) {
      var edit = packageStatementEdit(file, targetPackage);
      if (edit != null) edits.add(edit);
    }
    var packageEditCount = edits.size();
    var places = new ArrayList<SmartPsiFileRange>();
    var conflicts = ModPlanConflicts.builder();
    var imports = new ImportEdits(targetPackage, movedClasses);
    var references = new ArrayList<UsageInfo>();
    var viewsOfReferences = new IdentityHashMap<UsageInfo, RenamedClassView>();
    for (var usage : usages) {
      var element = usage.getElement();
      if (element == null) continue;
      var view = usage instanceof MoveRenameUsageInfo moveUsage ? views.get(moveUsage.getReferencedElement()) : null;
      if (view == null) return refusal(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, element);
      var place = SmartPointersKt.createSmartFileRange(element, usage.getRangeInElement());
      if (place != null) places.add(place);
      if (usage instanceof NonCodeUsageInfo nonCode) {
        if (place != null) edits.add(new ModPlanEdit.ReplaceText(place, nonCode.newText));
        conflicts.changedNonCodeUsage(element, nonCode.getRangeInElement());
      }
      else if (ModPlanJavaMoveUtil.isForeignUsage(usage)) {
        return refusal(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, element);
      }
      else if (!imports.collect(usage, view)) {
        references.add(usage);
        viewsOfReferences.put(usage, view);
      }
    }
    var bindings = ModPlanRenameUtil.bindingEdits(references, viewsOfReferences::get);
    if (bindings == null) return refusal(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, myWholeFiles.getFirst());
    edits.addAll(bindings);
    var stayingBindings = bindingsToStayingClasses(targetPackage);
    if (stayingBindings == null) return refusal(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, myWholeFiles.getFirst());
    edits.addAll(stayingBindings);
    edits.addAll(packageEditCount, imports.edits());
    return new Analysis(new ModPlanAnalysisResult.Found(edits, conflicts.build().merge(detectedConflicts)), places, null);
  }

  private static @Nullable ModPlanEdit packageStatementEdit(@NotNull PsiJavaFile file, @NotNull String targetPackage) {
    if (file.getPackageName().equals(targetPackage) || PsiUtil.isModuleFile(file) ||
        ContainerUtil.exists(file.getClasses(), aClass -> aClass instanceof PsiImplicitClass)) {
      return null;
    }
    return ModPlanEdit.update(file, copy -> copy.setPackageName(targetPackage));
  }

  private @Nullable List<ModPlanEdit> bindingsToStayingClasses(@NotNull String targetPackage) {
    var references = new ArrayList<UsageInfo>();
    var targets = new IdentityHashMap<UsageInfo, PsiClass>();
    var targetPackageHasClass = new HashMap<String, Boolean>();
    for (var file : myWholeFiles) {
      var oldPackage = file.getPackageName();
      var importList = file.getImportList();
      var importsOldPackage = importList != null && importList.findOnDemandImportStatement(oldPackage) != null;
      for (var reference : PsiTreeUtil.findChildrenOfType(file, PsiJavaCodeReferenceElement.class)) {
        if (reference.isQualified() || PsiTreeUtil.getParentOfType(reference, PsiImportList.class) != null ||
            !(reference.resolve() instanceof PsiClass target) || target instanceof PsiTypeParameter ||
            target.getContainingClass() != null || myWholeFiles.contains(target.getContainingFile()) ||
            !(target.getContainingFile() instanceof PsiJavaFile targetFile)) {
          continue;
        }
        var targetFilePackage = targetFile.getPackageName();
        var staysInOldPackage = !importsOldPackage && targetFilePackage.equals(oldPackage);
        if (!staysInOldPackage && !(target.getQualifiedName() != null && !targetFilePackage.equals(targetPackage) &&
                                    targetPackageHasClass.computeIfAbsent(StringUtil.notNullize(target.getName()),
                                                                          name -> hasClass(file.getProject(), targetPackage, name)))) {
          continue;
        }
        var usage = new UsageInfo(reference);
        references.add(usage);
        targets.put(usage, target);
      }
    }
    return ModPlanRenameUtil.bindingEdits(references, targets::get);
  }

  private static boolean hasClass(@NotNull Project project, @NotNull String packageName, @NotNull String name) {
    var scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(StringUtil.getQualifiedName(packageName, name), scope) != null;
  }

  private static boolean namesPackageOfMovedClass(@NotNull List<PsiClass> movedClasses) {
    for (var aClass : movedClasses) {
      var module = JavaModuleGraphUtil.findDescriptorByElement(aClass);
      if (module == null || !(aClass.getContainingFile() instanceof PsiJavaFile file)) continue;
      var packageName = file.getPackageName();
      for (var statement : module.getExports()) {
        if (packageName.equals(statement.getPackageName())) return true;
      }
      for (var statement : module.getOpens()) {
        if (packageName.equals(statement.getPackageName())) return true;
      }
    }
    return false;
  }

  private @NotNull String packageAfterMove(@NotNull PsiFile file, @NotNull String targetPackage) {
    if (!(file instanceof PsiJavaFile javaFile)) return "";
    return myWholeFiles.contains(javaFile) ? targetPackage : javaFile.getPackageName();
  }

  private static @NotNull VirtualFile existingAncestorOf(@NotNull VirtualFile directory) {
    var result = directory;
    while (result instanceof FutureVirtualFile) {
      result = result.getParent();
    }
    return result;
  }

  private static @NotNull Analysis refusal(@NotNull ModPlanAnalysisResult.RefusedReason reason, @Nullable PsiElement place) {
    return new Analysis(new ModPlanAnalysisResult.Refused(reason), List.of(), place);
  }

  private final class ImportEdits {
    private final @NotNull String myTargetPackage;
    private final @NotNull List<PsiClass> myMovedClasses;
    private final @NotNull Set<PsiImportStatementBase> myDeletedImports = new LinkedHashSet<>();
    private final @NotNull Set<PsiImportStatementBase> myKeptImports = new LinkedHashSet<>();
    private final @NotNull Set<PsiImportStatementBase> myOnDemandImportsOfOldPackage = new LinkedHashSet<>();
    private final @NotNull Set<PsiElement> myBoundReferences = new HashSet<>();

    ImportEdits(@NotNull String targetPackage, @NotNull List<PsiClass> movedClasses) {
      myTargetPackage = targetPackage;
      myMovedClasses = movedClasses;
    }

    boolean collect(@NotNull UsageInfo usage, @NotNull RenamedClassView view) {
      var element = usage.getElement();
      var file = usage.getFile();
      if (!(element instanceof PsiJavaCodeReferenceElement reference) || !(file instanceof PsiJavaFile javaFile)) return false;
      if (reference.getParent() instanceof PsiImportStatement statement && !statement.isOnDemand() &&
          packageAfterMove(file, myTargetPackage).equals(myTargetPackage)) {
        myDeletedImports.add(statement);
        return true;
      }
      if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) return false;
      myBoundReferences.add(reference);
      var importList = javaFile.getImportList();
      if (importList == null) return false;
      var qualifiedName = view.getToRename().getQualifiedName();
      var oldPackage = StringUtil.getPackageName(StringUtil.notNullize(qualifiedName));
      var oldImport = importList.findOnDemandImportStatement(oldPackage);
      if (oldImport != null && !myWholeFiles.contains(javaFile)) myOnDemandImportsOfOldPackage.add(oldImport);
      if (!myWholeFiles.contains(javaFile) && !javaFile.getPackageName().equals(myTargetPackage) &&
          PsiJavaCodeReferenceElementImpl.formAfterRename(reference, view) == RenamedClassForm.SHORT &&
          qualifiedName != null && importList.findSingleClassImportStatement(qualifiedName) == null) {
        var targetImport = importList.findOnDemandImportStatement(myTargetPackage);
        if (targetImport != null) myKeptImports.add(targetImport);
      }
      return false;
    }

    @NotNull List<ModPlanEdit> edits() {
      var result = new ArrayList<ModPlanEdit>();
      for (var statement : myKeptImports) {
        result.add(ModPlanOptimizeImportsListener.keepImportEdit(statement));
      }
      for (var statement : myOnDemandImportsOfOldPackage) {
        if (!isNeeded(statement)) result.add(ModPlanEdit.delete(statement));
      }
      for (var statement : myDeletedImports) {
        result.add(ModPlanEdit.delete(statement));
      }
      return result;
    }

    private boolean isNeeded(@NotNull PsiImportStatementBase statement) {
      for (var reference : PsiTreeUtil.findChildrenOfType(statement.getContainingFile(), PsiJavaCodeReferenceElement.class)) {
        if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) continue;
        var result = reference.advancedResolve(false);
        if (result.getCurrentFileResolveScope() != statement) continue;
        if (!(result.getElement() instanceof PsiClass aClass && myMovedClasses.contains(aClass) && myBoundReferences.contains(reference))) {
          return true;
        }
      }
      return false;
    }
  }
}