// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.completion.BaseCompletionParameters;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ModJavaStaticMemberProcessor {
  private final PsiElement myOriginalPosition;
  private final Set<PsiClass> myStaticImportedClasses = new HashSet<>();
  private final Set<PsiMember> myStaticImportedMembers = new HashSet<>();
  private final PsiElement myPosition;
  private final Project myProject;
  private final PsiResolveHelper myResolveHelper;
  private final boolean myPackagedContext;

  ModJavaStaticMemberProcessor(@NotNull BaseCompletionParameters parameters) {
    @NotNull PsiElement position = parameters.getPosition();
    myPosition = position;
    myProject = getPosition().getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    final PsiFile file = position.getContainingFile();
    myPackagedContext = file instanceof PsiJavaFile javaFile && !javaFile.getPackageName().isEmpty();
    myOriginalPosition = parameters.getOriginalPosition();
    if (file instanceof PsiJavaFile javaFile) {
      final PsiImportList importList = javaFile.getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          PsiClass aClass = statement.resolveTargetClass();
          if (aClass != null) {
            importMembersOf(aClass);
          }
        }
      }
    }
    Project project = parameters.getPosition().getProject();
    JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(project);
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope resolveScope = parameters.getOriginalFile().getResolveScope();
    JavaProjectCodeInsightSettings.AutoStaticNameContainer autoContainer = codeInsightSettings.getAllIncludedAutoStaticNames();
    for (String name : autoContainer.includedNames()) {
      PsiClass aClass = javaPsiFacade.findClass(name, resolveScope);
      if (aClass != null &&
          aClass.getQualifiedName() != null &&
          isAccessibleClass(aClass)) {
        for (PsiMethod method : aClass.getAllMethods()) {
          if (method.hasModifierProperty(PsiModifier.STATIC) &&
              autoContainer.containsName(aClass.getQualifiedName() + "." + method.getName())) {
            importMember(method);
          }
        }
        for (PsiField psiField : aClass.getAllFields()) {
          if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
              autoContainer.containsName(aClass.getQualifiedName() + "." + psiField.getName())) {
            importMember(psiField);
          }
        }
      }
      else {
        String shortMemberName = StringUtil.getShortName(name);
        String containingMemberName = StringUtil.getPackageName(name);
        if (containingMemberName.isEmpty() || shortMemberName.isEmpty()) continue;
        PsiClass containingClass = javaPsiFacade.findClass(containingMemberName, resolveScope);
        if (containingClass == null || containingClass.getQualifiedName() == null) continue;
        if (isAccessibleClass(containingClass)) {
          for (PsiMethod method : containingClass.findMethodsByName(shortMemberName, true)) {
            if (method.hasModifierProperty(PsiModifier.STATIC) &&
                autoContainer.containsName(containingClass.getQualifiedName() + "." + method.getName())) {
              importMember(method);
            }
          }
          PsiField psiField = containingClass.findFieldByName(shortMemberName, true);
          if (psiField != null &&
              psiField.hasModifierProperty(PsiModifier.STATIC) &&
              autoContainer.containsName(containingClass.getQualifiedName() + "." + psiField.getName())) {
            importMember(psiField);
          }
        }
      }
    }
  }

  private boolean isAccessibleClass(@NotNull PsiClass importFromClass) {
    boolean importFromDefaultPackage = importFromClass.getContainingFile() instanceof PsiJavaFile javaFile && javaFile.getPackageName().isBlank();
    if (importFromDefaultPackage) {
      boolean targetClassInDefaultPackage = myOriginalPosition.getContainingFile() instanceof PsiJavaFile targetClass && targetClass.getPackageName().isBlank();
      if(!targetClassInDefaultPackage) {
        return false;
      }
    }
    return true;
  }

  protected @Nullable ModCompletionItem createCompletionItem(@NotNull PsiMember member, final @NotNull PsiClass containingClass, boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    if (!PsiNameHelper.getInstance(member.getProject()).isIdentifier(member.getName(), PsiUtil.getLanguageLevel(getPosition()))) {
      return null;
    }

    PsiReference ref = createReferenceToMemberName(member);
    if (ref == null) return null;

    if (ref instanceof PsiReferenceExpression) {
      JavaResolveResult[] results = ((PsiReferenceExpression)ref).multiResolve(true);
      PsiClass memberContainingClass = member.getContainingClass();
      boolean shouldBeAutoImported = memberContainingClass != null &&
                                     member.hasModifierProperty(PsiModifier.STATIC) &&
                                     member.getName() != null &&
                                     JavaCodeStyleManager.getInstance(member.getProject())
                                       .isStaticAutoImportName(memberContainingClass.getQualifiedName() + "." + member.getName());
      if (shouldBeAutoImported && member.getContainingFile() instanceof PsiJavaFile javaFile &&
          javaFile.getPackageName().isBlank()) {
        shouldImport = false;
      }
      else if (results.length > 0) {
        if (shouldBeAutoImported) {
          shouldImport = !ContainerUtil.exists(results, result -> {
            PsiElement element = result.getElement();
            return element instanceof PsiModifierListOwner modifierListOwner &&
                   modifierListOwner.hasModifierProperty(PsiModifier.STATIC) ||
                   element instanceof PsiMember psiMember &&
                   member.getName().equals(psiMember.getName());
          });
        }
        else {
          shouldImport = false;
        }
      }
    }

    if (member instanceof PsiMethod method) {
      return getMethodCallElement(shouldImport, List.of(method));
    }
    return new VariableCompletionItem((PsiField)member, shouldImport)
      .qualifyIfNeeded(ObjectUtils.tryCast(getPosition().getParent(), PsiJavaCodeReferenceElement.class), containingClass);
  }

  private PsiReference createReferenceToMemberName(@NotNull PsiMember member) {
    String exprText = member.getName() + (member instanceof PsiMethod ? "()" : "");
    return JavaPsiFacade.getElementFactory(member.getProject()).createExpressionFromText(exprText, myOriginalPosition).findReferenceAt(0);
  }

  protected ModCompletionItem createCompletionItem(@NotNull List<? extends PsiMethod> overloads,
                                                   @NotNull PsiClass containingClass,
                                                   boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    return getMethodCallElement(shouldImport, overloads);
  }

  @NotNull MethodCallCompletionItem getMethodCallElement(boolean shouldImport, List<? extends PsiMethod> members) {
    return new MethodCallCompletionItem(members.getFirst(), shouldImport, members.size() > 1);
  }

  public void importMembersOf(@NotNull PsiClass psiClass) {
    myStaticImportedClasses.add(psiClass);
  }

  public void importMember(@NotNull PsiMember member) {
    myStaticImportedMembers.add(member);
  }

  public void processStaticMethodsGlobally(@NotNull PrefixMatcher matcher, @NotNull ModCompletionResult consumer) {
    GlobalSearchScope scope = myPosition.getResolveScope();
    Collection<String> memberNames = JavaStaticMemberNameIndex.getInstance().getAllKeys(myProject);
    for (String memberName : matcher.sortMatching(memberNames)) {
      Set<PsiClass> classes = new HashSet<>();
      for (PsiMember member : JavaStaticMemberNameIndex.getInstance().getStaticMembers(memberName, myProject, scope)) {
        processStaticMember(consumer, member, classes);
      }
    }
  }

  protected void processStaticMember(@NotNull ModCompletionResult consumer, PsiMember member, Set<PsiClass> classesToSkip) {
    if (isStaticallyImportable(member)) {
      PsiClass containingClass = member.getContainingClass();
      assert containingClass != null : member.getName() + "; " + member + "; " + member.getClass();

      if (JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext, containingClass.getContainingClass())) {
        if (member instanceof PsiMethod && !classesToSkip.add(containingClass)) return;
        boolean shouldImport = myStaticImportedClasses.contains(containingClass) || myStaticImportedMembers.contains(member);
        ModCompletionItem item = member instanceof PsiMethod ? createItemWithOverloads((PsiMethod)member, containingClass, shouldImport) :
                             member instanceof PsiField ? createCompletionItem(member, containingClass, shouldImport) :
                             null;
        if (item != null) {
          consumer.accept(item);
        }
      }
    }
  }

  private @Nullable ModCompletionItem createItemWithOverloads(@NotNull PsiMethod method, @NotNull PsiClass containingClass, boolean shouldImport) {
    List<PsiMethod> overloads = ContainerUtil.findAll(containingClass.findMethodsByName(method.getName(), true),
                                                      this::isStaticallyImportable);

    assert !overloads.isEmpty();
    if (overloads.size() == 1) {
      assert method == overloads.getFirst();
      return createCompletionItem(method, containingClass, shouldImport);
    }

    if (overloads.getFirst().getParameterList().isEmpty()) {
      overloads = new ArrayList<>(overloads);
      ContainerUtil.swapElements(overloads, 0, 1);
    }
    return createCompletionItem(overloads, containingClass, shouldImport);
  }

  public void processMembersOfRegisteredClasses(@NotNull Condition<? super String> nameCondition, @NotNull PairConsumer<? super PsiMember, ? super PsiClass> consumer) {
    for (PsiClass psiClass : myStaticImportedClasses) {
      for (PsiMethod method : psiClass.getAllMethods()) {
        if (nameCondition.value(method.getName())) {
          if (isStaticallyImportable(method)) {
            consumer.consume(method, psiClass);
          }
        }
      }

      for (PsiField field : psiClass.getAllFields()) {
        if (nameCondition.value(field. getName())) {
          if (isStaticallyImportable(field)) {
            consumer.consume(field, psiClass);
          }
        }
      }
    }

    for (PsiMember member : myStaticImportedMembers) {
      if (nameCondition.value(member. getName())) {
        if (isStaticallyImportable(member)) {
          consumer.consume(member, member.getContainingClass());
        }
      }
    }
  }

  protected boolean isStaticallyImportable(@NotNull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.STATIC) && isAccessible(member) && !isExcluded(member);
  }

  public PsiElement getPosition() {
    return myPosition;
  }

  protected boolean isAccessible(@NotNull PsiMember member) {
    return myResolveHelper.isAccessible(member, myPosition, null);
  }

  private static boolean isExcluded(@NotNull PsiMember method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
  }
}
