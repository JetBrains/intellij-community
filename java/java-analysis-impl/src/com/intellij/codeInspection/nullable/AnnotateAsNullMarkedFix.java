// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.nullable.MultipleElementsModCommandAction.Change;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.PsiPackage.PACKAGE_INFO_FILE;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;


@NotNullByDefault
public final class AnnotateAsNullMarkedFix implements ModCommandAction {
  private static final String NULL_MARKED_FQN = "org.jspecify.annotations.NullMarked";
  private static final String NULL_UNMARKED_FQN = "org.jspecify.annotations.NullUnmarked";
  private final @Nls String familyName;
  private final List<ModCommandAction> actions;

  /**
   * Creates a quick fix for the PsiTypeElement which is highlighted as expected to be marked with not-null annotation.
   * For each container (as specified in JSpecify spec) which encloses passed typeElement
   * a quick-fix is generated that either adds @NullMarked annotation or removes @NullUnmarked annotation
   * if-and-only-if such modification solves a nullability problem (missing expected not-null annotation).
   * If no quick-fix is found, then null is returned.
   * If only one quick-fix is found, then it will be returned.
   * If multiple quick-fixes are found, then a quick-fix that displays chooser with available quick-fixes is returned.
   *
   * @param typeElement element with missing not-null annotation
   * @param nullables   list of nullable annotations that should be removed from typeElement when applying any quick-fix
   */
  public static ModCommandAction createAnnotateAsNullMarkedFix(PsiTypeElement typeElement, List<String> nullables) {
    return new AnnotateAsNullMarkedFix(provideNullMarkedFixes(typeElement, nullables));
  }

  private AnnotateAsNullMarkedFix(List<ModCommandAction> actions) {
    this.actions = actions;
    this.familyName =
      JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.container.as", StringUtil.getShortName(NULL_MARKED_FQN));
  }

  @Override
  public @Nullable Presentation getPresentation(ActionContext context) {
    return switch (actions.size()) {
      case 0 -> null;
      case 1 -> actions.getFirst().getPresentation(context);
      default -> Presentation.of(familyName);
    };
  }

  @Override
  public String getFamilyName() {
    return familyName;
  }

  @Override
  public ModCommand perform(ActionContext context) {
    return ModCommand.chooseAction(JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.choose.container"), actions);
  }

  private static List<ModCommandAction> provideNullMarkedFixes(PsiTypeElement typeElement, List<String> nullables) {
    if (!isJSpecifyLibraryAvailable(typeElement.getContainingFile())) {
      return List.of();
    }

    List<Change<? extends PsiElement>> childrenChanges = new ArrayList<>();
    addChangesThatRemoveNullableAnnotations(typeElement, nullables, childrenChanges);
    List<? extends Container<? extends PsiElement>> containers = getContainersThatCanBeAnnotatedWithNullMarked(typeElement);

    var modCommandActions = new ArrayList<ModCommandAction>();
    for (int i = 0; i < containers.size(); i++) {
      Container<? extends PsiElement> container = containers.get(i);
      if (container.hasNullMarkedAnnotation()) {
        if (container.hasNullUnmarkedAnnotation()) {
          // jspecify spec treats case when both @NullMarked and @NullUnmarked annotations are present
          // as the case when none is present
          var text = container.textForRemoveAnnotationAction(NULL_UNMARKED_FQN);
          Change<?> removeUnmarked = container.newChangeThatRemovesAnnotation(NULL_UNMARKED_FQN);
          modCommandActions.add(new MultipleElementsModCommandAction(text, ContainerUtil.append(childrenChanges, removeUnmarked)));
        }
        else {
          break;
        }
      }
      else {
        if (container.hasNullUnmarkedAnnotation()) {
          Change<?> removeUnmarked = container.newChangeThatRemovesAnnotation(NULL_UNMARKED_FQN);
          String enclosing = annotationFromNearestAnnotatedParentContainer(containers, i);
          if (enclosing != null && enclosing.equals(NULL_MARKED_FQN)) {
            var text = container.textForRemoveAnnotationAction(NULL_UNMARKED_FQN);
            modCommandActions.add(new MultipleElementsModCommandAction(text, ContainerUtil.append(childrenChanges, removeUnmarked)));
            break;
          }
          else {
            var text = container.textForAddAnnotationAction(NULL_MARKED_FQN);
            Change<?> addMarked = container.newChangeThatAddsAnnotation(NULL_MARKED_FQN);
            var changes = ContainerUtil.append(childrenChanges, removeUnmarked, addMarked);
            modCommandActions.add(new MultipleElementsModCommandAction(text, changes));
          }
          childrenChanges = ContainerUtil.append(childrenChanges, removeUnmarked);
        }
        else {
          var text = container.textForAddAnnotationAction(NULL_MARKED_FQN);
          Change<?> addMarked = container.newChangeThatAddsAnnotation(NULL_MARKED_FQN);
          modCommandActions.add(new MultipleElementsModCommandAction(text, ContainerUtil.append(childrenChanges, addMarked)));
        }
      }
    }
    return modCommandActions;
  }

  private static boolean isJSpecifyLibraryAvailable(PsiFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file);
    if (module == null) return false;
    return JavaLibraryUtil.hasLibraryClass(module, NULL_MARKED_FQN) && JavaLibraryUtil.hasLibraryClass(module, NULL_UNMARKED_FQN);
  }

  /**
   * @return effective annotation on the nearest enclosing container that affects container at index i.
   */
  private static @Nullable String annotationFromNearestAnnotatedParentContainer(List<? extends Container<? extends PsiElement>> containers,
                                                                                int i) {
    for (int j = i + 1; j < containers.size(); j++) {
      Container<? extends PsiElement> container = containers.get(j);
      String annotation = container.getEffectiveAnnotation();
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  private static List<? extends Container<? extends PsiElement>> getContainersThatCanBeAnnotatedWithNullMarked(PsiElement element) {
    List<Container<? extends PsiElement>> result = new ArrayList<>();
    PsiModifierListOwner parent = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, true);
    while (parent != null) {
      if (parent.getModifierList() != null && (parent instanceof PsiClass || parent instanceof PsiMethod)) {
        result.add(new ModifierListOwnerContainer(parent, parent.getModifierList()));
      }
      parent = PsiTreeUtil.getParentOfType(parent, PsiModifierListOwner.class, true);
    }

    var containingFile = element.getContainingFile();
    if (containingFile == null) return result;
    var directory = containingFile.getContainingDirectory();
    if (directory == null) return result;
    var packageStatement = getChildOfType(containingFile, PsiPackageStatement.class);
    if (packageStatement == null) return result;
    var packageFqn = packageStatement.getPackageName();
    result.add(new PackageContainer(directory, packageFqn));

    return result;
  }

  private static void addChangesThatRemoveNullableAnnotations(PsiTypeElement typeElement, List<String> nullables,
                                                              List<Change<? extends PsiElement>> changes) {
    var annotationFqn = ContainerUtil.filter(nullables, annotation -> typeElement.hasAnnotation(annotation));
    changes.addAll(ContainerUtil.map(annotationFqn, a -> new Change<>(typeElement, (updater, element) -> removeAnnotation(element, a))));
  }

  /**
   * Container (PsiElement) on which JSpecify container annotation (NullMarked/NullUnmarked) can be added or removed.
   */
  private static sealed abstract class Container<T extends PsiElement> permits PackageContainer, ModifierListOwnerContainer {
    private final boolean hasNullMarkedAnnotation;
    private final boolean hasNullUnmarkedAnnotation;

    private Container(@Nullable PsiModifierList modifierList) {
      this.hasNullMarkedAnnotation = hasAnnotation(modifierList, NULL_MARKED_FQN);
      this.hasNullUnmarkedAnnotation = hasAnnotation(modifierList, NULL_UNMARKED_FQN);
    }

    private static boolean hasAnnotation(@Nullable PsiModifierList modifierList, String annotationFqn) {
      return modifierList != null && modifierList.hasAnnotation(annotationFqn);
    }

    private boolean hasNullMarkedAnnotation() {
      return hasNullMarkedAnnotation;
    }

    private boolean hasNullUnmarkedAnnotation() {
      return hasNullUnmarkedAnnotation;
    }

    public @Nullable String getEffectiveAnnotation() {
      // jspecify spec treats the case when both annotations are present as the case when none is present
      if (hasNullMarkedAnnotation && hasNullUnmarkedAnnotation) return null;
      if (hasNullMarkedAnnotation) return NULL_MARKED_FQN;
      if (hasNullUnmarkedAnnotation) return NULL_UNMARKED_FQN;
      return null;
    }

    public abstract Change<T> newChangeThatAddsAnnotation(String annotationFqn);

    public abstract Change<T> newChangeThatRemovesAnnotation(String annotationFqn);

    @IntentionName
    public abstract String textForAddAnnotationAction(String annotationFqn);

    @IntentionName
    public abstract String textForRemoveAnnotationAction(String annotationFqn);
  }

  /**
   * Provides Container operations for PsiModifierListOwner.
   * Add/Remove annotation operations are applied to modifier list.
   */
  private static final class ModifierListOwnerContainer extends Container<PsiModifierList> {
    private final PsiModifierListOwner modifierListOwner;
    private final PsiModifierList modifierList;

    private ModifierListOwnerContainer(PsiModifierListOwner modifierListOwner, PsiModifierList modifierList) {
      super(modifierList);
      this.modifierListOwner = modifierListOwner;
      this.modifierList = modifierList;
    }

    @Override
    public Change<PsiModifierList> newChangeThatAddsAnnotation(String annotationFqn) {
      return new Change<>(modifierList, (updater, m) -> updater.highlight(
        JavaCodeStyleManager.getInstance(m.getProject()).shortenClassReferences(m.addAnnotation(annotationFqn))));
    }

    @Override
    public Change<PsiModifierList> newChangeThatRemovesAnnotation(String annotationFqn) {
      return new Change<>(modifierList, (updater, modifierList) -> removeAnnotation(modifierList, annotationFqn));
    }

    @Override
    public String textForAddAnnotationAction(String annotationFqn) {
      return AddAnnotationPsiFix.calcText(modifierListOwner, annotationFqn);
    }

    @Override
    public String textForRemoveAnnotationAction(String annotationFqn) {
      if (modifierListOwner instanceof PsiNamedElement) {
        String name = PsiFormatUtil.formatSimple((PsiNamedElement)modifierListOwner);
        if (name != null) {
          var javaElementKind = JavaElementKind.fromElement(modifierListOwner).lessDescriptive();
          return createTextForRemoveAnnotationAction(annotationFqn, name, javaElementKind);
        }
      }
      return JavaAnalysisBundle.message("inspection.i18n.quickfix.remove.annotation", StringUtil.getShortName(annotationFqn));
    }
  }

  /**
   * Provides container operations for a package.
   * Remove/Add operations are applied to package statement in package-info.java file
   * which is created on the fly if it doesn't exist.
   */
  private static final class PackageContainer extends Container<PsiDirectory> {
    private final PsiDirectory directory;
    private final String packageFqn;

    private PackageContainer(PsiDirectory directory, String packageFqn) {
      super(getPackageInfoModifierList(directory));
      this.directory = directory;
      this.packageFqn = packageFqn;
    }

    private static @Nullable PsiModifierList getPackageInfoModifierList(PsiDirectory directory) {
      directory.findFile(PACKAGE_INFO_FILE);
      var packageInfoFile = directory.findFile(PACKAGE_INFO_FILE);
      if (packageInfoFile == null) return null;
      if (!(packageInfoFile instanceof PsiJavaFile packageInfoJavaFile)) return null;
      var packageStatement = packageInfoJavaFile.getPackageStatement();
      if (packageStatement == null) return null;
      return packageStatement.getAnnotationList();
    }

    @Override
    public Change<PsiDirectory> newChangeThatAddsAnnotation(String annotationFqn) {
      return new Change<>(directory, (updater, dir) -> {
        var packageInfoFile = updater.getWritable(getPackageInfoFile(updater, dir));
        var packageStatement = getPackageStatement(packageInfoFile, dir.getProject());
        var elementFactory = JavaPsiFacade.getElementFactory(dir.getProject());
        var annotation = elementFactory.createAnnotationFromText("@" + annotationFqn, packageStatement);
        var addedAnnotation = packageInfoFile.addBefore(annotation, packageStatement);
        JavaCodeStyleManager.getInstance(dir.getProject()).shortenClassReferences(addedAnnotation);
        CodeStyleManager.getInstance(directory.getProject()).reformat(packageInfoFile);
        updater.moveCaretTo(packageStatement);
        updater.highlight(addedAnnotation);
      });
    }

    private PsiPackageStatement getPackageStatement(PsiFile packageInfoFile, Project project) {
      if (packageInfoFile instanceof PsiJavaFile packageInfoJavaFile) {
        var packageStatement = packageInfoJavaFile.getPackageStatement();
        if (packageStatement != null) return packageStatement;
      }
      var elementFactory = JavaPsiFacade.getElementFactory(project);
      var packageStatement = elementFactory.createPackageStatement(packageFqn);
      return (PsiPackageStatement)packageInfoFile.addBefore(packageStatement, packageInfoFile.getFirstChild());
    }

    private static PsiFile getPackageInfoFile(ModPsiUpdater updater, PsiDirectory directory) {
      var packageInfoFile = directory.findFile(PACKAGE_INFO_FILE);
      if (packageInfoFile == null) {
        packageInfoFile = updater.getWritable(directory).createFile(PACKAGE_INFO_FILE);
      }
      return packageInfoFile;
    }

    @Override
    public Change<PsiDirectory> newChangeThatRemovesAnnotation(String annotationFqn) {
      return new Change<>(directory, (updater, dir) -> {
        var packageInfoFile = updater.getWritable(getPackageInfoFile(updater, dir));
        var packageStatement = getPackageStatement(packageInfoFile, dir.getProject());
        var modifierList = packageStatement.getAnnotationList();
        if (modifierList == null) return;
        removeAnnotation(modifierList, annotationFqn);
      });
    }

    @Override
    public String textForAddAnnotationAction(String annotationFqn) {
      return AddAnnotationPsiFix.calcText(StringUtil.getShortName(annotationFqn), JavaElementKind.PACKAGE, packageFqn);
    }

    @Override
    public String textForRemoveAnnotationAction(String annotationFqn) {
      return createTextForRemoveAnnotationAction(annotationFqn, packageFqn, JavaElementKind.PACKAGE);
    }
  }

  private static void removeAnnotation(PsiAnnotationOwner annotationOwner, String annotationFqn) {
    var annotation = annotationOwner.findAnnotation(annotationFqn);
    if (annotation != null) {
      annotation.delete();
    }
  }

  private static @Nls String createTextForRemoveAnnotationAction(String annotationFqn, String name, JavaElementKind elementKind) {
    String shortName = StringUtil.getShortName(annotationFqn);
    return JavaAnalysisBundle.message("inspection.i18n.quickfix.remove.annotation.from.element", shortName, elementKind.object(), name);
  }
}
