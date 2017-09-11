/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author peter
 */
public abstract class ImportClassFixBase<T extends PsiElement, R extends PsiReference> implements HintAction, HighPriorityAction {
  @NotNull
  private final T myElement;
  @NotNull
  private final R myRef;

  protected ImportClassFixBase(@NotNull T elem, @NotNull R ref) {
    myElement = elem;
    myRef = ref;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiFile file) {
    if (!myElement.isValid()) {
      return false;
    }

    PsiElement parent = myElement.getParent();
    if (parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null) {
      return false;
    }

    if (parent instanceof PsiReferenceExpression) {
      PsiExpression expression = ((PsiReferenceExpression)parent).getQualifierExpression();
      if (expression != null && expression != myElement) {
        return false;
      }
    }

    return !getClassesToImport(true).isEmpty();
  }

  @Nullable
  protected abstract String getReferenceName(@NotNull R reference);
  protected abstract PsiElement getReferenceNameElement(@NotNull R reference);
  protected abstract boolean hasTypeParameters(@NotNull R reference);

  @NotNull
  public List<PsiClass> getClassesToImport() {
    return getClassesToImport(false);
  }

  @NotNull
  public List<PsiClass> getClassesToImport(boolean acceptWrongNumberOfTypeParams) {
    if (myRef instanceof PsiJavaReference) {
      JavaResolveResult result = ((PsiJavaReference)myRef).advancedResolve(true);
      PsiElement element = result.getElement();
      // already imported
      // can happen when e.g. class name happened to be in a method position
      if (element instanceof PsiClass && result.isValidResult()) return Collections.emptyList();
    }

    String name = getReferenceName(myRef);
    GlobalSearchScope scope = myElement.getResolveScope();
    if (name == null) {
      return Collections.emptyList();
    }

    if (!canReferenceClass(myRef)) {
      return Collections.emptyList();
    }

    boolean referenceHasTypeParameters = hasTypeParameters(myRef);
    final Project project = myElement.getProject();
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(name, scope);
    if (classes.length == 0) return Collections.emptyList();
    List<PsiClass> classList = new ArrayList<>(classes.length);
    boolean isAnnotationReference = myElement.getParent() instanceof PsiAnnotation;
    final PsiFile file = myElement.getContainingFile();
    for (PsiClass aClass : classes) {
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (JavaCompletionUtil.isInExcludedPackage(aClass, false)) continue;
      if (!acceptWrongNumberOfTypeParams && referenceHasTypeParameters && !aClass.hasTypeParameters()) continue;
      String qName = aClass.getQualifiedName();
      if (qName != null) { //filter local classes
        if (qName.indexOf('.') == -1 || !PsiNameHelper.getInstance(project).isQualifiedName(qName)) continue; //do not show classes from default or invalid package
        if (qName.endsWith(name) && (file == null || ImportFilter.shouldImport(file, qName))) {
          if (isAccessible(aClass, myElement)) {
            classList.add(aClass);
          }
        }
      }
    }

    if (acceptWrongNumberOfTypeParams && referenceHasTypeParameters) {
      final List<PsiClass> candidates = new ArrayList<>();
      for (Iterator<PsiClass> iterator = classList.iterator(); iterator.hasNext(); ) {
        final PsiClass aClass = iterator.next();
        if (!aClass.hasTypeParameters()) {
          iterator.remove();
          candidates.add(aClass);
        }
      }

      if (classList.isEmpty()) {
        classList.addAll(candidates);
      }
    }

    classList = filterByRequiredMemberName(classList);

    List<PsiClass> filtered = filterByContext(classList, myElement);
    if (!filtered.isEmpty()) {
      classList = filtered;
    }

    filerByPackageName(classList, file);
    return classList;
  }

  protected void filerByPackageName(List<PsiClass> classList, PsiFile file) {
    final String packageName = StringUtil.getPackageName(getQualifiedName(myElement));
    if (!packageName.isEmpty() && 
        file instanceof PsiJavaFile && 
        Arrays.binarySearch(((PsiJavaFile)file).getImplicitlyImportedPackages(), packageName) < 0) {
      for (Iterator<PsiClass> iterator = classList.iterator(); iterator.hasNext(); ) {
        final String classQualifiedName = iterator.next().getQualifiedName();
        if (classQualifiedName != null && !packageName.equals(StringUtil.getPackageName(classQualifiedName))) {
          iterator.remove();
        }
      }
    }
  }

  protected boolean canReferenceClass(R ref) {
    return true;
  }

  private List<PsiClass> filterByRequiredMemberName(List<PsiClass> classList) {
    final String memberName = getRequiredMemberName(myElement);
    if (memberName != null) {
      List<PsiClass> filtered = ContainerUtil.findAll(classList, psiClass -> {
        PsiField field = psiClass.findFieldByName(memberName, true);
        if (field != null && field.hasModifierProperty(PsiModifier.STATIC) && isAccessible(field, myElement)) return true;

        PsiClass inner = psiClass.findInnerClassByName(memberName, true);
        if (inner != null && isAccessible(inner, myElement)) return true;

        for (PsiMethod method : psiClass.findMethodsByName(memberName, true)) {
          if (method.hasModifierProperty(PsiModifier.STATIC) && isAccessible(method, myElement)) return true;
        }
        return false;
      });
      if (!filtered.isEmpty()) {
        classList = filtered;
      }
    }
    return classList;
  }

  private void filterAlreadyImportedButUnresolved(@NotNull List<PsiClass> list) {
    PsiElement element = myRef.getElement();
    PsiFile containingFile = element == null ? null : element.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) return;
    PsiJavaFile javaFile = (PsiJavaFile)containingFile;
    PsiImportList importList = javaFile.getImportList();
    PsiImportStatementBase[] importStatements = importList == null ? PsiImportStatementBase.EMPTY_ARRAY : importList.getAllImportStatements();
    Set<String> importedNames = new THashSet<>(importStatements.length);
    for (PsiImportStatementBase statement : importStatements) {
      PsiJavaCodeReferenceElement ref = statement.getImportReference();
      String name = ref == null ? null : ref.getReferenceName();
      if (name != null && ref.resolve() == null) importedNames.add(name);
    }

    for (int i = list.size() - 1; i >= 0; i--) {
      PsiClass aClass = list.get(i);
      String className = aClass.getName();
      if (className != null && importedNames.contains(className)) {
        list.remove(i);
      }
    }
  }

  @Nullable
  protected String getRequiredMemberName(T reference) {
    return null;
  }

  @NotNull
  protected List<PsiClass> filterByContext(@NotNull List<PsiClass> candidates, @NotNull T ref) {
    return candidates;
  }

  protected abstract boolean isAccessible(PsiMember member, T reference);

  protected abstract String getQualifiedName(T reference);

  protected static List<PsiClass> filterAssignableFrom(PsiType type, List<PsiClass> candidates) {
    final PsiClass actualClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (actualClass != null) {
      return ContainerUtil.findAll(candidates, psiClass -> InheritanceUtil.isInheritorOrSelf(actualClass, psiClass, true));
    }
    return candidates;
  }

  protected static List<PsiClass> filterBySuperMethods(PsiParameter parameter, List<PsiClass> candidates) {
    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      PsiElement granny = parent.getParent();
      if (granny instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)granny;
        if (method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) != null) {
          PsiClass aClass = method.getContainingClass();
          final Set<PsiClass> probableTypes = new HashSet<>();
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
    }
    return candidates;
  }

  public enum Result {
    POPUP_SHOWN,
    CLASS_AUTO_IMPORTED,
    POPUP_NOT_SHOWN
  }

  public Result doFix(@NotNull final Editor editor, boolean allowPopup, final boolean allowCaretNearRef) {
    List<PsiClass> classesToImport = getClassesToImport();
    //do not show popups for already imported classes when library is missing (show them for explicit action)
    filterAlreadyImportedButUnresolved(classesToImport);
    if (classesToImport.isEmpty()) return Result.POPUP_NOT_SHOWN;

    try {
      String name = getQualifiedName(myElement);
      if (name != null) {
        Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
          return Result.POPUP_NOT_SHOWN;
        }
      }
    }
    catch (PatternSyntaxException e) {
      //ignore
    }
    final PsiFile psiFile = myElement.getContainingFile();
    if (classesToImport.size() > 1) {
      reduceSuggestedClassesBasedOnDependencyRuleViolation(psiFile, classesToImport);
    }
    PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
    final Project project = myElement.getProject();
    CodeInsightUtil.sortIdenticalShortNamedMembers(classes, myRef);

    final QuestionAction action = createAddImportAction(classes, project, editor);

    boolean canImportHere = true;

    boolean isInModlessContext =  Registry.is("ide.perProjectModality") ?
                                  !LaterInvocator.isInModalContextForProject(editor.getProject()) :
                                  !LaterInvocator.isInModalContext();

    if (classes.length == 1 &&
        (canImportHere = canImportHere(allowCaretNearRef, editor, psiFile, classes[0].getName())) &&
        isAddUnambiguousImportsOnTheFlyEnabled(psiFile) &&
        (ApplicationManager.getApplication().isUnitTestMode() || DaemonListeners.canChangeFileSilently(psiFile)) &&
        isInModlessContext &&
        !autoImportWillInsertUnexpectedCharacters(classes[0])
      ) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> action.execute());
      return Result.CLASS_AUTO_IMPORTED;
    }

    if (allowPopup && canImportHere) {
      String hintText = ShowAutoImportPass.getMessage(classes.length > 1, classes[0].getQualifiedName());
      if (!ApplicationManager.getApplication().isUnitTestMode() && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
        HintManager.getInstance().showQuestionHint(editor, hintText, getStartOffset(myElement, myRef),
                                                   getEndOffset(myElement, myRef), action);
      }
      return Result.POPUP_SHOWN;
    }
    return Result.POPUP_NOT_SHOWN;
  }

  public static boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile psiFile) {
    return FileTypeUtils.isInServerPageFile(psiFile) ?
           CodeInsightSettings.getInstance().JSP_ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY :
           CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
  }

  protected int getStartOffset(T element, R ref) {
    return element.getTextOffset();
  }

  protected int getEndOffset(T element, R ref) {
    return element.getTextRange().getEndOffset();
  }

  private static boolean autoImportWillInsertUnexpectedCharacters(PsiClass aClass) {
    PsiClass containingClass = aClass.getContainingClass();
    // when importing inner class, the reference might be qualified with outer class name and it can be confusing
    return containingClass != null;
  }

  private boolean canImportHere(boolean allowCaretNearRef, Editor editor, PsiFile psiFile, String exampleClassName) {
    return (allowCaretNearRef || !isCaretNearRef(editor, myRef)) &&
           !hasUnresolvedImportWhichCanImport(psiFile, exampleClassName);
  }

  protected abstract boolean isQualified(R reference);

  @Override
  public boolean showHint(@NotNull final Editor editor) {
    if (isQualified(myRef)) {
      return false;
    }
    Result result = doFix(editor, true, false);
    return result == Result.POPUP_SHOWN || result == Result.CLASS_AUTO_IMPORTED;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("import.class.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("import.class.fix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  protected abstract boolean hasUnresolvedImportWhichCanImport(PsiFile psiFile, String name);

  private static void reduceSuggestedClassesBasedOnDependencyRuleViolation(PsiFile file, List<PsiClass> availableClasses) {
    final Project project = file.getProject();
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(project);
    for (int i = availableClasses.size() - 1; i >= 0; i--) {
      PsiClass psiClass = availableClasses.get(i);
      PsiFile targetFile = psiClass.getContainingFile();
      if (targetFile == null) continue;
      final DependencyRule[] violated = validationManager.getViolatorDependencyRules(file, targetFile);
      if (violated.length != 0) {
        availableClasses.remove(i);
        if (availableClasses.size() == 1) break;
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
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(() -> {
      List<PsiClass> classesToImport = getClassesToImport(true);
      PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
      if (classes.length == 0) return;

      AddImportAction action = createAddImportAction(classes, project, editor);
      action.execute();
    });
  }

  protected void bindReference(PsiReference reference, PsiClass targetClass) {
    reference.bindToElement(targetClass);
  }

  protected AddImportAction createAddImportAction(PsiClass[] classes, Project project, Editor editor) {
    return new AddImportAction(project, myRef, editor, classes) {
      @Override
      protected void bindReference(PsiReference ref, PsiClass targetClass) {
        ImportClassFixBase.this.bindReference(ref, targetClass);
      }
    };
  }
}
