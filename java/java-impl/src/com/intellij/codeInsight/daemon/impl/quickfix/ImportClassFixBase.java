// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.daemon.impl.SilentChangeVetoer;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class ImportClassFixBase<T extends PsiElement, R extends PsiReference> extends ExpensivePsiIntentionAction implements HintAction, PriorityAction {
  private final @NotNull T myReferenceElement;
  private final @NotNull R myReference;
  private final PsiClass[] myClassesToImport;
  private final boolean myHasUnresolvedImportWhichCanImport;
  private final PsiFile myContainingFile;
  /**
   * If true, this.isAvailable() will return false when PSI has changed after this action instantiation.
   * By default, make this action unavailable on PSI modification because e.g., the file text might change to obsolete this fix altogether.
   * However, sometimes we do need to perform import on changed PSI, e.g., in case of auto-importing unambiguous references in bulk.
   */
  private boolean abortOnPSIModification = true;
  private boolean myInContent;
  private ThreeState extensionsAllowToChangeFileSilently;

  protected ImportClassFixBase(@NotNull T referenceElement, @NotNull R reference) {
    super(referenceElement.getProject());
    myReferenceElement = referenceElement;
    myReference = reference;
    myContainingFile = referenceElement.getContainingFile();
    myClassesToImport = calcClassesToImport();
    String firstName;
    myHasUnresolvedImportWhichCanImport = myClassesToImport.length != 0
                                          && (firstName = myClassesToImport[0].getName()) != null
                                          && myContainingFile != null
                                          && hasUnresolvedImportWhichCanImport(myContainingFile, firstName);
  }

  @Override
  public @NotNull Priority getPriority() {
    return PriorityAction.Priority.TOP;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiFile file) {
    if (myClassesToImport.length == 0) return false;
    return isStillAvailable() && !getClassesToImport(true).isEmpty();
  }

  protected boolean isStillAvailable() {
    if (!isPsiModificationStampChanged()) {
      return true;
    }
    if (abortOnPSIModification) return false;
    // ok, something did change. but can we still import? (in case of auto-import there maybe multiple fixes wanting to be executed)
    List<? extends PsiClass> classesToImport = getClassesToImport(true);
    return classesToImport.size() == 1 && !isClassMaybeImportedAlready(myContainingFile, classesToImport.get(0));
  }

  /**
   * @return true if the class candidate name to be imported already present in the import list (maybe some auto-import-fix for another reference did it?)
   * This method is intended to be cheap and resolve-free, because it might be called in EDT.
   * This method is used as an optimization against trying to import the same class several times,
   * so false negatives are OK (returning false even when the class already imported) whereas false positives are bad (don't return true when the class wasn't imported).
   */
  protected boolean isClassMaybeImportedAlready(@NotNull PsiFile containingFile, @NotNull PsiClass classToImport) {
    return false;
  }

  protected abstract @Nullable String getReferenceName(@NotNull R reference);
  protected abstract PsiElement getReferenceNameElement(@NotNull R reference);
  protected abstract boolean hasTypeParameters(@NotNull R reference);

  public @NotNull List<? extends PsiClass> getClassesToImport() {
    return getClassesToImport(false);
  }

  public @NotNull List<? extends PsiClass> getClassesToImport(boolean acceptWrongNumberOfTypeParams) {
    if (!acceptWrongNumberOfTypeParams && hasTypeParameters(myReference)) {
      return ContainerUtil.findAll(myClassesToImport, PsiTypeParameterListOwner::hasTypeParameters);
    }
    return Arrays.asList(myClassesToImport);
  }

  protected @NotNull R getReference() {
    return myReference;
  }

  private PsiClass @NotNull [] calcClassesToImport() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    PsiFile file = myContainingFile;
    if (file == null) {
      return PsiClass.EMPTY_ARRAY;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    myInContent = virtualFile != null && ModuleUtilCore.projectContainsFile(file.getProject(), virtualFile, false);

    extensionsAllowToChangeFileSilently = virtualFile == null ? ThreeState.UNSURE : SilentChangeVetoer.extensionsAllowToChangeFileSilently(file.getProject(), virtualFile);

    PsiElement referenceElement;
    if (!myReferenceElement.isValid() || (referenceElement = myReference.getElement()) != myReferenceElement && !referenceElement.isValid()) {
      return PsiClass.EMPTY_ARRAY;
    }
    if (myReference instanceof PsiJavaReference) {
      JavaResolveResult result = ((PsiJavaReference)myReference).advancedResolve(true);
      PsiElement element = result.getElement();
      // already imported
      // can happen when e.g., class name happened to be in a method position
      if (result.isValidResult() || element instanceof PsiClass && result.getCurrentFileResolveScope() instanceof PsiImportStatement) {
        return PsiClass.EMPTY_ARRAY;
      }
    }

    String name = getReferenceName(myReference);
    if (name == null) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (!canReferenceClass(myReference)) {
      return PsiClass.EMPTY_ARRAY;
    }

    Project project = file.getProject();

    PsiElement parent = myReferenceElement.getParent();
    if (parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (parent instanceof PsiReferenceExpression) {
      PsiExpression expression = ((PsiReferenceExpression)parent).getQualifierExpression();
      if (expression != null && expression != myReferenceElement) {
        return PsiClass.EMPTY_ARRAY;
      }
    }

    if (file instanceof PsiJavaCodeReferenceCodeFragment && !((PsiJavaCodeReferenceCodeFragment)file).isClassesAccepted()) {
      return PsiClass.EMPTY_ARRAY;
    }

    GlobalSearchScope scope = file.getResolveScope();
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(name, scope);
    if (classes.length == 0) return PsiClass.EMPTY_ARRAY;
    List<PsiClass> classList = new ArrayList<>(classes.length);
    boolean isAnnotationReference = myReferenceElement.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (qualifiedNameAllowsAutoImport(file, aClass)) {
        classList.add(aClass);
      }
    }
    boolean anyAccessibleFound = ContainerUtil.exists(classList, aClass -> isAccessible(aClass, myReferenceElement));
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    classList.removeIf(aClass -> (anyAccessibleFound ||
                                  !BaseIntentionAction.canModify(aClass) ||
                                  facade.arePackagesTheSame(aClass, myReferenceElement) ||
                                  PsiTreeUtil.getParentOfType(aClass, PsiImplicitClass.class) != null) &&
                                 !isAccessible(aClass, myReferenceElement));

    filterByRequiredMemberName(classList);

    Collection<PsiClass> filtered = filterByContext(classList, myReferenceElement);
    if (!filtered.isEmpty()) {
      classList = new ArrayList<>(filtered);
    }

    filerByPackageName(classList, file);

    filterAlreadyImportedButUnresolved(classList, file);

    if (classList.isEmpty() || isReferenceNameForbiddenForAutoImport()) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (classList.size() > 1) {
      reduceSuggestedClassesBasedOnDependencyRuleViolation(classList, file);
    }

    PsiClass[] array = classList.toArray(PsiClass.EMPTY_ARRAY);
    CodeInsightUtil.sortIdenticalShortNamedMembers(array, myReference);
    return array;
  }

  public static boolean qualifiedNameAllowsAutoImport(@NotNull PsiFile placeFile, @NotNull PsiClass aClass) {
    if (JavaCompletionUtil.isInExcludedPackage(aClass, false)) {
      return false;
    }
    String qName = aClass.getQualifiedName();
    if (qName != null) { //filter local classes
      if (qName.indexOf('.') == -1 || !PsiNameHelper.getInstance(placeFile.getProject()).isQualifiedName(qName)) return false;
      return ImportFilter.shouldImport(placeFile, qName);
    }
    return false;
  }

  private void filerByPackageName(@NotNull Collection<PsiClass> classList, @NotNull PsiFile file) {
    String qualifiedName = getQualifiedName(myReferenceElement);
    String packageName = StringUtil.getPackageName(qualifiedName);
    if (!packageName.isEmpty() &&
        file instanceof PsiJavaFile javaFile &&
        !ImportUtils.createImplicitImportChecker(javaFile).isImplicitlyImported(new ImportUtils.Import(qualifiedName, false))) {
      classList.removeIf(aClass -> {
        String classQualifiedName = aClass.getQualifiedName();
        return classQualifiedName != null && !packageName.equals(StringUtil.getPackageName(classQualifiedName));
      });
    }
  }

  protected boolean canReferenceClass(@NotNull R ref) {
    return true;
  }

  private void filterByRequiredMemberName(@NotNull List<PsiClass> classList) {
    String memberName = getRequiredMemberName(myReferenceElement);
    if (memberName != null) {
      classList.removeIf(psiClass -> {
        PsiField field = psiClass.findFieldByName(memberName, true);
        if (field != null && field.hasModifierProperty(PsiModifier.STATIC) && isAccessible(field, myReferenceElement)) return false;

        PsiClass inner = psiClass.findInnerClassByName(memberName, true);
        if (inner != null && isAccessible(inner, myReferenceElement)) return false;

        for (PsiMethod method : psiClass.findMethodsByName(memberName, true)) {
          if (method.hasModifierProperty(PsiModifier.STATIC) && isAccessible(method, myReferenceElement)) return false;
        }
        return true;
      });
    }
  }

  private static void filterAlreadyImportedButUnresolved(@NotNull Collection<PsiClass> list, @NotNull PsiFile containingFile) {
    if (!(containingFile instanceof PsiJavaFile javaFile)) return;
    PsiImportList importList = javaFile.getImportList();
    PsiImportStatementBase[] importStatements = importList == null ? PsiImportStatementBase.EMPTY_ARRAY : importList.getAllImportStatements();
    Set<String> unresolvedImports = new HashSet<>(importStatements.length);
    for (PsiImportStatementBase statement : importStatements) {
      if (statement instanceof PsiImportModuleStatement importModuleStatement) {
        PsiJavaModuleReferenceElement refElement = importModuleStatement.getModuleReference();
        if (refElement != null) {
          PsiJavaModuleReference ref = refElement.getReference();
          if (ref != null && ref.resolve() == null) unresolvedImports.add(importModuleStatement.getReferenceName());
        }
      } else {
        PsiJavaCodeReferenceElement ref = statement.getImportReference();
        String name = ref == null ? null : ref.getReferenceName();
        if (name != null && ref.resolve() == null) unresolvedImports.add(name);
      }
    }
    if (unresolvedImports.isEmpty()) return;
    list.removeIf(aClass -> {
      String className = aClass.getName();
      return className != null && unresolvedImports.contains(className);
    });
  }

  protected @Nullable String getRequiredMemberName(@NotNull T referenceElement) {
    return null;
  }

  protected @NotNull Collection<PsiClass> filterByContext(@NotNull Collection<PsiClass> candidates, @NotNull T referenceElement) {
    return candidates;
  }

  protected abstract boolean isAccessible(@NotNull PsiMember member, @NotNull T referenceElement);

  protected abstract String getQualifiedName(@NotNull T referenceElement);

  protected static @NotNull Collection<PsiClass> filterAssignableFrom(@NotNull PsiType type, @NotNull Collection<PsiClass> candidates) {
    PsiClass actualClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (actualClass != null) {
      return ContainerUtil.findAll(candidates, psiClass -> InheritanceUtil.isInheritorOrSelf(actualClass, psiClass, true));
    }
    return candidates;
  }

  protected static @NotNull Collection<PsiClass> filterBySuperMethods(@NotNull PsiParameter parameter, @NotNull Collection<PsiClass> candidates) {
    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      PsiElement granny = parent.getParent();
      if (granny instanceof PsiMethod method && method.getModifierList().hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE)) {
        PsiClass aClass = method.getContainingClass();
        Set<PsiClass> probableTypes = new HashSet<>();
        InheritanceUtil.processSupers(aClass, false, psiClass -> {
          for (PsiMethod psiMethod : psiClass.findMethodsByName(method.getName(), false)) {
            for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
              ContainerUtil.addIfNotNull(probableTypes, PsiUtil.resolveClassInClassTypeOnly(psiParameter.getType()));
            }
          }
          return true;
        });
        List<PsiClass> filtered = ContainerUtil.filter(candidates, psiClass -> probableTypes.contains(psiClass));
        if (!filtered.isEmpty()) {
          return filtered;
        }
      }
    }
    return candidates;
  }

  /**
   * The result of the import class operation
   */
  public enum Result {
    /**
     * The class import popup was shown (because e.g., the reference was ambiguous)
     */
    POPUP_SHOWN,
    /**
     * The class was auto-imported silently, no popup was shown
     */
    CLASS_AUTO_IMPORTED,
    /**
     * The popup was not shown for some reason (e.g., the referenced class was not found) and the class was not imported
     */
    POPUP_NOT_SHOWN
  }

  public @NotNull Result doFix(@NotNull Editor editor, boolean allowPopup, boolean allowCaretNearRef, boolean mayAddUnambiguousImportsSilently) {
    ThreadingAssertions.assertEventDispatchThread();
    List<? extends PsiClass> result = getClassesToImport();
    PsiClass[] classes = result.toArray(PsiClass.EMPTY_ARRAY);
    if (classes.length == 0) return Result.POPUP_NOT_SHOWN;

    PsiFile psiFile = myContainingFile;
    if (psiFile == null || !psiFile.isValid()) return Result.POPUP_NOT_SHOWN;
    Project project = psiFile.getProject();
    if (!isStillAvailable()) return Result.POPUP_NOT_SHOWN;

    QuestionAction action = createAddImportAction(classes, project, editor);

    boolean canImportHere = true;
    if (classes.length == 1 &&
        (canImportHere = canImportHere(allowCaretNearRef, editor)) &&
        mayAddUnambiguousImportsSilently &&
        !autoImportWillInsertUnexpectedCharacters(classes[0])) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> action.execute());
      return Result.CLASS_AUTO_IMPORTED;
    }

    if (allowPopup && canImportHere) {
      if (!ApplicationManager.getApplication().isUnitTestMode() && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
        String hintText = ShowAutoImportPass.getMessage(classes.length > 1, IdeBundle.message("go.to.class.kind.text"), classes[0].getQualifiedName());
        HintManager.getInstance().showQuestionHint(editor, hintText, getStartOffset(myReferenceElement, myReference),
                                                   getEndOffset(myReferenceElement, myReference), action);
      }
      return Result.POPUP_SHOWN;
    }
    return Result.POPUP_NOT_SHOWN;
  }

  private boolean isReferenceNameForbiddenForAutoImport() {
    try {
      String name = getQualifiedName(myReferenceElement);
      if (name != null) {
        Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
          return true;
        }
      }
    }
    catch (PatternSyntaxException e) {
      //ignore
    }
    return false;
  }

  protected int getStartOffset(@NotNull T element, @NotNull R ref) {
    return element.getTextOffset();
  }

  protected int getEndOffset(@NotNull T element, @NotNull R ref) {
    return element.getTextRange().getEndOffset();
  }

  private boolean autoImportWillInsertUnexpectedCharacters(@NotNull PsiClass aClass) {
    PsiClass containingClass = aClass.getContainingClass();
    // when importing inner class, the reference might be qualified with the outer class name, and it can be confusing
    return containingClass != null &&
           !CodeStyle.getSettings(myContainingFile).getCustomSettings(JavaCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS;
  }

  private boolean canImportHere(boolean allowCaretNearRef, @NotNull Editor editor) {
    return (allowCaretNearRef || !isCaretNearRef(editor, myReference)) && !myHasUnresolvedImportWhichCanImport;
  }

  protected abstract boolean isQualified(@NotNull R reference);

  @Override
  public boolean showHint(@NotNull Editor editor) {
    if (isQualified(myReference)) {
      return false;
    }
    PsiFile file = myReferenceElement.isValid() && myContainingFile != null && myContainingFile.isValid() ? myContainingFile : null;
    if (file == null) return false;

    Result result = doFix(editor, true, false, ShowAutoImportPass.mayAutoImportNow(file, myInContent, extensionsAllowToChangeFileSilently));
    return result == Result.POPUP_SHOWN || result == Result.CLASS_AUTO_IMPORTED;
  }

  @Override
  public @NotNull String getText() {
    return QuickFixBundle.message("import.class.fix");
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("import.class.fix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  protected abstract boolean hasUnresolvedImportWhichCanImport(@NotNull PsiFile psiFile, @NotNull String name);

  private static void reduceSuggestedClassesBasedOnDependencyRuleViolation(@NotNull List<PsiClass> classes, @NotNull PsiFile file) {
    Project project = file.getProject();
    DependencyValidationManager validationManager = DependencyValidationManager.getInstance(project);
    for (int i = classes.size() - 1; i >= 0; i--) {
      PsiClass psiClass = classes.get(i);
      PsiFile targetFile = psiClass.getContainingFile();
      if (targetFile == null) continue;
      DependencyRule[] violated = validationManager.getViolatorDependencyRules(file, targetFile);
      // remove class with violated dependency except the only remaining
      if (violated.length != 0 && (i!=0 || classes.size()>1)) {
        classes.remove(i);
      }
    }
  }

  private boolean isCaretNearRef(@NotNull Editor editor, @NotNull R ref) {
    PsiElement nameElement = getReferenceNameElement(ref);
    if (nameElement == null) return false;
    TextRange range = nameElement.getTextRange();
    int offset = editor.getCaretModel().getOffset();

    return offset == range.getEndOffset();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!isStillAvailable()) return;
      PsiClass[] classes = getClassesToImport(true).toArray(PsiClass.EMPTY_ARRAY);
      if (classes.length == 0) return;

      AddImportAction action = createAddImportAction(classes, project, editor);
      action.execute();
    });
  }

  protected void bindReference(@NotNull PsiReference reference, @NotNull PsiClass targetClass) {
    reference.bindToElement(targetClass);
  }

  protected @NotNull AddImportAction createAddImportAction(PsiClass @NotNull [] classes, @NotNull Project project, @NotNull Editor editor) {
    return new AddImportAction(project, myReference, editor, classes) {
      @Override
      protected void bindReference(@NotNull PsiReference ref, @NotNull PsiClass targetClass) {
        ImportClassFixBase.this.bindReference(ref, targetClass);
      }
    };
  }

  public void surviveOnPSIModifications() {
    abortOnPSIModification = false;
  }
}
