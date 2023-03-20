// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.jsp.JspSpiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
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
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

public final class ImportHelper{
  private static final Logger LOG = Logger.getInstance(ImportHelper.class);

  private final JavaCodeStyleSettings mySettings;
  @NonNls private static final String JAVA_LANG_PACKAGE = "java.lang";

  public ImportHelper(@NotNull JavaCodeStyleSettings settings) {
    mySettings = settings;
  }

  /**
   * @deprecated Use {@link #ImportHelper(JavaCodeStyleSettings)} instead. The instance of JavaCodeStyleSettings
   *             can be obtained using {@link JavaCodeStyleSettings#getInstance(PsiFile)} method.
   */
  @Deprecated(forRemoval = true)
  public ImportHelper(@NotNull CodeStyleSettings settings){
    mySettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
  }

  @Nullable("null means no need to replace the import list because they are the same")
  PsiImportList prepareOptimizeImportsResult(@NotNull final PsiJavaFile file) {
    return prepareOptimizeImportsResult(file, Predicates.alwaysTrue());
  }

  /**
   * @param filter pretend some references do not exist so the corresponding imports may be deleted
   */
  @Nullable("null means no need to replace the import list because they are the same")
  public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file, @NotNull Predicate<? super Pair<String, Boolean>> filter) {
    PsiImportList oldList = file.getImportList();
    if (oldList == null) return null;

    // Java parser works in a way that comments may be included to the import list, e.g.:
    //     import a;
    //     /* comment */
    //     import b;
    // We want to preserve those comments then.
    List<PsiElement> nonImports = new NotNullList<>();
    // Note: this array may contain "<packageOrClassName>.*" for unresolved imports!
    List<Pair<String, Boolean>> names =
      collectNamesToImport(file, nonImports)
        .stream()
        .filter(filter)
        .sorted(Pair.comparingByFirst())
        .collect(Collectors.toList());

    List<Pair<String, Boolean>> resultList = sortItemsAccordingToSettings(names, mySettings);

    final Map<String, Boolean> classesOrPackagesToImportOnDemand = new HashMap<>();
    collectOnDemandImports(resultList, mySettings, classesOrPackagesToImportOnDemand);

    MultiMap<String, String> conflictingMemberNames = new MultiMap<>();
    for (Pair<String, Boolean> pair : resultList) {
      if (pair.second) {
        conflictingMemberNames.putValue(StringUtil.getShortName(pair.first), StringUtil.getPackageName(pair.first));
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

    Set<String> classesToUseSingle = findSingleImports(file, resultList, classesOrPackagesToImportOnDemand.keySet());
    Set<String> toReimport = new HashSet<>();
    calcClassesConflictingViaOnDemandImports(file, classesOrPackagesToImportOnDemand, file.getResolveScope(), toReimport);
    classesToUseSingle.addAll(toReimport);

    try {
      StringBuilder text = buildImportListText(resultList, classesOrPackagesToImportOnDemand.keySet(), classesToUseSingle);
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
    catch(IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public static void collectOnDemandImports(@NotNull List<? extends Pair<String, Boolean>> resultList,
                                            @NotNull JavaCodeStyleSettings settings,
                                            @NotNull Map<String, Boolean> outClassesOrPackagesToImportOnDemand) {
    ObjectIntMap<String> packageToCountMap = new ObjectIntHashMap<>();
    ObjectIntMap<String> classToCountMap = new ObjectIntHashMap<>();
    for (Pair<String, Boolean> pair : resultList) {
      String name = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      String packageOrClassName = getPackageOrClassName(name);
      if (packageOrClassName.isEmpty()) continue;
      ObjectIntMap<String> map = isStatic ? classToCountMap : packageToCountMap;
      map.put(packageOrClassName, map.getOrDefault(packageOrClassName, 0) + 1);
    }

    for (ObjectIntMap.Entry<String> entry : classToCountMap.entries()) {
      String packageOrClassName = entry.getKey();
      int count = entry.getValue();
      if (isToUseImportOnDemand(packageOrClassName, count, true, settings)){
        outClassesOrPackagesToImportOnDemand.put(packageOrClassName, true);
      }
    }
    for (ObjectIntMap.Entry<String> entry : packageToCountMap.entries()) {
      String packageOrClassName = entry.getKey();
      int count = entry.getValue();
      if (isToUseImportOnDemand(packageOrClassName, count, false, settings)){
        outClassesOrPackagesToImportOnDemand.put(packageOrClassName, false);
      }
    }
  }

  public static List<Pair<String, Boolean>> sortItemsAccordingToSettings(List<? extends Pair<String, Boolean>> names, final JavaCodeStyleSettings settings) {
    int[] entryForName = ArrayUtil.newIntArray(names.size());
    PackageEntry[] entries = settings.IMPORT_LAYOUT_TABLE.getEntries();
    for(int i = 0; i < names.size(); i++){
      Pair<String, Boolean> pair = names.get(i);
      String packageName = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      entryForName[i] = findEntryIndex(packageName, isStatic, entries);
    }

    List<Pair<String, Boolean>> resultList = new ArrayList<>(names.size());
    for(int i = 0; i < entries.length; i++){
      for(int j = 0; j < names.size(); j++){
        if (entryForName[j] == i){
          resultList.add(names.get(j));
          names.set(j, null);
        }
      }
    }
    for (Pair<String, Boolean> name : names) {
      if (name != null) resultList.add(name);
    }
    return resultList;
  }

  @NotNull
  private static Set<String> findSingleImports(@NotNull final PsiJavaFile file,
                                               @NotNull Collection<? extends Pair<String, Boolean>> names,
                                               @NotNull final Set<String> onDemandImports) {
    final GlobalSearchScope resolveScope = file.getResolveScope();
    final String thisPackageName = file.getPackageName();
    final Set<String> implicitlyImportedPackages = Set.of(file.getImplicitlyImportedPackages());
    final PsiManager manager = file.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());

    List<String> onDemandImportsList = new ArrayList<>(onDemandImports);
    List<PsiClass> onDemandElements = ContainerUtil.map(onDemandImportsList, onDemandName -> facade.findClass(onDemandName, resolveScope));
    Set<String> namesToUseSingle = new HashSet<>();
    for (Pair<String, Boolean> pair : names) {
      String name = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      String prefix = getPackageOrClassName(name);
      if (prefix.isEmpty()) continue;
      final boolean isImplicitlyImported = implicitlyImportedPackages.contains(prefix);
      if (!onDemandImports.contains(prefix) && !isImplicitlyImported) continue;
      String shortName = PsiNameHelper.getShortClassName(name);

      String thisPackageClass = !thisPackageName.isEmpty() ? thisPackageName + "." + shortName : shortName;
      if (JavaPsiFacade.getInstance(manager.getProject()).findClass(thisPackageClass, resolveScope) != null) {
        namesToUseSingle.add(name);
        continue;
      }
      if (!isImplicitlyImported) {
        String langPackageClass = JAVA_LANG_PACKAGE + "." + shortName; //TODO : JSP!
        if (facade.findClass(langPackageClass, resolveScope) != null) {
          namesToUseSingle.add(name);
          continue;
        }
      }
      PsiResolveHelper resolveHelper = facade.getResolveHelper();

      for (int i = 0; i < onDemandImportsList.size(); i++) {
        String onDemandName = onDemandImportsList.get(i);
        if (prefix.equals(onDemandName)) continue;
        if (isStatic) {
          PsiClass aClass = onDemandElements.get(i);
          if (aClass != null) {

            class AccessibilityChecker {
              boolean checkMember(PsiMember member) {
                if (member.hasModifierProperty(PsiModifier.STATIC) && resolveHelper.isAccessible(member, file, null)) {
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
            }

            PsiField field = aClass.findFieldByName(shortName, true);
            if (field != null && new AccessibilityChecker().checkMember(field)) {
              namesToUseSingle.add(name);
            }
            else {
              PsiClass inner = aClass.findInnerClassByName(shortName, true);
              if (inner != null && new AccessibilityChecker().checkMember(inner)) {
                namesToUseSingle.add(name);
              }
              else {
                PsiMethod[] methods = aClass.findMethodsByName(shortName, true);
                if (Arrays.stream(methods).anyMatch(new AccessibilityChecker()::checkMember)) {
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

  private static void calcClassesConflictingViaOnDemandImports(@NotNull PsiJavaFile file,
                                                               @NotNull Map<String, Boolean> onDemandImports,
                                                               @NotNull GlobalSearchScope resolveScope,
                                                               @NotNull Set<? super String> outNamesToUseSingle) {
    List<String> onDemands = new ArrayList<>(Arrays.asList(file.getImplicitlyImportedPackages()));
    for (String onDemand : onDemandImports.keySet()) {
      if (!onDemands.contains(onDemand)) {
        onDemands.add(onDemand);
      }
    }
    if (onDemands.size() < 2) return;

    // if we have classes x.A, x.B and there is an "import x.*" then classNames = {"x" -> ("A", "B")}
    Map<String, Set<String>> classNames = new HashMap<>();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    for (int i = onDemands.size()-1; i>=0; i--) {
      String onDemand = onDemands.get(i);
      PsiPackage aPackage = facade.findPackage(onDemand);
      boolean isStatic = ObjectUtils.notNull(onDemandImports.get(onDemand), Boolean.FALSE);
      PsiClass aClass;
      if (aPackage != null) { // import foo.package1.*;
        Set<String> set = Arrays.stream(aPackage.getClasses(resolveScope)).map(PsiClass::getName).collect(toSet());
        classNames.put(onDemand, set);
      }
      else if ((aClass = facade.findClass(onDemand, resolveScope)) != null) {  // import static foo.package1.Class1.*;
        if (isStatic) {
          PsiMember[][] membersArray = {aClass.getInnerClasses(), aClass.getMethods(), aClass.getFields()};
          Set<String> set = Arrays.stream(membersArray)
            .flatMap(Arrays::stream)
            .filter(member -> member.hasModifierProperty(PsiModifier.STATIC))
            .map(PsiMember::getName).collect(toSet());
          classNames.put(onDemand, set);
        }
        else {
          classNames.put(onDemand, Arrays.stream(aClass.getInnerClasses()).map(PsiClass::getName).collect(toSet()));
        }
      }
      else {
        onDemands.remove(i);
      }
    }

    final Set<String> conflicts = new HashSet<>();
    for (int i = 0; i < onDemands.size(); i++) {
      String on1 = onDemands.get(i);
      for (int j = i+1; j < onDemands.size(); j++) {
        String on2 = onDemands.get(j);
        Set<String> intersection = new HashSet<>(classNames.get(on1));
        intersection.retainAll(classNames.get(on2));

        conflicts.addAll(intersection);
      }
    }
    if (!conflicts.isEmpty() && !(file instanceof PsiCompiledElement)) {
      String packageName = file.getPackageName();
      file.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          if (reference.getQualifier() != null) return;
          PsiElement element = reference.resolve();
          if (element instanceof PsiClass && conflicts.contains(((PsiClass)element).getName())) {
            String fqn = ((PsiClass)element).getQualifiedName();
            if (fqn != null &&
                !PsiTreeUtil.isAncestor(file, element, true) &&
                !packageName.equals(StringUtil.getPackageName(fqn))) {
              outNamesToUseSingle.add(fqn);
            }
          }
        }
      });
    }
  }

  @NotNull
  private static StringBuilder buildImportListText(@NotNull List<? extends Pair<String, Boolean>> names,
                                                   @NotNull final Set<String> packagesOrClassesToImportOnDemand,
                                                   @NotNull final Set<String> namesToUseSingle) {
    final Set<Pair<String, Boolean>> importedPackagesOrClasses = new HashSet<>();
    @NonNls final StringBuilder buffer = new StringBuilder();
    for (Pair<String, Boolean> pair : names) {
      String name = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      String packageOrClassName = getPackageOrClassName(name);
      final boolean implicitlyImported = JAVA_LANG_PACKAGE.equals(packageOrClassName);
      boolean useOnDemand = implicitlyImported || packagesOrClassesToImportOnDemand.contains(packageOrClassName);
      final Pair<String, Boolean> current = Pair.create(packageOrClassName, isStatic);
      if (namesToUseSingle.remove(name)) {
        if (useOnDemand && importedPackagesOrClasses.contains(current)) {
          buffer.insert(buffer.lastIndexOf("import"), "import " + (isStatic ? "static " : "") + name + ";\n");
          continue;
        }
        useOnDemand = false;
      }
      if (useOnDemand && (importedPackagesOrClasses.contains(current) || implicitlyImported)) continue;
      buffer.append("import ");
      if (isStatic) buffer.append("static ");
      if (useOnDemand) {
        importedPackagesOrClasses.add(current);
        buffer.append(packageOrClassName);
        buffer.append(".*");
      }
      else {
        buffer.append(name);
      }
      buffer.append(";\n");
    }

    for (String remainingSingle : namesToUseSingle) {
      buffer.append("import ");
      buffer.append(remainingSingle);
      buffer.append(";\n");
    }

    return buffer;
  }

  /**
   * Adds import if it is needed.
   * @return false when the FQ-name have to be used in code (e.g. when conflicting imports already exist)
   */
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass) {
    return addImport(file, refClass, false);
  }

  private boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass, boolean forceReimport){
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    PsiResolveHelper helper = facade.getResolveHelper();

    String className = refClass.getQualifiedName();
    if (className == null) return true;

    if (!ImportFilter.shouldImport(file, className)) {
      return false;
    }
    String packageName = getPackageOrClassName(className);
    String shortName = PsiNameHelper.getShortClassName(className);

    findUnusedSingleImport(file, shortName, className).ifPresent(PsiElement::delete);

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

    boolean useOnDemand = true;
    if (packageName.isEmpty()){
      useOnDemand = false;
    }

    PsiElement conflictPackageRef = findImportOnDemand(file, packageName);
    if (conflictPackageRef != null) {
      useOnDemand = false;
    }

    List<PsiClass> classesToReimport = new ArrayList<>();

    List<PsiJavaCodeReferenceElement> importRefs = getImportsFromPackage(file, packageName);
    if (useOnDemand){
      if (mySettings.USE_SINGLE_CLASS_IMPORTS &&
          importRefs.size() + 1 < mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND &&
          !mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(packageName)) {
        useOnDemand = false;
      }
      // name of class we try to import is the same as of the class defined in this package
      if (containsInCurrentPackage(file, curRefClass)) {
        useOnDemand = true;
      }
      // check conflicts
      if (useOnDemand){
        PsiElement[] onDemandRefs = file.getOnDemandImports(true, true);
        List<String> refTexts = new ArrayList<>(onDemandRefs.length);
        for (PsiElement ref : onDemandRefs) {
          String refName = ref instanceof PsiClass ? ((PsiClass)ref).getQualifiedName() : ((PsiPackage)ref).getQualifiedName();
          refTexts.add(refName);
        }
        calcClassesToReimport(file, facade, helper, packageName, refTexts, classesToReimport);
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
      PsiImportStatement statement = useOnDemand ? factory.createImportStatementOnDemand(packageName) : factory.createImportStatement(refClass);
      importList.add(statement);
      if (useOnDemand) {
        for (PsiJavaCodeReferenceElement ref : importRefs) {
          LOG.assertTrue(ref.getParent() instanceof PsiImportStatement);
          if (!ref.isValid()) continue; // todo[dsl] Q?
          PsiImportStatement importStatement = (PsiImportStatement) ref.getParent();
          if (importStatement.isForeignFileImport()) {
            // we should not optimize imports from include files
            continue;
          }
          classesToReimport.add((PsiClass)ref.resolve());
          importStatement.delete();
        }
      }

      for (PsiClass aClass : classesToReimport) {
        if (aClass != null) {
          addImport(file, aClass, true);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return true;
  }

  private static Optional<PsiImportStatement> findUnusedSingleImport(PsiJavaFile file, String shortName, String fqName) {
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
              return Optional.of(statement);
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  private static boolean containsInCurrentPackage(@NotNull PsiJavaFile file, PsiClass curRefClass) {
    if (curRefClass != null) {
      final String curRefClassQualifiedName = curRefClass.getQualifiedName();
      if (curRefClassQualifiedName != null &&
          ArrayUtil.find(file.getImplicitlyImportedPackages(), StringUtil.getPackageName(curRefClassQualifiedName)) < 0) {
         return true;
      }
    }
    return false;
  }

  private static void calcClassesToReimport(@NotNull PsiJavaFile file,
                                            @NotNull JavaPsiFacade facade,
                                            @NotNull PsiResolveHelper helper,
                                            @NotNull String packageName,
                                            @NotNull Collection<String> onDemandRefs,
                                            @NotNull List<? super PsiClass> outClassesToReimport) {
    if (onDemandRefs.isEmpty()) {
      return;
    }
    PsiPackage aPackage = facade.findPackage(packageName);
    if (aPackage == null) {
      return;
    }
    GlobalSearchScope resolveScope = file.getResolveScope();
    for (PsiClass aClass : aPackage.getClasses(resolveScope)) {
      String name = aClass.getName();
      for (String refName : onDemandRefs) {
        String conflictClassName = refName + "." + name;
        PsiClass conflictClass = facade.findClass(conflictClassName, resolveScope);
        if (conflictClass == null || !helper.isAccessible(conflictClass, file, null)) continue;
        String conflictClassName2 = packageName + "." + name;
        PsiClass conflictClass2 = facade.findClass(conflictClassName2, resolveScope);
        if (conflictClass2 != null &&
            helper.isAccessible(conflictClass2, file, null) &&
            ReferencesSearch.search(conflictClass, new LocalSearchScope(file), false).findFirst() != null) {
          outClassesToReimport.add(conflictClass);
        }
      }
    }
  }

  @NotNull
  private static List<PsiJavaCodeReferenceElement> getImportsFromPackage(@NotNull PsiJavaFile file, @NotNull String packageName){
    PsiClass[] refs = file.getSingleClassImports(true);
    List<PsiJavaCodeReferenceElement> array = new ArrayList<>();
    for (PsiClass ref1 : refs) {
      String className = ref1.getQualifiedName();
      if (getPackageOrClassName(className).equals(packageName)) {
        final PsiJavaCodeReferenceElement ref = file.findImportReferenceTo(ref1);
        if (ref != null) {
          array.add(ref);
        }
      }
    }
    return array;
  }

  private static PsiClass findSingleImportByShortName(@NotNull final PsiJavaFile file, @NotNull String shortClassName){
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

    // there maybe a class imported implicitly from current package
    String packageName = file.getPackageName();
    if (!StringUtil.isEmptyOrSpaces(packageName)) {
      String fqn = packageName + "." + shortClassName;
      final PsiClass aClass = JavaPsiFacade.getInstance(file.getProject()).findClass(fqn, file.getResolveScope());
      if (aClass != null) {
        final boolean[] foundRef = {false};
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

  private static PsiPackage findImportOnDemand(@NotNull PsiJavaFile file, @NotNull String packageName){
    PsiElement[] refs = file.getOnDemandImports(false, true);
    for (PsiElement ref : refs) {
      if (ref instanceof PsiPackage && ((PsiPackage)ref).getQualifiedName().equals(packageName)) {
        return (PsiPackage)ref;
      }
    }
    return null;
  }

  public static boolean isAlreadyImported(@NotNull PsiJavaFile file, @NotNull String fullyQualifiedName) {
    String className = extractClassName(file, fullyQualifiedName);

    Project project = file.getProject();
    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(project);

    PsiClass psiClass = resolveHelper.resolveReferencedClass(className, file);
    return psiClass != null && fullyQualifiedName.equals(psiClass.getQualifiedName());
  }

  @NotNull
  private static String extractClassName(@NotNull PsiJavaFile file, @NotNull String fullyQualifiedName) {
    for (PsiClass aClass : file.getClasses()) {
      String outerClassName = aClass.getQualifiedName();
      if (outerClassName != null && fullyQualifiedName.startsWith(outerClassName)) {
        return fullyQualifiedName.substring(outerClassName.lastIndexOf('.') + 1);
      }
    }

    return ClassUtil.extractClassName(fullyQualifiedName);
  }

  public ASTNode getDefaultAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement){
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (ref == null) return null;

    int entryIndex = findEntryIndex(statement);
    PsiImportStatementBase[] allStatements = list.getAllImportStatements();
    int[] entries = ArrayUtil.newIntArray(allStatements.length);
    List<PsiImportStatementBase> statements = new ArrayList<>();
    for(int i = 0; i < allStatements.length; i++){
      PsiImportStatementBase statement1 = allStatements[i];
      int entryIndex1 = findEntryIndex(statement1);
      entries[i] = entryIndex1;
      if (entryIndex1 == entryIndex){
        statements.add(statement1);
      }
    }

    if (statements.isEmpty()){
      int index;
      for(index = entries.length - 1; index >= 0; index--){
        if (entries[index] < entryIndex) break;
      }
      index++;
      return index < entries.length ? SourceTreeToPsiMap.psiElementToTree(allStatements[index]) : null;
    }
    else {
      String refText = ref.getCanonicalText();
      if (statement.isOnDemand()){
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

  public int getEmptyLinesBetween(@NotNull PsiImportStatementBase statement1, @NotNull PsiImportStatementBase statement2){
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
    for(int i = index1 + 1; i < index2; i++){
      if (entries[i] == PackageEntry.BLANK_LINE_ENTRY){
        int space = 0;
        do{
          space++;
        } while(entries[++i] == PackageEntry.BLANK_LINE_ENTRY);
        maxSpace = Math.max(maxSpace, space);
      }
    }
    return maxSpace;
  }

  private static boolean isToUseImportOnDemand(@NotNull String packageName,
                                               int classCount,
                                               boolean isStaticImportNeeded,
                                               @NotNull JavaCodeStyleSettings settings){
    if (!settings.USE_SINGLE_CLASS_IMPORTS) return true;
    int limitCount = isStaticImportNeeded ? settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND :
                     settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    if (classCount >= limitCount) return true;
    if (packageName.isEmpty()) return false;
    PackageEntryTable table = settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    return table.contains(packageName);
  }

  private static int findEntryIndex(@NotNull String packageName, boolean isStatic, PackageEntry @NotNull [] entries) {
    PackageEntry bestEntry = null;
    int bestEntryIndex = -1;
    int allOtherStaticIndex = -1;
    int allOtherIndex = -1;
    for(int i = 0; i < entries.length; i++){
      PackageEntry entry = entries[i];
      if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
        allOtherStaticIndex = i;
      }
      if (entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY) {
        allOtherIndex = i;
      }
      if (entry.isBetterMatchForPackageThan(bestEntry, packageName, isStatic)) {
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

  int findEntryIndex(@NotNull PsiImportStatementBase statement){
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (ref == null) return -1;
    String packageName;
    if (statement.isOnDemand()){
      packageName = ref.getCanonicalText();
    }
    else{
      String className = ref.getCanonicalText();
      packageName = getPackageOrClassName(className);
    }
    return findEntryIndex(packageName, statement instanceof PsiImportStaticStatement, mySettings.IMPORT_LAYOUT_TABLE.getEntries());
  }

  public static boolean hasConflictingOnDemandImport(@NotNull PsiJavaFile file, @NotNull PsiClass psiClass, @NotNull String referenceName) {
    Collection<Pair<String, Boolean>> resultList = collectNamesToImport(file, new ArrayList<>());
    String qualifiedName = psiClass.getQualifiedName();
    for (Pair<String, Boolean> pair : resultList) {
      if (pair.second &&
          referenceName.equals(StringUtil.getShortName(pair.first)) &&
          !StringUtil.getPackageName(pair.first).equals(qualifiedName)) {
        return true;
      }
    }

    PsiImportList importList = file.getImportList();
    if (importList == null) return false;
    Set<String> onDemandImportedClasses = Arrays.stream(importList.getImportStaticStatements())
      .filter(statement -> statement.isOnDemand())
      .map(statement -> statement.resolveTargetClass())
      .filter(aClass -> aClass != null)
      .map(aClass -> aClass.getQualifiedName()).collect(toSet());
    String newImport = StringUtil.getQualifiedName(qualifiedName, referenceName);
    Set<String> singleImports = findSingleImports(file, Collections.singletonList(Pair.create(newImport, true)), onDemandImportedClasses);

    return singleImports.contains(newImport);
  }

  @NotNull
  // returns list of (name, isImportStatic) pairs
  private static Collection<Pair<String,Boolean>> collectNamesToImport(@NotNull PsiJavaFile file, List<? super PsiElement> comments){
    Set<Pair<String,Boolean>> names = new HashSet<>();

    final JspFile jspFile = JspPsiUtil.getJspFile(file);
    collectNamesToImport(names, comments, file, jspFile);
    if (jspFile != null) {
      PsiFile[] files = ArrayUtil.mergeArrays(JspSpiUtil.getIncludingFiles(jspFile), JspSpiUtil.getIncludedFiles(jspFile));
      for (PsiFile includingFile : files) {
        final PsiFile javaRoot = includingFile.getViewProvider().getPsi(JavaLanguage.INSTANCE);
        if (javaRoot instanceof PsiJavaFile && file != javaRoot) {
          collectNamesToImport(names, comments, (PsiJavaFile)javaRoot, jspFile);
        }
      }
    }

    addUnresolvedImportNames(names, file);

    return names;
  }

  private static void collectNamesToImport(@NotNull final Set<? super Pair<String, Boolean>> names,
                                           @NotNull List<? super PsiElement> comments,
                                           @NotNull final PsiJavaFile file,
                                           PsiFile context) {
    String packageName = file.getPackageName();

    final List<PsiFile> roots = file.getViewProvider().getAllFiles();
    for (PsiElement root : roots) {
      addNamesToImport(names, comments, root, packageName, context);
    }
  }

  private static void addNamesToImport(@NotNull Set<? super Pair<String, Boolean>> names,
                                       @NotNull List<? super PsiElement> comments,
                                       @NotNull PsiElement scope,
                                       @NotNull String thisPackageName,
                                       PsiFile context){
    if (scope instanceof PsiImportList) return;

    final LinkedList<PsiElement> stack = new LinkedList<>();
    stack.add(scope);
    while (!stack.isEmpty()) {
      final PsiElement child = stack.removeFirst();
      if (child instanceof PsiImportList) {
        for(PsiElement element = child.getFirstChild(); element != null; element = element.getNextSibling()) {
          ASTNode node = element.getNode();
          if (node == null) {
            continue;
          }
          IElementType elementType = node.getElementType();
          if (!ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(elementType) &&
              !JavaJspElementType.WHITE_SPACE_BIT_SET.contains(elementType))
          {
            comments.add(element);
          }
        }
        continue;
      }
      if (child instanceof PsiLiteralExpression) continue;
      ContainerUtil.addAll(stack, child.getChildren());

      for (final PsiReference reference : child.getReferences()) {
        JavaResolveResult resolveResult = HighlightVisitorImpl.resolveJavaReference(reference);
        if (resolveResult == null) continue;

        PsiJavaCodeReferenceElement referenceElement = null;
        if (reference instanceof PsiJavaReference javaReference) {
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
        }

        PsiElement refElement = resolveResult.getElement();

        PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
        if (!(currentFileResolveScope instanceof PsiImportStatementBase) && refElement != null) continue;
        if (context != null &&
            refElement != null &&
            (!currentFileResolveScope.isValid() ||
             currentFileResolveScope instanceof JspxImportStatement &&
             context != ((JspxImportStatement)currentFileResolveScope).getDeclarationFile())) {
          continue;
        }

        if (refElement == null && referenceElement != null) {
          refElement = ResolveClassUtil.resolveClass(referenceElement, referenceElement.getContainingFile()); // might be uncomplete code
        }
        if (refElement == null) continue;

        if (referenceElement != null && currentFileResolveScope instanceof PsiImportStaticStatement importStaticStatement) {
          String name = importStaticStatement.getImportReference().getCanonicalText();
          if (importStaticStatement.isOnDemand()) {
            String refName = referenceElement.getReferenceName();
            if (refName != null) name = name + "." + refName;
          }
          names.add(Pair.create(name, Boolean.TRUE));
          continue;
        }

        if (refElement instanceof PsiClass psiClass) {
          String qName = psiClass.getQualifiedName();
          if (qName == null || hasPackage(qName, thisPackageName)) continue;
          names.add(Pair.create(qName, Boolean.FALSE));
        }
      }
    }
  }

  private static void addUnresolvedImportNames(@NotNull final Set<? super Pair<String, Boolean>> namesToImport, @NotNull PsiJavaFile file) {
    final PsiImportList importList = file.getImportList();
    PsiImportStatementBase[] imports = importList == null ? PsiImportStatementBase.EMPTY_ARRAY : importList.getAllImportStatements();
    final Map<String, Pair<String, Boolean>> unresolvedNames = new HashMap<>();
    @NotNull Set<Pair<String, Boolean>> unresolvedOnDemand = new HashSet<>();
    for (PsiImportStatementBase anImport : imports) {
      PsiJavaCodeReferenceElement ref = anImport.getImportReference();
      if (ref == null) continue;
      JavaResolveResult[] results = ref.multiResolve(false);
      if (results.length == 0) {
        String text = ref.getCanonicalText();
        if (anImport.isOnDemand()) {
          text += ".*";
        }

        Pair<String, Boolean> pair = Pair.create(text, anImport instanceof PsiImportStaticStatement);
        if (anImport.isOnDemand()) {
          unresolvedOnDemand.add(pair);
        }
        else {
          unresolvedNames.put(ref.getReferenceName(), pair);
        }
      }
    }

    // do not optimize unresolved imports for things like JSP (IDEA-41814)
    if (file.getViewProvider().getLanguages().size() > 1 && file.getViewProvider().getBaseLanguage() != JavaLanguage.INSTANCE) {
      namesToImport.addAll(unresolvedOnDemand);
      namesToImport.addAll(unresolvedNames.values());
      return;
    }

    final boolean[] hasResolveProblem = {false};
    // do not visit imports
    for (PsiClass aClass : file.getClasses()) {
      if (!(aClass instanceof PsiCompiledElement)) {
        aClass.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
            String name = reference.getReferenceName();
            Pair<String, Boolean> pair = unresolvedNames.get(name);
            if (reference.multiResolve(false).length == 0) {
              hasResolveProblem[0] = true;
              if (pair != null) {
                namesToImport.add(pair);
                unresolvedNames.remove(name);
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

  static boolean isImplicitlyImported(@NotNull String className, @NotNull PsiJavaFile file) {
    String[] packageNames = file.getImplicitlyImportedPackages();
    for (String packageName : packageNames) {
      if (hasPackage(className, packageName)) return true;
    }
    return false;
  }

  static boolean hasPackage(@NotNull String className, @NotNull String packageName){
    if (!className.startsWith(packageName)) return false;
    if (className.length() == packageName.length()) return false;
    if (!packageName.isEmpty() && className.charAt(packageName.length()) != '.') return false;
    return className.indexOf('.', packageName.length() + 1) < 0;
  }

  @NotNull
  private static String getPackageOrClassName(@NotNull String className){
    int dotIndex = className.lastIndexOf('.');
    return dotIndex < 0 ? "" : className.substring(0, dotIndex);
  }
}
