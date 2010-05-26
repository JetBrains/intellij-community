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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.impl.source.resolve.ResolveClassUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ImportHelper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.ImportHelper");

  private final CodeStyleSettings mySettings;
  @NonNls private static final String JAVA_LANG_PACKAGE = "java.lang";

  public ImportHelper(@NotNull CodeStyleSettings settings){
    mySettings = settings;
  }

  public PsiImportList prepareOptimizeImportsResult(@NotNull final PsiJavaFile file) {
    // Note: this array may contain "<packageOrClassName>.*" for unresolved imports!
    List<Pair<String, Boolean>> names = new ArrayList<Pair<String, Boolean>>(collectNamesToImport(file));
    Collections.sort(names, new Comparator<Pair<String, Boolean>>() {
      public int compare(Pair<String, Boolean> o1, Pair<String, Boolean> o2) {
        return o1.getFirst().compareTo(o2.getFirst());
      }
    });

    int[] entryForName = ArrayUtil.newIntArray(names.size());
    PackageEntry[] entries = mySettings.IMPORT_LAYOUT_TABLE.getEntries();
    for(int i = 0; i < names.size(); i++){
      Pair<String, Boolean> pair = names.get(i);
      String packageName = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      entryForName[i] = findEntryIndex(packageName, isStatic, entries);
    }

    List<Pair<String, Boolean>> resultList = new ArrayList<Pair<String, Boolean>>(names.size());
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

    TObjectIntHashMap<String> packageToCountMap = new TObjectIntHashMap<String>();
    TObjectIntHashMap<String> classToCountMap = new TObjectIntHashMap<String>();
    for (Pair<String, Boolean> pair : resultList) {
      String name = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      String packageOrClassName = getPackageOrClassName(name);
      if (packageOrClassName.length() == 0) continue;
      if (isStatic) {
        int count = classToCountMap.get(packageOrClassName);
        classToCountMap.put(packageOrClassName, count + 1);
      }
      else {
        int count = packageToCountMap.get(packageOrClassName);
        packageToCountMap.put(packageOrClassName, count + 1);
      }
    }

    final Set<String> classesOrPackagesToImportOnDemand = new THashSet<String>();
    class MyVisitorProcedure implements TObjectIntProcedure<String> {
      private final boolean myIsVisitingPackages;

      MyVisitorProcedure(boolean isVisitingPackages) {
        myIsVisitingPackages = isVisitingPackages;
      }

      public boolean execute(final String packageOrClassName, final int count) {
        if (isToUseImportOnDemand(packageOrClassName, count, !myIsVisitingPackages)){
          classesOrPackagesToImportOnDemand.add(packageOrClassName);
        }
        return true;
      }
    }
    classToCountMap.forEachEntry(new MyVisitorProcedure(false));
    packageToCountMap.forEachEntry(new MyVisitorProcedure(true));

    Set<String> classesToUseSingle = findSingleImports(file, resultList, classesOrPackagesToImportOnDemand);
    Set<String> toReimport = new THashSet<String>();
    calcClassesConflictingViaOnDemandImports(file, classesOrPackagesToImportOnDemand, file.getResolveScope(), toReimport);
    classesToUseSingle.addAll(toReimport);

    try {
      StringBuilder text = buildImportListText(resultList, classesOrPackagesToImportOnDemand, classesToUseSingle);
      String ext = StdFileTypes.JAVA.getDefaultExtension();
      PsiFileFactory factory = PsiFileFactory.getInstance(file.getProject());
      final PsiJavaFile dummyFile = (PsiJavaFile)factory.createFileFromText("_Dummy_." + ext, StdFileTypes.JAVA, text);
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(file.getProject());
      codeStyleManager.reformat(dummyFile);

      PsiImportList result = dummyFile.getImportList();
      PsiImportList oldList = file.getImportList();
      if (oldList.isReplaceEquivalent(result)) return null;
      return result;
    }
    catch(IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  private static Set<String> findSingleImports(@NotNull final PsiJavaFile file,
                                               @NotNull List<Pair<String,Boolean>> names,
                                               @NotNull final Set<String> onDemandImports
                                               ) {
    final GlobalSearchScope resolveScope = file.getResolveScope();
    Set<String> namesToUseSingle = new THashSet<String>();
    final String thisPackageName = file.getPackageName();
    final Set<String> implicitlyImportedPackages = new THashSet<String>(Arrays.asList(file.getImplicitlyImportedPackages()));
    final PsiManager manager = file.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    List<PsiElement> onDemandElements = new ArrayList<PsiElement>(onDemandImports.size());
    List<String> onDemandImportsList = new ArrayList<String>(onDemandImports);
    for (String onDemandName : onDemandImportsList) {
      PsiElement aClass = facade.findClass(onDemandName, resolveScope);
      onDemandElements.add(aClass);
    }
    for (Pair<String, Boolean> pair : names) {
      String name = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      String prefix = getPackageOrClassName(name);
      if (prefix.length() == 0) continue;
      final boolean isImplicitlyImported = implicitlyImportedPackages.contains(prefix);
      if (!onDemandImports.contains(prefix) && !isImplicitlyImported) continue;
      String shortName = PsiNameHelper.getShortClassName(name);

      String thisPackageClass = thisPackageName.length() > 0 ? thisPackageName + "." + shortName : shortName;
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
      for (int i = 0; i < onDemandImportsList.size(); i++) {
        String onDemandName = onDemandImportsList.get(i);
        if (prefix.equals(onDemandName)) continue;
        if (isStatic) {
          PsiElement element = onDemandElements.get(i);
          PsiClass aClass = (PsiClass)element;
          if (aClass != null) {
            PsiField field = aClass.findFieldByName(shortName, true);
            if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
              namesToUseSingle.add(name);
            }
            else {
              PsiClass inner = aClass.findInnerClassByName(shortName, true);
              if (inner != null && inner.hasModifierProperty(PsiModifier.STATIC)) {
                namesToUseSingle.add(name);
              }
              else {
                PsiMethod[] methods = aClass.findMethodsByName(shortName, true);
                for (PsiMethod method : methods) {
                  if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    namesToUseSingle.add(name);
                  }
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

  private static void calcClassesConflictingViaOnDemandImports(PsiJavaFile file, Collection<String> onDemandImportsList,
                                                               GlobalSearchScope resolveScope, final Set<String> namesToUseSingle) {
    List<String> onDemands = new ArrayList<String>(Arrays.asList(file.getImplicitlyImportedPackages()));
    onDemands.addAll(onDemandImportsList);
    if (onDemands.size() < 2) return;

    Map<String, Set<String>> classNames = new THashMap<String, Set<String>>();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    for (int i = onDemands.size()-1; i>=0; i--) {
      String onDemand = onDemands.get(i);
      PsiPackage aPackage = facade.findPackage(onDemand);
      if (aPackage == null) {
        onDemands.remove(i);
        continue;
      }
      PsiClass[] psiClasses = aPackage.getClasses(resolveScope);
      Set<String> set = new THashSet<String>(psiClasses.length);
      for (PsiClass psiClass : psiClasses) {
        set.add(psiClass.getName());
      }
      classNames.put(onDemand, set);
    }

    final Set<String> conflicts = new THashSet<String>();
    for (int i = 0; i < onDemands.size(); i++) {
      String on1 = onDemands.get(i);
      for (int j = i+1; j < onDemands.size(); j++) {
        String on2 = onDemands.get(j);
        Set<String> inter = new THashSet<String>(classNames.get(on1));
        inter.retainAll(classNames.get(on2));

        conflicts.addAll(inter);
      }
    }
    if (!conflicts.isEmpty()) {
      file.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          if (reference.getQualifier() != null) return;
          PsiElement element = reference.resolve();
          if (element instanceof PsiClass && conflicts.contains(((PsiClass)element).getName())) {
            String fqn = ((PsiClass)element).getQualifiedName();
            namesToUseSingle.add(fqn);
          }
        }
      });
    }
  }

  @NotNull
  private static StringBuilder buildImportListText(@NotNull List<Pair<String, Boolean>> names,
                                                   @NotNull final Set<String> packagesOrClassesToImportOnDemand,
                                                   @NotNull final Set<String> namesToUseSingle) {
    final Set<String> importedPackagesOrClasses = new THashSet<String>();
    @NonNls final StringBuilder buffer = new StringBuilder();
    for (Pair<String, Boolean> pair : names) {
      String name = pair.getFirst();
      Boolean isStatic = pair.getSecond();
      String packageOrClassName = getPackageOrClassName(name);
      final boolean implicitlyImported = JAVA_LANG_PACKAGE.equals(packageOrClassName);
      boolean useOnDemand = implicitlyImported || packagesOrClassesToImportOnDemand.contains(packageOrClassName);
      if (useOnDemand && namesToUseSingle.remove(name)) {
        useOnDemand = false;
      }
      if (useOnDemand && (importedPackagesOrClasses.contains(packageOrClassName) || implicitlyImported)) continue;
      buffer.append("import ");
      if (isStatic) buffer.append("static ");
      if (useOnDemand) {
        importedPackagesOrClasses.add(packageOrClassName);
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
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass){
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    PsiResolveHelper helper = facade.getResolveHelper();

    String className = refClass.getQualifiedName();
    if (className == null) return true;
    String packageName = getPackageOrClassName(className);
    String shortName = PsiNameHelper.getShortClassName(className);

    PsiClass conflictSingleRef = findSingleImportByShortName(file, shortName);
    if (conflictSingleRef != null){
      return className.equals(conflictSingleRef.getQualifiedName());
    }

    PsiClass curRefClass = helper.resolveReferencedClass(shortName, file);
    if (file.getManager().areElementsEquivalent(refClass, curRefClass)) {
      return true;
    }

    boolean useOnDemand = true;
    if (packageName.length() == 0){
      useOnDemand = false;
    }

    PsiElement conflictPackageRef = findImportOnDemand(file, packageName);
    if (conflictPackageRef != null) {
      useOnDemand = false;
    }

    List<PsiClass> classesToReimport = new ArrayList<PsiClass>();

    List<PsiJavaCodeReferenceElement> importRefs = getImportsFromPackage(file, packageName);
    if (useOnDemand){
      if (mySettings.USE_SINGLE_CLASS_IMPORTS &&
          importRefs.size() + 1 < mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND &&
          !mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(packageName)) {
        useOnDemand = false;
      }
      // name of class we try to import is the same as of the class defined in this file
      if (curRefClass != null) {
        useOnDemand = true;
      }
      // check conflicts
      if (useOnDemand){
        PsiElement[] onDemandRefs = file.getOnDemandImports(false, true);
        List<String> refTexts = new ArrayList<String>(onDemandRefs.length);
        for (PsiElement ref : onDemandRefs) {
          String refName = ref instanceof PsiClass ? ((PsiClass)ref).getQualifiedName() : ((PsiPackage)ref).getQualifiedName();
          refTexts.add(refName);
        }
        calcClassesToReimport(file, facade, helper, packageName, classesToReimport, refTexts);
      }
    }

    try {
      PsiImportList importList = file.getImportList();
      PsiImportStatement statement = useOnDemand ? factory.createImportStatementOnDemand(packageName) : factory.createImportStatement(refClass);
      importList.add(statement);
      if (useOnDemand) {
        for (PsiJavaCodeReferenceElement ref : importRefs) {
          LOG.assertTrue(ref.getParent() instanceof PsiImportStatement);
          if (!ref.isValid()) continue; // todo[dsl] Q?
          classesToReimport.add((PsiClass)ref.resolve());
          PsiImportStatement importStatement = (PsiImportStatement) ref.getParent();
          importStatement.delete();
        }
      }

      for (PsiClass aClass : classesToReimport) {
        if (aClass != null) {
          addImport(file, aClass);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return true;
  }

  private static void calcClassesToReimport(PsiJavaFile file, JavaPsiFacade facade, PsiResolveHelper helper, String packageName, List<PsiClass> classesToReimport,
                                     Collection<String> onDemandRefs) {
    if (onDemandRefs.isEmpty()) {
      return;
    }
    PsiPackage aPackage = facade.findPackage(packageName);
    if (aPackage != null) {
      PsiDirectory[] dirs = aPackage.getDirectories();
      GlobalSearchScope resolveScope = file.getResolveScope();
      for (PsiDirectory dir : dirs) {
        PsiFile[] files = dir.getFiles(); // do not iterate classes - too slow when not loaded
        for (PsiFile aFile : files) {
          if (!(aFile instanceof PsiJavaFile)) continue;
          String name = aFile.getVirtualFile().getNameWithoutExtension();
          for (String refName : onDemandRefs) {
            String conflictClassName = refName + "." + name;
            PsiClass conflictClass = facade.findClass(conflictClassName, resolveScope);
            if (conflictClass == null || !helper.isAccessible(conflictClass, file, null)) continue;
            String conflictClassName2 = packageName + "." + name;
            PsiClass conflictClass2 = facade.findClass(conflictClassName2, resolveScope);
            if (conflictClass2 != null &&
                helper.isAccessible(conflictClass2, file, null) &&
                ReferencesSearch.search(conflictClass, new LocalSearchScope(file), false).findFirst() != null) {
              classesToReimport.add(conflictClass);
            }
          }
        }
      }
    }
  }

  @NotNull
  private static List<PsiJavaCodeReferenceElement> getImportsFromPackage(@NotNull PsiJavaFile file, @NotNull String packageName){
    PsiClass[] refs = file.getSingleClassImports(true);
    List<PsiJavaCodeReferenceElement> array = new ArrayList<PsiJavaCodeReferenceElement>();
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

  private static PsiClass findSingleImportByShortName(@NotNull PsiJavaFile file, @NotNull String shortClassName){
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

  public ASTNode getDefaultAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement){
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (ref == null) return null;

    int entryIndex = findEntryIndex(statement);
    PsiImportStatementBase[] allStatements = list.getAllImportStatements();
    int[] entries = ArrayUtil.newIntArray(allStatements.length);
    List<PsiImportStatementBase> statements = new ArrayList<PsiImportStatementBase>();
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

  private boolean isToUseImportOnDemand(@NotNull String packageName, int classCount, boolean isStaticImportNeeded){
    if (!mySettings.USE_SINGLE_CLASS_IMPORTS) return true;
    int limitCount = isStaticImportNeeded ? mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND :
                     mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    if (classCount >= limitCount) return true;
    if (packageName.length() == 0) return false;
    PackageEntryTable table = mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    return table != null && table.contains(packageName);
  }

  private static int findEntryIndex(@NotNull String packageName, boolean isStatic, @NotNull PackageEntry[] entries) {
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

  public int findEntryIndex(@NotNull PsiImportStatementBase statement){
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

  @NotNull
  // returns list of (name, isImportStatic) pairs
  private static Collection<Pair<String,Boolean>> collectNamesToImport(@NotNull PsiJavaFile file){
    Set<Pair<String,Boolean>> names = new THashSet<Pair<String,Boolean>>();

    final JspFile jspFile = JspPsiUtil.getJspFile(file);
    collectNamesToImport(names, file, jspFile);
    if (jspFile != null) {
      PsiFile[] files = ArrayUtil.mergeArrays(JspSpiUtil.getIncludingFiles(jspFile), JspSpiUtil.getIncludedFiles(jspFile), PsiFile.class);
      for (PsiFile includingFile : files) {
        final PsiFile javaRoot = includingFile.getViewProvider().getPsi(StdLanguages.JAVA);
        if (javaRoot instanceof PsiJavaFile && file != javaRoot) {
          collectNamesToImport(names, (PsiJavaFile)javaRoot, jspFile);
        }
      }
    }

    addUnresolvedImportNames(names, file);

    return names;
  }

  private static void collectNamesToImport(@NotNull final Set<Pair<String, Boolean>> names,
                                           @NotNull final PsiJavaFile file,
                                           PsiFile context) {
    String packageName = file.getPackageName();

    final PsiElement[] roots = file.getPsiRoots();
    for (PsiElement root : roots) {
      addNamesToImport(names, root, packageName, context);
    }
  }

  private static void addNamesToImport(@NotNull Set<Pair<String, Boolean>> names,
                                       @NotNull PsiElement scope,
                                       @NotNull String thisPackageName,
                                       PsiFile context){
    if (scope instanceof PsiImportList) return;

    final LinkedList<PsiElement> stack = new LinkedList<PsiElement>();
    stack.add(scope);
    while (!stack.isEmpty()) {
      final PsiElement child = stack.removeFirst();
      if (child instanceof PsiImportList) continue;
      stack.addAll(Arrays.asList(child.getChildren()));

      for(final PsiReference reference : child.getReferences()){
        if (!(reference instanceof PsiJavaReference)) continue;
        final PsiJavaReference javaReference = (PsiJavaReference)reference;
        if (javaReference instanceof JavaClassReference && ((JavaClassReference)javaReference).getContextReference() != null) continue;
        PsiJavaCodeReferenceElement referenceElement = null;
        if (reference instanceof PsiJavaCodeReferenceElement) {
          referenceElement = (PsiJavaCodeReferenceElement)child;
          if (referenceElement.getQualifier() != null) {
            continue;
          }
          if (reference instanceof PsiJavaCodeReferenceElementImpl
              && ((PsiJavaCodeReferenceElementImpl)reference).getKind() == PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND) {
            continue;
          }
        }

        final JavaResolveResult resolveResult = javaReference.advancedResolve(true);
        PsiElement refElement = resolveResult.getElement();
        if (refElement == null && referenceElement != null) {
          refElement = ResolveClassUtil.resolveClass(referenceElement); // might be uncomplete code
        }
        if (refElement == null) continue;

        PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
        if (!(currentFileResolveScope instanceof PsiImportStatementBase)) continue;
        if (context != null &&
            (!currentFileResolveScope.isValid() ||
            currentFileResolveScope instanceof JspxImportStatement &&
            context != ((JspxImportStatement)currentFileResolveScope).getDeclarationFile())) {
          continue;
        }

        if (referenceElement != null) {
          if (currentFileResolveScope instanceof PsiImportStaticStatement) {
            PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)currentFileResolveScope;
            String name = importStaticStatement.getImportReference().getCanonicalText();
            if (importStaticStatement.isOnDemand()) {
              String refName = referenceElement.getReferenceName();
              if (refName != null) name = name + "." + refName;
            }
            names.add(Pair.create(name, Boolean.TRUE));
            continue;
          }
        }

        if (refElement instanceof PsiClass) {
          String qName = ((PsiClass)refElement).getQualifiedName();
          if (hasPackage(qName, thisPackageName)) continue;
          names.add(Pair.create(qName, Boolean.FALSE));
        }
      }
    }
  }

  private static void addUnresolvedImportNames(@NotNull final Set<Pair<String, Boolean>> namesToImport, @NotNull PsiJavaFile file) {
    PsiImportStatementBase[] imports = file.getImportList().getAllImportStatements();
    final Map<String, Pair<String, Boolean>> unresolvedNames = new THashMap<String, Pair<String, Boolean>>();
    @NotNull Set<Pair<String, Boolean>> unresolvedOnDemand = new THashSet<Pair<String, Boolean>>();
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

    final boolean[] hasResolveProblem = {false};
    // do not visit imports
    for (PsiClass aClass : file.getClasses()) {
      aClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
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
    if (hasResolveProblem[0]) {
      namesToImport.addAll(unresolvedOnDemand);
    }
    // otherwise, optimize out all red on demand imports for green file
  }

  public static boolean isImplicitlyImported(@NotNull String className, @NotNull PsiJavaFile file) {
    String[] packageNames = file.getImplicitlyImportedPackages();
    for (String packageName : packageNames) {
      if (hasPackage(className, packageName)) return true;
    }
    return false;
  }

  public static boolean hasPackage(@NotNull String className, @NotNull String packageName){
    if (!className.startsWith(packageName)) return false;
    if (className.length() == packageName.length()) return false;
    if (packageName.length() > 0 && className.charAt(packageName.length()) != '.') return false;
    return className.indexOf('.', packageName.length() + 1) < 0;
  }

  @NotNull
  private static String getPackageOrClassName(@NotNull String className){
    int dotIndex = className.lastIndexOf('.');
    return dotIndex < 0 ? "" : className.substring(0, dotIndex);
  }

}
