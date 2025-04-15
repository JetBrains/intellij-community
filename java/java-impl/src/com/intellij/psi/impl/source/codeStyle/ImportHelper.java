// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.jsp.JspSpiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.impl.source.resolve.ResolveClassUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.NotNullList;
import com.siyeh.ig.psiutils.ImportUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ImportHelper {
  private static final Logger LOG = Logger.getInstance(ImportHelper.class);

  private final JavaCodeStyleSettings mySettings;
  private static final @NonNls String JAVA_LANG_PACKAGE = "java.lang";

  public ImportHelper(@NotNull JavaCodeStyleSettings settings) {
    mySettings = settings;
  }

  /**
   * @deprecated Use {@link #ImportHelper(JavaCodeStyleSettings)} instead. The instance of JavaCodeStyleSettings
   * can be obtained using {@link JavaCodeStyleSettings#getInstance(PsiFile)} method.
   */
  @Deprecated(forRemoval = true)
  public ImportHelper(@NotNull CodeStyleSettings settings) {
    mySettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
  }

  @Nullable("null means no need to replace the import list because they are the same")
  PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
    return prepareOptimizeImportsResult(file, Predicates.alwaysTrue());
  }

  /**
   * @param filter pretend some references do not exist, so the corresponding imports may be deleted
   * @return the import list to replace with, or null when there's no need to replace the import list because they are the same
   */
  public @Nullable PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file, @NotNull Predicate<? super Import> filter) {
    PsiImportList oldList = file.getImportList();
    if (oldList == null) return null;

    // Java parser works in a way that comments may be included to the import list, e.g.:
    //     import a;
    //     /* comment */
    //     import b;
    // We want to preserve those comments then.
    List<PsiElement> nonImports = new NotNullList<>();
    // Note: this array may contain "<packageOrClassName>.*" for unresolved imports!
    List<Import> imports =
      collectNamesToImport(file, nonImports)
        .stream()
        .filter(filter)
        .sorted(Comparator.comparing(o -> o.name()))
        .collect(Collectors.toList());

    SortedImportItems items = sortItemsWithModulesAccordingToSettings(imports, mySettings);
    List<Import> resultList = items.imports();
    Map<String, Boolean> classesOrPackagesToImportOnDemand = new HashMap<>();
    List<PsiImportModuleStatement> previousModuleStatements = collectModuleImports(file, mySettings);
    Map<String, PsiImportModuleStatement> moduleStatementMap = collectNamesImportedByModules(file, previousModuleStatements, resultList);
    collectOnDemandImports(resultList, mySettings, classesOrPackagesToImportOnDemand, moduleStatementMap);

    MultiMap<String, String> conflictingMemberNames = new MultiMap<>();
    for (Import anImport : resultList) {
      if (anImport.isStatic()) {
        conflictingMemberNames.putValue(StringUtil.getShortName(anImport.name()), StringUtil.getPackageName(anImport.name()));
      }
    }

    for (String methodName : conflictingMemberNames.keySet()) {
      Collection<String> collection = conflictingMemberNames.get(methodName);
      if (!classesOrPackagesToImportOnDemand.keySet().containsAll(collection)) {
        for (String name : collection) {
          classesOrPackagesToImportOnDemand.remove(name);
        }
      }
    }

    ImportUtils.ImplicitImportChecker checker = ImportUtils.createImplicitImportChecker(file);
    Set<String> classesToUseSingle = findSingleImports(file, resultList, classesOrPackagesToImportOnDemand.keySet(),
                                                       moduleStatementMap.keySet(), checker);
    Set<String> toReimport = calculateOnDemandImportConflicts(file, classesOrPackagesToImportOnDemand, moduleStatementMap.values(),
                                                              moduleStatementMap.keySet());
    classesToUseSingle.addAll(toReimport);

    try {
      boolean onDemandFirst = mySettings.isLayoutOnDemandImportFromSamePackageFirst();
      StringBuilder text =
        buildImportListText(resultList,
                            classesOrPackagesToImportOnDemand.keySet(),
                            classesToUseSingle,
                            checker,
                            moduleStatementMap,
                            onDemandFirst,
                            items.moduleIndex());
      for (PsiElement nonImport : nonImports) {
        text.append("\n").append(nonImport.getText());
      }
      String ext = JavaFileType.INSTANCE.getDefaultExtension();
      PsiFileFactory factory = PsiFileFactory.getInstance(file.getProject());
      PsiJavaFile dummyFile = (PsiJavaFile)factory.createFileFromText("_Dummy_." + ext, JavaLanguage.INSTANCE, text, false, false);
      PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(dummyFile, file.getLanguageLevel());
      PsiFileFactoryImpl.markGenerated(dummyFile);
      CodeStyle.reformatWithFileContext(dummyFile, file);

      PsiImportList newImportList = dummyFile.getImportList();
      assert newImportList != null : dummyFile.getText();
      if (oldList.isReplaceEquivalent(newImportList)) return null;
      return newImportList;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  /**
   * Collects the names of classes that are imported by modules specified implicitly in the given Java file and in the import list.
   *
   * @param file       the Java file for which imported class names are being collected.
   * @param statements a list of import module statements that specify the modules from which classes are imported.
   * @param list       a list of import objects representing the imports from which class names need to be collected.
   * @return a map of class names and used module imports.
   */
  private static @NotNull Map<String, PsiImportModuleStatement> collectNamesImportedByModules(@NotNull PsiJavaFile file,
                                                                                     @NotNull List<PsiImportModuleStatement> statements,
                                                                                     @NotNull List<Import> list) {
    List<PsiImportStatementBase> implicitImports = ImportsUtil.getAllImplicitImports(file);
    List<PsiImportModuleStatement> moduleImports =
      new ArrayList<>(ContainerUtil.filterIsInstance(implicitImports, PsiImportModuleStatement.class));
    moduleImports.addAll(statements);

    Map<String, PsiImportModuleStatement> usedClasses = new HashMap<>();
    for (Import anImport : list) {
      if (anImport.isStatic) continue;
      String qualifiedName = anImport.name();
      String referencePackageName = StringUtil.getPackageName(qualifiedName);
      String referenceShortName = StringUtil.getShortName(qualifiedName);
      if (referencePackageName.isEmpty() || referenceShortName.isEmpty()) continue;
      for (PsiImportModuleStatement statement : moduleImports) {
        PsiPackageAccessibilityStatement importedPackage = statement.findImportedPackage(referencePackageName);
        if (importedPackage == null) continue;
        PsiJavaCodeReferenceElement packageReference = importedPackage.getPackageReference();
        if (packageReference == null) continue;
        PsiElement resolved = packageReference.resolve();
        if (!(resolved instanceof PsiPackage psiPackage)) continue;
        if (!psiPackage.containsClassNamed(referenceShortName)) continue;
        usedClasses.put(anImport.name, statement);
      }
    }
    return usedClasses;
  }

  /**
   * Collects the module import statements from the specified Java file, considering the code style settings, to insert in the new import list
   *
   * @param file     the Java file from which module import statements are collected.
   * @param settings the code style settings that determine whether module imports should be preserved.
   * @return a list of module import statements present in the Java file.
   */
  private static @NotNull List<PsiImportModuleStatement> collectModuleImports(@NotNull PsiJavaFile file, JavaCodeStyleSettings settings) {
    if (!settings.isPreserveModuleImports()) return Collections.emptyList();
    PsiImportList importList = file.getImportList();
    if (importList == null) return Collections.emptyList();
    return Arrays.asList(importList.getImportModuleStatements());
  }

  public static void collectOnDemandImports(@NotNull List<Import> resultList,
                                            @NotNull JavaCodeStyleSettings javaCodeStyleSettings,
                                            @NotNull Map<String, Boolean> outClassesOrPackagesToImportOnDemand,
                                            @NotNull Map<String, PsiImportModuleStatement> moduleStatementMap) {
    Object2IntMap<String> packageToCountMap = new Object2IntOpenHashMap<>();
    Object2IntMap<String> classToCountMap = new Object2IntOpenHashMap<>();
    for (Import anImport : resultList) {
      if (!anImport.isStatic() && moduleStatementMap.containsKey(anImport.name)) continue;
      String packageOrClassName = StringUtil.getPackageName(anImport.name());
      if (packageOrClassName.isEmpty()) continue;
      Object2IntMap<String> map = anImport.isStatic() ? classToCountMap : packageToCountMap;
      map.put(packageOrClassName, map.getOrDefault(packageOrClassName, 0) + 1);
    }

    classToCountMap.forEach((className, count) -> {
      if (isToUseImportOnDemand(className, count, true, javaCodeStyleSettings)) {
        outClassesOrPackagesToImportOnDemand.put(className, true);
      }
    });
    packageToCountMap.forEach((packageName, count) -> {
      if (isToUseImportOnDemand(packageName, count, false, javaCodeStyleSettings)) {
        outClassesOrPackagesToImportOnDemand.put(packageName, false);
      }
    });
  }

  private record SortedImportItems(@NotNull List<Import> imports, int moduleIndex) {
  }

  public static @NotNull List<Import> sortItemsAccordingToSettings(@NotNull List<Import> imports,
                                                                   @NotNull JavaCodeStyleSettings settings) {
    return sortItemsWithModulesAccordingToSettings(imports, settings).imports;
  }

  private static @NotNull ImportHelper.SortedImportItems sortItemsWithModulesAccordingToSettings(@NotNull List<Import> imports,
                                                                                                 @NotNull JavaCodeStyleSettings settings) {
    int[] entryForName = ArrayUtil.newIntArray(imports.size());
    PackageEntry[] entries = settings.IMPORT_LAYOUT_TABLE.getEntries();
    int moduleEntryIndex = findEntryIndex("", false, true, entries);
    for (int i = 0; i < imports.size(); i++) {
      Import anImport = imports.get(i);
      entryForName[i] = findEntryIndex(StringUtil.getPackageName(anImport.name()),
                                       settings.LAYOUT_STATIC_IMPORTS_SEPARATELY && anImport.isStatic(), false, entries);
    }

    List<Import> resultList = new ArrayList<>(imports.size());
    int moduleIndex = 0;
    for (int i = 0; i < entries.length; i++) {
      if (i == moduleEntryIndex) {
        moduleIndex = resultList.size();
        continue;
      }
      for (int j = 0; j < imports.size(); j++) {
        if (entryForName[j] == i) {
          resultList.add(imports.get(j));
          imports.set(j, null);
        }
      }
    }
    for (Import name : imports) {
      if (name != null) resultList.add(name);
    }
    return new SortedImportItems(resultList, moduleIndex);
  }

  private static @NotNull Set<String> findSingleImports(@NotNull PsiJavaFile file,
                                                        @NotNull Collection<Import> imports,
                                                        @NotNull Set<String> onDemandImports,
                                                        @NotNull Set<String> namesImportedByModuleStatements,
                                                        @NotNull ImportUtils.ImplicitImportChecker checker) {
    GlobalSearchScope resolveScope = file.getResolveScope();
    String thisPackageName = file.getPackageName();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());

    List<String> onDemandImportsList = new ArrayList<>(onDemandImports);
    List<PsiClass> onDemandElements = ContainerUtil.map(onDemandImportsList, onDemandName -> facade.findClass(onDemandName, resolveScope));
    Set<String> namesToUseSingle = new HashSet<>();
    for (Import anImport : imports) {
      String name = anImport.name();
      String prefix = StringUtil.getPackageName(name);
      if (prefix.isEmpty()) continue;
      boolean isImplicitlyImported = checker.isImplicitlyImported(name, anImport.isStatic());
      if (!onDemandImports.contains(prefix) && !isImplicitlyImported && !namesImportedByModuleStatements.contains(name)) continue;
      String shortName = PsiNameHelper.getShortClassName(name);

      String thisPackageClass = !thisPackageName.isEmpty() ? thisPackageName + "." + shortName : shortName;
      if (facade.findClass(thisPackageClass, resolveScope) != null && facade.findClass(name, resolveScope) != null) {
        namesToUseSingle.add(name);
        continue;
      }
      if (!isImplicitlyImported) {
        String langPackageClass = JAVA_LANG_PACKAGE + "." + shortName; //TODO : JSP!
        if (facade.findClass(langPackageClass, resolveScope) != null && facade.findClass(name, resolveScope) != null) {
          namesToUseSingle.add(name);
          continue;
        }
      }
      PsiResolveHelper resolveHelper = facade.getResolveHelper();

      for (int i = 0; i < onDemandImportsList.size(); i++) {
        String onDemandName = onDemandImportsList.get(i);
        if (prefix.equals(onDemandName)) continue;
        if (anImport.isStatic()) {
          PsiClass aClass = onDemandElements.get(i);
          if (aClass != null) {
            PsiField field = aClass.findFieldByName(shortName, true);
            if (field != null && checkMemberAccessibility(field, resolveHelper, file, aClass, prefix)) {
              namesToUseSingle.add(name);
            }
            else {
              PsiClass inner = aClass.findInnerClassByName(shortName, true);
              if (inner != null && checkMemberAccessibility(inner, resolveHelper, file, aClass, prefix)) {
                namesToUseSingle.add(name);
              }
              else {
                PsiMethod[] methods = aClass.findMethodsByName(shortName, true);
                if (ContainerUtil.exists(methods, psiMethod -> checkMemberAccessibility(psiMethod, resolveHelper, file, aClass, prefix))) {
                  namesToUseSingle.add(name);
                }
              }
            }
          }
        }
        else {
          PsiClass aClass = facade.findClass(onDemandName + "." + shortName, resolveScope);
          if (aClass != null) {
            namesToUseSingle.add(name);
          }
        }
      }
    }

    return namesToUseSingle;
  }

  private static boolean checkMemberAccessibility(@NotNull PsiMember member,
                                                  @NotNull PsiResolveHelper resolveHelper,
                                                  @NotNull PsiFile psiFile,
                                                  @NotNull PsiClass aClass,
                                                  @NotNull @NlsSafe String prefix) {
    if (member.hasModifierProperty(PsiModifier.STATIC) && resolveHelper.isAccessible(member, psiFile, null)) {
      PsiClass containingClass = member.getContainingClass();
      if (containingClass == null) return false;
      for (PsiClass superClass : InheritanceUtil.getSuperClasses(aClass)) {
        if (prefix.equals(superClass.getQualifiedName()) && InheritanceUtil.isInheritorOrSelf(superClass, containingClass, true)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static Set<String> calculateOnDemandImportConflicts(@NotNull PsiJavaFile file,
                                                              @NotNull Map<String, Boolean> onDemandImports,
                                                              @NotNull Collection<PsiImportModuleStatement> usedModuleStatements,
                                                              @NotNull Set<String> namesImportedByModuleStatements) {
    if (file instanceof PsiCompiledElement) return Collections.emptySet();
    List<PsiImportStatementBase> implicitImports = ImportsUtil.getAllImplicitImports(file);
    List<PsiImportModuleStatement> moduleImports =
      new ArrayList<>(ContainerUtil.filterIsInstance(implicitImports, PsiImportModuleStatement.class));
    moduleImports.addAll(usedModuleStatements);

    List<String> onDemands =
      StreamEx.of(implicitImports)
        .filter(implicit -> implicit.isOnDemand() &&
                            !(implicit instanceof PsiImportModuleStatement) && implicit.getImportReference() != null)
        .map(t -> t.getImportReference().getQualifiedName())
        .toMutableList();
    for (String onDemand : onDemandImports.keySet()) {
      if (!onDemands.contains(onDemand)) {
        onDemands.add(onDemand);
      }
    }
    if (moduleImports.isEmpty() && onDemands.size() < 2) return Collections.emptySet();

    // if we have classes x.A, x.B and there is an "import x.*" then classNames = {"x" -> ("A", "B")}
    GlobalSearchScope resolveScope = file.getResolveScope();
    Map<String, Set<String>> classNames = new HashMap<>();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    for (int i = onDemands.size() - 1; i >= 0; i--) {
      String onDemand = onDemands.get(i);
      PsiPackage aPackage = facade.findPackage(onDemand);
      boolean isStatic = ObjectUtils.notNull(onDemandImports.get(onDemand), Boolean.FALSE);
      PsiClass aClass;
      if (aPackage != null) { // import foo.package1.*;
        Set<String> set = Arrays.stream(aPackage.getClasses(resolveScope)).map(PsiClass::getName).collect(Collectors.toSet());
        classNames.put(onDemand, set);
      }
      else if ((aClass = facade.findClass(onDemand, resolveScope)) != null) {  // import static foo.package1.Class1.*;
        if (isStatic) {
          Set<String> set = Arrays.stream(aClass.getInnerClasses())
            .filter(member -> member.hasModifierProperty(PsiModifier.STATIC))
            .map(PsiMember::getName)
            .collect(Collectors.toSet());
          classNames.put(onDemand, set);
        }
        else {
          classNames.put(onDemand, Arrays.stream(aClass.getInnerClasses()).map(PsiClass::getName).collect(Collectors.toSet()));
        }
      }
      else {
        onDemands.remove(i);
      }
    }

    Set<String> conflicts = new HashSet<>();
    for (int i = 0; i < onDemands.size(); i++) {
      String on1 = onDemands.get(i);
      for (int j = i + 1; j < onDemands.size(); j++) {
        String on2 = onDemands.get(j);
        Set<String> intersection = new HashSet<>(classNames.get(on1));
        intersection.retainAll(classNames.get(on2));

        conflicts.addAll(intersection);
      }
    }
    if (moduleImports.isEmpty() && conflicts.isEmpty()) return Collections.emptySet();

    Set<String> result = new HashSet<>();
    String filePackageName = file.getPackageName();
    ImportUtils.ImplicitImportChecker checker = ImportUtils.createImplicitImportChecker(file);
    for (PsiClass aClass : file.getClasses()) {
      // do not visit imports
      aClass.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          if (reference.getQualifier() != null) return;
          JavaResolveResult resolveResult = reference.advancedResolve(false);
          if (!(resolveResult.getElement() instanceof PsiClass psiClass)) return;
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName == null) return;
          String referencePackageName = StringUtil.getPackageName(qualifiedName);
          String referenceShortName = StringUtil.getShortName(qualifiedName);
          //conflict with packages
          boolean hasConflict = conflicts.contains(psiClass.getName());
          if (!hasConflict) {
            //if it is already imported by on demands, there is no conflict with modules
            if (!(PsiUtil.isAvailable(JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS, file) &&
                  classNames.getOrDefault(referencePackageName, Collections.emptySet()).contains(referenceShortName)) ||
                //with PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS conflicts are possible only between modules, other types of imports should be more important
                namesImportedByModuleStatements.contains(qualifiedName)) {
              hasConflict = !moduleImports.isEmpty() &&
                            ImportUtils.hasOnDemandImportConflictWithImports(file, moduleImports, qualifiedName, false, true);
            }
          }
          if (!hasConflict) return;
          //can be visible by inheritance
          if (!(resolveResult.getCurrentFileResolveScope() instanceof PsiImportStatementBase) &&
              !isImplicitlyImported(psiClass, checker)) {
            return;
          }
          //in the same package or in the same class
          if (PsiTreeUtil.isAncestor(file, psiClass, true) ||
              filePackageName.equals(referencePackageName)) {
            return;
          }
          result.add(qualifiedName);
        }
      });
    }
    return result;
  }

  private static @NotNull StringBuilder buildImportListText(@NotNull List<Import> imports,
                                                            @NotNull Set<String> packagesOrClassesToImportOnDemand,
                                                            @NotNull Set<String> namesToUseSingle,
                                                            @NotNull ImportUtils.ImplicitImportChecker implicitImportContext,
                                                            @NotNull Map<String, PsiImportModuleStatement> moduleStatementMap,
                                                            boolean onDemandImportsFirst,
                                                            int moduleIndex) {
    Set<String> importedPackagesOrClasses = new HashSet<>();
    @NonNls StringBuilder buffer = new StringBuilder();

    Set<PsiImportModuleStatement> usedModuleImports = new HashSet<>();
    int indexModuleString = -1;
    for (int i = 0; i < imports.size(); i++) {
      if (i == moduleIndex) {
        indexModuleString = buffer.length();
      }
      Import importedName = imports.get(i);
      String name = importedName.name();
      boolean isStatic = importedName.isStatic();
      String packageOrClassName = StringUtil.getPackageName(name);
      boolean implicitlyImported = implicitImportContext.isImplicitlyImported(name, isStatic);
      if (!implicitlyImported && packagesOrClassesToImportOnDemand.remove(packageOrClassName)) {
        appendImportStatement(packageOrClassName + ".*", isStatic, buffer);
        importedPackagesOrClasses.add(packageOrClassName);
      }
      if (namesToUseSingle.contains(name)) {
        if (!implicitlyImported && !onDemandImportsFirst && importedPackagesOrClasses.contains(packageOrClassName)) {
          buffer.insert(buffer.lastIndexOf("import "), "import " + (isStatic ? "static " : "") + name + ";\n");
        }
        else {
          appendImportStatement(name, isStatic, buffer);
        }
      }
      else if (!implicitlyImported && !importedPackagesOrClasses.contains(packageOrClassName)) {
        if (!moduleStatementMap.containsKey(name)) {
          appendImportStatement(name, isStatic, buffer);
        }
        else {
          usedModuleImports.add(moduleStatementMap.get(name));
        }
      }
    }

    StringBuilder moduleStatements = new StringBuilder();
    usedModuleImports.stream()
      .sorted(Comparator.comparing(m -> {
        PsiJavaModuleReferenceElement reference = m.getModuleReference();
        if (reference == null) return "";
        return reference.getText();
      }))
      .map(m -> m.getText())
      .forEach(importModuleStatement -> {
        moduleStatements.append(importModuleStatement).append("\n");
      });
    if (!moduleStatements.isEmpty()) {
      if (indexModuleString != -1) {
        buffer.insert(indexModuleString, moduleStatements);
      }
      else {
        buffer.append(moduleStatements);
      }
    }

    return buffer;
  }

  private static void appendImportStatement(String name, boolean isStatic, StringBuilder buffer) {
    buffer.append("import ");
    if (isStatic) buffer.append("static ");
    buffer.append(name).append(";\n");
  }

  /**
   * Adds import if it is needed.
   *
   * @return false when the FQN has to be used in code (e.g. when conflicting imports already exist)
   */
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass) {
    return addImport(file, refClass, false);
  }

  private boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass, boolean forceReimport) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    PsiResolveHelper helper = facade.getResolveHelper();

    String className = refClass.getQualifiedName();
    if (className == null) return true;

    if (!ImportFilter.shouldImport(file, className)) {
      return false;
    }
    String packageName = StringUtil.getPackageName(className);
    String shortName = PsiNameHelper.getShortClassName(className);

    PsiImportStatement unusedSingleImport = findUnusedSingleImport(file, shortName, className);
    if (unusedSingleImport != null) {
      unusedSingleImport.delete();
    }

    if (!forceReimport) {
      PsiClass conflictSingleRef = findSingleImportByShortName(file, shortName);
      if (conflictSingleRef != null) {
        return className.equals(conflictSingleRef.getQualifiedName());
      }
    }

    PsiClass curRefClass = helper.resolveReferencedClass(shortName, file);
    if (file.getManager().areElementsEquivalent(refClass, curRefClass)) {
      return true;
    }

    boolean useOnDemand = !packageName.isEmpty();

    //right now supports import on demand only for packages
    //it doesn't support add imports on module
    if (hasImportOnDemand(file, packageName)) {
      useOnDemand = false;
    }

    List<PsiClass> classesToReimport = new ArrayList<>();

    List<PsiJavaCodeReferenceElement> importRefs = getImportsFromPackage(file, packageName);
    if (useOnDemand) {
      if (mySettings.USE_SINGLE_CLASS_IMPORTS &&
          importRefs.size() + 1 < mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND &&
          !mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(packageName)) {
        useOnDemand = false;
      }
      // the name of class we try to import is the same as of the class defined in this package
      if (curRefClass != null) {
        useOnDemand = true;
      }
      // check conflicts
      if (useOnDemand) {
        calcNonStaticClassesToReimport(file, facade, helper, packageName, classesToReimport);
      }
    }

    if (useOnDemand &&
        refClass.getContainingClass() != null &&
        mySettings.isInsertInnerClassImportsFor(PsiNameHelper.getShortClassName(className)) &&
        containsInCurrentPackage(file, curRefClass)) {
      return false;
    }

    if (curRefClass != null) {
      if (!classesToReimport.isEmpty()) {
        return false;
      }
      else {
        useOnDemand = false;
      }
    }

    try {
      PsiImportList importList = file.getImportList();
      assert importList != null : file;
      PsiImportStatement statement =
        useOnDemand ? factory.createImportStatementOnDemand(packageName) : factory.createImportStatement(refClass);
      importList.add(statement);
      if (useOnDemand) {
        for (PsiJavaCodeReferenceElement ref : importRefs) {
          if (ref.getParent() instanceof PsiImportStatement importStatement) {
            if (!ref.isValid()) continue; // todo[dsl] Q?
            if (importStatement.isForeignFileImport()) {
              // we should not optimize imports from include files
              continue;
            }
            classesToReimport.add((PsiClass)ref.resolve());
            importStatement.delete();
          }
          else {
            LOG.error("Expected import statement but got: " + ref.getParent());
          }
        }
      }

      for (PsiClass aClass : classesToReimport) {
        if (aClass != null) {
          addImport(file, aClass, true);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
  }

  private static PsiImportStatement findUnusedSingleImport(@NotNull PsiJavaFile file, @NotNull String shortName, @NotNull String fqName) {
    PsiImportList importList = file.getImportList();
    if (importList != null) {
      for (PsiImportStatement statement : importList.getImportStatements()) {
        PsiJavaCodeReferenceElement ref = statement.getImportReference();
        if (!statement.isOnDemand() && ref != null && shortName.equals(ref.getReferenceName()) &&
            !fqName.equals(statement.getQualifiedName())) {
          PsiElement target = statement.resolve();
          if (target instanceof PsiClass) {
            Collection<PsiReference> all = ReferencesSearch.search(target, new LocalSearchScope(file)).findAll();
            if (all.size() == 1 && PsiTreeUtil.isAncestor(statement, all.iterator().next().getElement(), true)) {
              return statement;
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean containsInCurrentPackage(@NotNull PsiJavaFile file, PsiClass curRefClass) {
    if (curRefClass != null) {
      String curRefClassQualifiedName = curRefClass.getQualifiedName();
      return curRefClassQualifiedName != null && !isImplicitlyImported(curRefClass, file);
    }
    return false;
  }

  private static void calcNonStaticClassesToReimport(@NotNull PsiJavaFile file,
                                                     @NotNull JavaPsiFacade facade,
                                                     @NotNull PsiResolveHelper helper,
                                                     @NotNull String packageName,
                                                     @NotNull List<? super PsiClass> outClassesToReimport) {
    PsiPackage aPackage = facade.findPackage(packageName);
    if (aPackage == null) {
      return;
    }

    List<PsiImportStatementBase> onDemandImports =
      StreamEx.of(file.getImportList() != null ? file.getImportList().getAllImportStatements() : PsiImportStatementBase.EMPTY_ARRAY)
        .append(ImportsUtil.getAllImplicitImports(file))
        .filter(statement -> statement.isOnDemand() && !(statement instanceof PsiImportStaticStatement))
        .toList();

    if (onDemandImports.isEmpty()) return;

    Set<String> importedPackages = new HashSet<>();
    Set<PsiImportModuleStatement> importedModules = new HashSet<>();
    for (PsiImportStatementBase anImport : onDemandImports) {
      if (anImport instanceof PsiImportStatement importStatement) {
        importedPackages.add(importStatement.getQualifiedName());
      }
      else if (anImport instanceof PsiImportModuleStatement importModuleStatement) {
        importedModules.add(importModuleStatement);
      }
    }

    GlobalSearchScope resolveScope = file.getResolveScope();
    Collection<Import> imports = collectNamesToImport(file, new ArrayList<>());
    MultiMap<String, String> namesToImport = MultiMap.createLinked();
    for (Import anImport : imports) {
      if (anImport.isStatic()) continue;
      namesToImport.putValue(ClassUtil.extractClassName(anImport.name()), ClassUtil.extractPackageName(anImport.name()));
    }

    for (String name : namesToImport.keySet()) {
      boolean hasConflict = false;
      String conflictClassName2 = packageName + "." + name;
      PsiClass conflictClass2 = facade.findClass(conflictClassName2, resolveScope);
      if (conflictClass2 == null ||
          !helper.isAccessible(conflictClass2, file, null)) {
        continue;
      }

      for (String packageNameToReimport : namesToImport.get(name)) {
        if (importedPackages.contains(packageNameToReimport)) {
          hasConflict = true;
        }
        //shouldn't filter for demand over module, because it is necessary to check,
        //that class which is imported by module will not be shadowed by new on-demand package import
        for (PsiImportModuleStatement module : importedModules) {
          if (module.findImportedPackage(packageNameToReimport) != null) {
            hasConflict = true;
            break;
          }
        }
        if (hasConflict) {
          PsiClass conflictClass = facade.findClass(packageNameToReimport + "." + name, resolveScope);
          if (conflictClass == null || !helper.isAccessible(conflictClass, file, null)) continue;
          outClassesToReimport.add(conflictClass);
        }
      }
    }
  }

  private static @NotNull List<PsiJavaCodeReferenceElement> getImportsFromPackage(@NotNull PsiJavaFile file, @NotNull String packageName) {
    PsiClass[] refs = file.getSingleClassImports(true);
    List<PsiJavaCodeReferenceElement> array = new ArrayList<>(refs.length);
    for (PsiClass ref1 : refs) {
      String className = ref1.getQualifiedName();
      if (className == null) continue;
      if (StringUtil.getPackageName(className).equals(packageName)) {
        PsiJavaCodeReferenceElement ref = file.findImportReferenceTo(ref1);
        if (ref != null) {
          array.add(ref);
        }
      }
    }
    return array;
  }

  private static PsiClass findSingleImportByShortName(@NotNull PsiJavaFile file, @NotNull String shortClassName) {
    PsiClass[] refs = file.getSingleClassImports(true);
    for (PsiClass ref : refs) {
      String className = ref.getQualifiedName();
      if (className != null && PsiNameHelper.getShortClassName(className).equals(shortClassName)) {
        return ref;
      }
    }
    for (PsiClass aClass : file.getClasses()) {
      String className = aClass.getQualifiedName();
      if (className != null && PsiNameHelper.getShortClassName(className).equals(shortClassName)) {
        return aClass;
      }
    }

    // there maybe a class imported implicitly from the current package
    String packageName = file.getPackageName();
    if (!StringUtil.isEmptyOrSpaces(packageName)) {
      String fqn = packageName + "." + shortClassName;
      PsiClass aClass = JavaPsiFacade.getInstance(file.getProject()).findClass(fqn, file.getResolveScope());
      if (aClass != null) {
        boolean[] foundRef = {false};
        // check if that short name referenced in the file
        file.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            if (foundRef[0]) return;
            super.visitElement(element);
          }

          @Override
          public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
            if (shortClassName.equals(reference.getReferenceName()) &&
                file.getManager().areElementsEquivalent(reference.resolve(), aClass)) {
              foundRef[0] = true;
            }
            super.visitReferenceElement(reference);
          }
        });
        if (foundRef[0]) return aClass;
      }
    }
    return null;
  }

  private static boolean hasImportOnDemand(@NotNull PsiJavaFile file, @NotNull String packageName) {
    return StreamEx.of(ImportsUtil.getAllImplicitImports(file))
      .append(file.getImportList() != null ? file.getImportList().getAllImportStatements() : PsiImportStatementBase.EMPTY_ARRAY)
      .filter(statement -> statement.isOnDemand())
      .anyMatch(statement -> {
        if (statement instanceof PsiImportStatement importStatement) {
          return packageName.equals(importStatement.getQualifiedName());
        }
        if (statement instanceof PsiImportModuleStatement importModuleStatement) {
          return importModuleStatement.findImportedPackage(packageName) != null;
        }
        return false;
      });
  }

  /**
   * Checks if the class with the given fully qualified name is already imported in the specified Java file.
   *
   * @param file               the Java file to check for the import.
   * @param fullyQualifiedName the fully qualified name of the class to check.
   * @return true if the class is already imported, false otherwise.
   */
  public static boolean isAlreadyImported(@NotNull PsiJavaFile file, @NotNull String fullyQualifiedName) {
    return ImportUtils.isAlreadyImported(file, fullyQualifiedName);
  }

  public ASTNode getDefaultAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement) {
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (ref == null) return null;

    int entryIndex = findEntryIndex(statement);
    PsiImportStatementBase[] allStatements = list.getAllImportStatements();
    int[] entries = ArrayUtil.newIntArray(allStatements.length);
    List<PsiImportStatementBase> statements = new ArrayList<>();
    for (int i = 0; i < allStatements.length; i++) {
      PsiImportStatementBase statement1 = allStatements[i];
      int entryIndex1 = findEntryIndex(statement1);
      entries[i] = entryIndex1;
      if (entryIndex1 == entryIndex) {
        statements.add(statement1);
      }
    }

    if (statements.isEmpty()) {
      int index;
      for (index = entries.length - 1; index >= 0; index--) {
        if (entries[index] < entryIndex) break;
      }
      index++;
      return index < entries.length ? SourceTreeToPsiMap.psiElementToTree(allStatements[index]) : null;
    }
    else {
      String refText = ref.getCanonicalText();
      if (statement.isOnDemand()) {
        refText += ".";
      }

      PsiImportStatementBase insertBefore = null;
      PsiImportStatementBase insertAfter = null;
      for (PsiImportStatementBase statement1 : statements) {
        PsiJavaCodeReferenceElement ref1 = statement1.getImportReference();
        if (ref1 == null) {
          continue;
        }
        String refTextThis = ref1.getCanonicalText();
        if (statement1.isOnDemand()) {
          refTextThis += ".";
        }

        int comp = Comparing.compare(refText, refTextThis);
        if (comp < 0 && insertBefore == null) {
          insertBefore = statement1;
        }
        if (comp > 0) {
          insertAfter = statement1;
        }
      }

      if (insertBefore != null) return insertBefore.getNode();
      if (insertAfter != null) return insertAfter.getNode().getTreeNext();
      return null;
    }
  }

  public int getEmptyLinesBetween(@NotNull PsiImportStatementBase statement1, @NotNull PsiImportStatementBase statement2) {
    int index1 = findEntryIndex(statement1);
    int index2 = findEntryIndex(statement2);
    if (index1 == index2) return 0;
    if (index1 > index2) {
      int t = index1;
      index1 = index2;
      index2 = t;
    }
    PackageEntry[] entries = mySettings.IMPORT_LAYOUT_TABLE.getEntries();
    int maxSpace = 0;
    for (int i = index1 + 1; i < index2; i++) {
      if (entries[i] == PackageEntry.BLANK_LINE_ENTRY) {
        int space = 0;
        //noinspection AssignmentToForLoopParameter
        do {
          space++;
        }
        while (entries[++i] == PackageEntry.BLANK_LINE_ENTRY);
        maxSpace = Math.max(maxSpace, space);
      }
    }
    return maxSpace;
  }

  private static boolean isToUseImportOnDemand(@NotNull String packageOrClassName,
                                               int classCount,
                                               boolean isStaticImportNeeded,
                                               @NotNull JavaCodeStyleSettings settings) {
    if (!settings.USE_SINGLE_CLASS_IMPORTS) return true;
    int limitCount = isStaticImportNeeded ? settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND :
                     settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    if (classCount >= limitCount) return true;
    if (packageOrClassName.isEmpty()) return false;
    PackageEntryTable table = settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    return table.contains(packageOrClassName);
  }

  private static int findEntryIndex(@NotNull String packageName, boolean isStatic, boolean isModule, PackageEntry @NotNull [] entries) {
    PackageEntry bestEntry = null;
    int bestEntryIndex = -1;
    int allOtherStaticIndex = -1;
    int allOtherIndex = -1;
    for (int i = 0; i < entries.length; i++) {
      PackageEntry entry = entries[i];
      if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
        allOtherStaticIndex = i;
      }
      if (!isModule && entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY) {
        allOtherIndex = i;
      }
      if (!isModule && entry.isBetterMatchForPackageThan(bestEntry, packageName, isStatic)) {
        bestEntry = entry;
        bestEntryIndex = i;
      }
      if (isModule && entry == PackageEntry.ALL_MODULE_IMPORTS) {
        bestEntry = entry;
        bestEntryIndex = i;
      }
    }
    if (bestEntryIndex == -1 && isStatic && allOtherStaticIndex == -1 && allOtherIndex != -1) {
      // if no layout for static imports specified, put them among all others
      bestEntryIndex = allOtherIndex;
    }
    return bestEntryIndex;
  }

  int findEntryIndex(@NotNull PsiImportStatementBase statement) {
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (statement instanceof PsiImportModuleStatement) {
      return findEntryIndex("",
                            mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY && statement instanceof PsiImportStaticStatement,
                            true,
                            mySettings.IMPORT_LAYOUT_TABLE.getEntries());
    }
    if (ref == null) return -1;
    String packageName = statement.isOnDemand() ? ref.getCanonicalText() : StringUtil.getPackageName(ref.getCanonicalText());
    return findEntryIndex(packageName,
                          mySettings.LAYOUT_STATIC_IMPORTS_SEPARATELY && statement instanceof PsiImportStaticStatement,
                          false,
                          mySettings.IMPORT_LAYOUT_TABLE.getEntries());
  }

  public static boolean hasConflictingOnStaticDemandImport(@NotNull PsiJavaFile file,
                                                           @NotNull PsiClass psiClass,
                                                           @NotNull String referenceName) {
    Collection<Import> resultList = collectNamesToImport(file, new ArrayList<>());
    String qualifiedName = psiClass.getQualifiedName();
    for (Import anImport : resultList) {
      if (anImport.isStatic() &&
          referenceName.equals(StringUtil.getShortName(anImport.name())) &&
          !StringUtil.getPackageName(anImport.name()).equals(qualifiedName)) {
        return true;
      }
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiResolveHelper resolveHelper = facade.getResolveHelper();
    for (Import anImport : resultList) {
      if (anImport.isStatic()) {
        String shortName = StringUtil.getShortName(anImport.name());
        String prefix = StringUtil.getPackageName(anImport.name());
        if (prefix.isEmpty()) continue;
        PsiField field = psiClass.findFieldByName(shortName, true);
        if (field != null &&
            field.hasModifierProperty(PsiModifier.STATIC) &&
            checkMemberAccessibility(field, resolveHelper, file, psiClass, prefix)) {
          if (isOnDemandStaticImported(file, anImport)) {
            return true;
          }
        }
        else {
          PsiClass inner = psiClass.findInnerClassByName(shortName, true);
          if (inner != null &&
              inner.hasModifierProperty(PsiModifier.STATIC) &&
              checkMemberAccessibility(inner, resolveHelper, file, psiClass, prefix)) {
            if (isOnDemandStaticImported(file, anImport)) {
              return true;
            }
          }
          else {
            PsiMethod[] methods = psiClass.findMethodsByName(shortName, true);
            if (ContainerUtil.exists(methods, psiMethod ->
              psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
              checkMemberAccessibility(psiMethod, resolveHelper, file, psiClass, prefix))) {
              if (isOnDemandStaticImported(file, anImport)) {
                return true;
              }
            }
          }
        }
      }
    }

    PsiImportList importList = file.getImportList();
    if (importList == null) return false;
    Set<String> onDemandImportedClasses =
      StreamEx.of(ImportsUtil.getAllImplicitImports(file)).select(PsiImportStaticStatement.class)
        .append(importList.getImportStaticStatements())
        .filter(statement -> statement.isOnDemand())
        .map(statement -> statement.resolveTargetClass())
        .filter(aClass -> aClass != null)
        .map(aClass -> aClass.getQualifiedName())
        .collect(Collectors.toSet());
    String newImport = StringUtil.getQualifiedName(qualifiedName, referenceName);
    Set<String> singleImports = findSingleImports(file, Collections.singletonList(new Import(newImport, true)), onDemandImportedClasses,
                                                  Collections.emptySet(), ImportUtils.createImplicitImportChecker(file));

    return singleImports.contains(newImport);
  }

  private static boolean isOnDemandStaticImported(@NotNull PsiJavaFile file, @NotNull Import anImport) {
    if(!anImport.isStatic()) return false;
    PsiImportList importList = file.getImportList();
    if(importList==null)return false;
    for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
      if(!statement.isOnDemand()) return false;
      String packageName = StringUtil.getPackageName(anImport.name);
      if (statement.getImportReference() != null &&
          packageName.equals(statement.getImportReference().getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  // returns list of (name, isImportStatic) pairs
  private static @NotNull Collection<Import> collectNamesToImport(@NotNull PsiJavaFile file, @NotNull List<? super PsiElement> comments) {
    Set<Import> imports = new HashSet<>();

    JspFile jspFile = JspPsiUtil.getJspFile(file);
    collectNamesToImport(imports, comments, file, jspFile);
    if (jspFile != null) {
      PsiFile[] files = ArrayUtil.mergeArrays(JspSpiUtil.getIncludingFiles(jspFile), JspSpiUtil.getIncludedFiles(jspFile));
      for (PsiFile includingFile : files) {
        PsiFile javaRoot = includingFile.getViewProvider().getPsi(JavaLanguage.INSTANCE);
        if (javaRoot instanceof PsiJavaFile psiJavaFile && file != javaRoot) {
          collectNamesToImport(imports, comments, psiJavaFile, jspFile);
        }
      }
    }

    addUnresolvedImportNames(imports, file);

    return imports;
  }

  private static void collectNamesToImport(@NotNull Set<? super Import> imports,
                                           @NotNull List<? super PsiElement> comments,
                                           @NotNull PsiJavaFile file,
                                           @Nullable PsiFile context) {
    String packageName = file.getPackageName();

    List<PsiFile> roots = file.getViewProvider().getAllFiles();
    for (PsiElement root : roots) {
      addNamesToImport(imports, comments, root, packageName, context);
    }
  }

  private static void addNamesToImport(@NotNull Set<? super Import> imports,
                                       @NotNull List<? super PsiElement> comments,
                                       @NotNull PsiElement scope,
                                       @NotNull String thisPackageName,
                                       @Nullable PsiFile context) {
    if (scope instanceof PsiImportList) return;
    ImportUtils.ImplicitImportChecker checker =
      scope.getContainingFile() instanceof PsiJavaFile javaFile ? ImportUtils.createImplicitImportChecker(javaFile) : null;

    Queue<PsiElement> queue = new ArrayDeque<>();
    queue.add(scope);
    while (!queue.isEmpty()) {
      PsiElement child = queue.remove();
      if (child instanceof PsiImportList) {
        for (PsiElement element = child.getFirstChild(); element != null; element = element.getNextSibling()) {
          ASTNode node = element.getNode();
          if (node == null) {
            continue;
          }
          IElementType elementType = node.getElementType();
          if (!ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(elementType) &&
              !JavaJspElementType.WHITE_SPACE_BIT_SET.contains(elementType)) {
            comments.add(element);
          }
        }
        continue;
      }
      if (child instanceof PsiLiteralExpression) continue;
      ContainerUtil.addAll(queue, child.getChildren());

      for (PsiReference reference : child.getReferences()) {
        PsiJavaCodeReferenceElement referenceElement = null;
        if (!(reference instanceof PsiJavaReference javaReference)) {
          continue;
        }
        if (javaReference instanceof JavaClassReference classReference && classReference.getContextReference() != null) continue;
        if (reference instanceof PsiJavaCodeReferenceElement) {
          referenceElement = (PsiJavaCodeReferenceElement)child;
          if (referenceElement.getQualifier() != null) {
            continue;
          }
          if (reference instanceof PsiJavaCodeReferenceElementImpl refImpl
              && refImpl.getKindEnum(refImpl.getContainingFile()) == PsiJavaCodeReferenceElementImpl.Kind.CLASS_IN_QUALIFIED_NEW_KIND) {
            continue;
          }
        }

        JavaResolveResult resolveResult = javaReference.advancedResolve(false);
        PsiElement refElement = resolveResult.getElement();

        PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
        if (!(currentFileResolveScope instanceof PsiImportStatementBase) && refElement != null) {
          //imported not with imports (implicit or explicit)
          if (!(refElement instanceof PsiClass psiClass && checker != null && isImplicitlyImported(psiClass, checker))) {
            continue;
          }
        }
        if (context != null &&
            refElement != null &&
            ((currentFileResolveScope != null && !currentFileResolveScope.isValid()) ||
             currentFileResolveScope instanceof JspxImportStatement jspxImportStatement &&
             context != jspxImportStatement.getDeclarationFile())) {
          continue;
        }

        if (refElement == null && referenceElement != null) {
          refElement = ResolveClassUtil.resolveClass(referenceElement, referenceElement.getContainingFile()); // might be incomplete code
        }
        if (refElement == null) continue;

        if (referenceElement != null && currentFileResolveScope instanceof PsiImportStaticStatement importStaticStatement) {
          PsiJavaCodeReferenceElement importReference = importStaticStatement.getImportReference();
          if (importReference == null) continue;
          String name = importReference.getCanonicalText();
          if (importStaticStatement.isOnDemand()) {
            String refName = referenceElement.getReferenceName();
            if (refName != null) name = name + "." + refName;
          }
          imports.add(new Import(name, true));
          continue;
        }

        if (refElement instanceof PsiClass psiClass) {
          // Implicitly declared classed are not accessible outside the file, so it is not possible to have import statement on them.
          if (refElement.getParent() instanceof PsiImplicitClass) continue;

          String qName = psiClass.getQualifiedName();
          if (qName == null || hasPackage(qName, thisPackageName)) continue;
          imports.add(new Import(qName, false));
        }
      }
    }
  }

  private static void addUnresolvedImportNames(@NotNull Set<? super Import> namesToImport, @NotNull PsiJavaFile file) {
    PsiImportList importList = file.getImportList();
    PsiImportStatementBase[] imports = importList == null ? PsiImportStatementBase.EMPTY_ARRAY : importList.getAllImportStatements();
    Map<String, Import> unresolvedNames = new HashMap<>();
    @NotNull Set<Import> unresolvedOnDemand = new HashSet<>();
    for (PsiImportStatementBase anImport : imports) {
      PsiJavaCodeReferenceElement ref = anImport.getImportReference();
      if (ref == null) continue;
      JavaResolveResult[] results = ref.multiResolve(false);
      if (results.length == 0) {
        String text = ref.getCanonicalText();
        boolean isStatic = anImport instanceof PsiImportStaticStatement;
        if (anImport.isOnDemand()) {
          unresolvedOnDemand.add(new Import(text + ".*", isStatic));
        }
        else {
          unresolvedNames.put(ref.getReferenceName(), new Import(text, isStatic));
        }
      }
    }

    if (unresolvedNames.isEmpty() && unresolvedOnDemand.isEmpty()) {
      return;
    }

    // do not optimize unresolved imports for things like JSP (IDEA-41814)
    if (file.getViewProvider().getLanguages().size() > 1 && file.getViewProvider().getBaseLanguage() != JavaLanguage.INSTANCE) {
      namesToImport.addAll(unresolvedOnDemand);
      namesToImport.addAll(unresolvedNames.values());
      return;
    }

    boolean[] hasResolveProblem = {false};
    // do not visit imports
    for (PsiClass aClass : file.getClasses()) {
      if (!(aClass instanceof PsiCompiledElement)) {
        aClass.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
            if (reference.getQualifier() == null) {
              String name = reference.getReferenceName();
              Import unresolvedImport = unresolvedNames.get(name);
              if (reference.multiResolve(false).length == 0) {
                hasResolveProblem[0] = true;
                if (unresolvedImport != null &&
                    (IncompleteModelUtil.canBeClassReference(reference) || unresolvedImport.isStatic())) {
                  namesToImport.add(unresolvedImport);
                  unresolvedNames.remove(name);
                  if (unresolvedNames.isEmpty()) return;
                }
              }
            }
            super.visitReferenceElement(reference);
          }
        });
      }
    }
    if (hasResolveProblem[0]) {
      namesToImport.addAll(unresolvedOnDemand);
    }
    // otherwise, optimize out all red on demand imports for green file
  }

  static boolean isImplicitlyImported(@Nullable PsiClass psiClass, @NotNull PsiJavaFile file) {
    if (psiClass == null) return false;
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return false;
    ImportUtils.ImplicitImportChecker checker = ImportUtils.createImplicitImportChecker(file);
    return checker.isImplicitlyImported(qualifiedName, psiClass.hasModifierProperty(PsiModifier.STATIC));
  }

  private static boolean isImplicitlyImported(@Nullable PsiClass psiClass, @NotNull ImportUtils.ImplicitImportChecker checker) {
    if (psiClass == null) return false;
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return false;
    return checker.isImplicitlyImported(qualifiedName, psiClass.hasModifierProperty(PsiModifier.STATIC));
  }

  static boolean hasPackage(@NotNull String className, @NotNull String packageName) {
    if (!className.startsWith(packageName)) return false;
    if (className.length() == packageName.length()) return false;
    if (!packageName.isEmpty() && className.charAt(packageName.length()) != '.') return false;
    return className.indexOf('.', packageName.length() + 1) < 0;
  }

  /**
   * An imported element, e.g. a fully qualified class name.
   * This is an implementation detail, unfortunately public because of JavaFX, don't expose it in public API.
   *
   * @param name     the fully qualified name of the element that should be imported.
   * @param isStatic whether it should be imported statically.
   */
  @ApiStatus.Internal
  public record Import(@NotNull String name, boolean isStatic) {
  }
}