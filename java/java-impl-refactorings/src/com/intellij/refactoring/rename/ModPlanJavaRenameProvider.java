// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl.RenamedClassForm;
import com.intellij.psi.impl.source.resolve.RenamedClassView;
import com.intellij.psi.impl.source.resolve.ResolveAfterRenameUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.ModPlanOptimizeImportsListener;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rename of Java classes, methods, variables and labels on the ModPlan engine. It finds what is renamed and the usages through the
 * legacy processors, and decides before the write what their {@code renameElement} decides after it, as the copy of a file sees the other files
 * under the old names: the form of a reference to a renamed class, the qualifier of a reference that another member takes, the imports
 * left unused, the {@code @Override} of a method that overrides nothing.
 * <p>
 * Each subclass is the provider of one legacy processor. A Java element belongs to the provider of the processor that
 * {@code RenamePsiElementProcessor.forElement} gives. An element that the legacy rename gives to a processor of another plugin, such as
 * a field of a JavaFX controller, has no owner here.
 */
@ApiStatus.Internal
public abstract class ModPlanJavaRenameProvider extends ModPlanBridgeRenameProvider {
  protected ModPlanJavaRenameProvider(@NotNull Class<? extends RenamePsiElementProcessor> processorClass) {
    super(processorClass);
  }

  /** The legacy rename gives {@code subject}, a Java element, to the processor of this provider or to a subclass of it. */
  @Override
  public boolean isOwner(@NotNull SymbolPointer subject) {
    PsiElement element = subject.element();
    return element != null && element.getLanguage().is(JavaLanguage.INSTANCE) &&
           processorClass().isInstance(RenamePsiElementProcessor.forElement(element));
  }

  /**
   * @return the usages of {@code element}, as {@code RenameUtil.findUsages} finds them with the processor, see
   * {@link ModPlanRenameUtil#ownUsages}
   */
  @Override
  protected @NotNull List<UsageInfo> usagesOf(@NotNull PsiElement element,
                                            @NotNull String newName,
                                            @NotNull ModPlanRequest request,
                                            @NotNull Map<PsiElement, String> renamed) {
    List<UsageInfo> ownUsages = super.usagesOf(element, newName, request, renamed);
    Set<UsageInfo> references = Collections.newSetFromMap(new IdentityHashMap<>());
    references.addAll(referencesAmong(ownUsages, renamed));
    List<PsiImportStatementBase> deletedImports = new ArrayList<>();
    for (UsageInfo usage : ownUsages) {
      if (usage instanceof CollidingClassImportUsageInfo info) deletedImports.add(info.getImportStatement());
    }
    List<UsageInfo> result = new ArrayList<>();
    List<UsageInfo> importReferences = new ArrayList<>();
    for (UsageInfo usage : ownUsages) {
      if (!references.contains(usage)) {
        result.add(usage);
        continue;
      }
      // The references of an import go after the others: handleElementRename of a call decides by the import it comes through.
      boolean inImport = PsiTreeUtil.getParentOfType(usage.getElement(), PsiImportStatementBase.class, false) != null;
      if (inImport) {
        importReferences.add(referenceUsageOf(element, newName, usage, renamed, deletedImports));
      }
      else {
        result.add(referenceUsageOf(element, newName, usage, renamed, deletedImports));
      }
    }
    result.addAll(importReferences);
    return result;
  }

  /**
   * @param deletedImports the imports that the edits of {@link CollidingClassImportUsageInfo} delete
   * @return the usage of the reference of {@code usage} in the form that the write gives it, see {@link JavaReferenceUsage}; {@code usage}
   * itself if the reference only gets the new name
   */
  private static @NotNull UsageInfo referenceUsageOf(@NotNull PsiElement element,
                                                     @NotNull String newName,
                                                     @NotNull UsageInfo usage,
                                                     @NotNull Map<PsiElement, String> renamed,
                                                     @NotNull List<PsiImportStatementBase> deletedImports) {
    PsiReference reference = usage.getReference();
    if (reference == null || usage instanceof ResolvableCollisionUsageInfo) return usage;
    if (element instanceof PsiMethod && reference instanceof PsiImportStaticReferenceElement importReference &&
        isImportOfOtherMembers(importReference, newName, renamed)) {
      return new JavaReferenceUsage.SeveralMembersStaticImportUsage(importReference, element);
    }
    if (reference.getElement() != usage.getElement()) return usage;
    if (element instanceof PsiClass aClass && usage.getElement() instanceof PsiJavaCodeReferenceElement classReference) {
      RenamedClassForm form =
        PsiJavaCodeReferenceElementImpl.formAfterRename(classReference, new RenamedClassView(aClass, newName, deletedImports, null));
      if (form != RenamedClassForm.RENAMED) {
        return new JavaReferenceUsage.ClassReferenceUsage(reference, aClass, form);
      }
    }
    if (usage.getElement() instanceof PsiReferenceExpression expression &&
        hiddenAfterRenameEdit(element, newName, expression, renamed) instanceof ModPlanEdit edit) {
      return new JavaReferenceUsage.HiddenMemberUsage(reference, element, edit);
    }
    return usage;
  }

  /**
   * The edits of the rename of {@code element} and of its {@code usages}, as {@code renameElement} of the processor writes them. The
   * collisions are written after the rename, as there: an inserted Java reference binds to what its name means in the copy at that moment.
   * Only the edits of {@link CollidingClassImportUsageInfo} come first, as {@code renameElement} deletes these imports first: the bind of a
   * reference sees the copy without them. A reference that the edit of a collision with a static import writes gets no other edit. The imports that the optimization of the
   * imports decides go last. Refuses the plan if a collision has no edit of its own.
   *
   * <p>A collision usage that does not implement {@link ModPlanWritableUsageInfo} has no edit. The engine would write it as a plain
   * reference, but the legacy rename calls its {@code resolveCollision} after the write. So the plan is refused. Each collision usage of
   * this module has an edit, so only a collision that a processor of another plugin adds is refused.
   *
   * <p>A reference to a constructor, {@code Foo::new}, gets no edit. Its {@code handleElementRename} does nothing, and the reference to
   * the class in front of it has an edit of its own.
   * <pre>{@code
   * class Foo {}
   * Supplier<Foo> factory = Foo::new;
   * // rename Foo to Bar
   * class Bar {}
   * Supplier<Bar> factory = Bar::new;   // the edit of the class reference writes Bar, ::new stays
   * }</pre>
   */
  @Override
  protected @NotNull ModPlanAnalysisResult analyze(@NotNull PsiElement element,
                                                   @NotNull String newName,
                                                   @NotNull List<UsageInfo> usages,
                                                   @NotNull Map<PsiElement, String> renamed) {
    if (ContainerUtil.exists(referencesAmong(usages, renamed), usage -> usage instanceof ResolvableCollisionUsageInfo)) {
      return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED);
    }
    Set<PsiElement> writtenByCollisions = new HashSet<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof MemberHidesStaticImportUsageInfo info && renamed.containsKey(info.getReferencedElement())) {
        writtenByCollisions.add(usage.getElement());
      }
    }
    // The usages that get no edit of their own:
    // 1. A reference that the edit of a collision with a static import writes. One run renames B.log and p.Util.format to print:
    //      import static p.Util.format;
    //      class A { static class B { static void log() {} static void use() { format(); } } }
    //    The edit of the collision writes Util.print() at the call, so the call gets no edit as a usage of format.
    // 2. A reference to a constructor: Foo::new stays, and the edit of the class reference Foo writes Bar, see the Javadoc.
    // 3. A reference of another language through an import alias, see ModPlanRenameAliasFilter. Groovy gives:
    //      import p.Widget as W
    //      print new W()      // rename Widget to Gadget: W stays
    List<UsageInfo> keptUsages = ContainerUtil.filter(usages, usage -> usage instanceof ModPlanWritableUsageInfo ||
                                                                       usage instanceof NonCodeUsageInfo ||
                                                                       !writtenByCollisions.contains(usage.getElement()) &&
                                                                       !(usage.getElement() instanceof PsiMethodReferenceExpression reference &&
                                                                         reference.isConstructor()) &&
                                                                       !(element instanceof PsiClass aClass && isAliasReference(usage, aClass)));
    List<ModPlanEdit> edits = new ArrayList<>();
    for (UsageInfo usage : keptUsages) {
      if (usage instanceof CollidingClassImportUsageInfo info) edits.addAll(info.asModPlanEdits(newName, renamed));
    }
    ModPlanAnalysisResult result =
      super.analyze(element, newName, ContainerUtil.filter(keptUsages, usage -> !(usage instanceof CollidingClassImportUsageInfo)), renamed);
    if (!(result instanceof ModPlanAnalysisResult.Found(List<ModPlanEdit> foundEdits, ModPlanConflicts conflicts))) return result;
    edits.addAll(foundEdits);
    if (element instanceof PsiClass aClass) {
      edits.addAll(keepReferentEdits(aClass, newName));
    }
    if (element instanceof PsiMethod method &&
        RenameJavaMethodProcessor.overrideAfterRename(method, newName, renamed) instanceof PsiAnnotation override) {
      edits.add(ModPlanEdit.delete(override));
    }
    edits.addAll(importEdits(keptUsages, renamed));
    return new ModPlanAnalysisResult.Found(edits, conflicts);
  }

  private static boolean isAliasReference(@NotNull UsageInfo usage, @NotNull PsiClass aClass) {
    return ModPlanRenameAliasFilter.EP_NAME.findFirstSafe(filter -> filter.isAliasReference(usage, aClass)) != null;
  }

  /** @return the edit that qualifies {@code occurrence}, a reference to a member of {@code containingClass}, and gives it {@code newName} */
  static @NotNull ModPlanEdit qualifiedMemberEdit(@NotNull PsiElement occurrence,
                                                  @NotNull String newName,
                                                  @NotNull PsiClass containingClass,
                                                  boolean isStatic) {
    return ModPlanEdit.update(occurrence, copy -> RenameJavaMemberProcessor.qualifyMember(copy, newName, containingClass, isStatic));
  }

  /**
   * @param shorten whether the written name may become shorter, as an inserted reference does where its short name means the class; right
   *                in the file of the renamed class only, whose copy has the rename
   * @return the edit that writes {@code name} instead of the name of {@code reference}, with its type arguments and annotations
   */
  static @NotNull ModPlanEdit nameEdit(@NotNull PsiJavaCodeReferenceElement reference, @NotNull String name, boolean shorten) {
    return ModPlanEdit.update(reference, copy -> {
      List<PsiAnnotation> annotations = PsiTreeUtil.getChildrenOfTypeAsList(copy, PsiAnnotation.class);
      String annotatedName = annotations.isEmpty() ? name : StringUtil.getQualifiedName(
        StringUtil.getPackageName(name), StringUtil.join(annotations, PsiElement::getText, " ") + " " + StringUtil.getShortName(name));
      PsiReferenceParameterList parameters = copy.getParameterList();
      String text = annotatedName + (parameters == null ? "" : parameters.getText());
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(copy.getProject());
      PsiElement replacement = copy instanceof PsiReferenceExpression ? factory.createExpressionFromText(text, copy)
                                                                      : factory.createReferenceFromText(text, copy);
      if (shorten) {
        copy.replace(replacement);
      }
      else {
        // As PsiJavaCodeReferenceElementImpl.bindToClass writes the qualified name: replace() would shorten it by what it resolves to in
        // this copy, which does not see the class renamed in another file.
        ((CompositeElement)copy.getNode().getTreeParent()).replaceChildInternal(copy.getNode(), (TreeElement)replacement.getNode());
      }
    });
  }

  /**
   * What {@code ChangeContextUtil} keeps in {@code renameElement}: a reference in the scope of the renamed class to another class of the
   * new name gets the qualified name of that class. Without it, the reference would mean the renamed class after the rename, and the code
   * would change its meaning. The scope is the file of a top-level class, or the top-level class of a nested one.
   * <pre>{@code
   * import java.io.*;
   * class Loader { Reader reader; }
   * // rename Loader to Reader
   * class Reader { java.io.Reader reader; }
   * }</pre>
   */
  private static @NotNull List<ModPlanEdit> keepReferentEdits(@NotNull PsiClass aClass, @NotNull String newName) {
    PsiElement scope = aClass.getContainingClass() != null ? PsiTreeUtil.getTopmostParentOfType(aClass, PsiClass.class)
                                                           : aClass.getContainingFile();
    List<ModPlanEdit> result = new ArrayList<>();
    if (scope == null) return result;
    for (PsiJavaCodeReferenceElement reference : PsiTreeUtil.findChildrenOfType(scope, PsiJavaCodeReferenceElement.class)) {
      if (!reference.isQualified() && newName.equals(reference.getReferenceName()) && reference.resolve() instanceof PsiClass referenced &&
          referenced != aClass && referenced.getQualifiedName() != null) {
        result.add(nameEdit(reference, referenced.getQualifiedName(), true));
      }
    }
    return result;
  }

  /**
   * What {@code fixNameCollisionsWithInnerClassMethod} and {@code fixPossibleNameCollisionsForFieldRenaming} find after the write: an
   * occurrence that another member takes under the new name gets the qualifier of the renamed member.
   *
   * <p>A method: an inner class has a method of the new name, and an outer class inherits the renamed method. The occurrence gets the
   * outer class as the qualifier.
   * <pre>{@code
   * class RenameCollisions {
   *   public void method() {}
   *   class InnerClass {
   *     public void innerMethod() {}
   *     void m() { method(); }
   *   }
   * }
   * // rename RenameCollisions.method to innerMethod
   * class RenameCollisions {
   *   public void innerMethod() {}
   *   class InnerClass {
   *     public void innerMethod() {}
   *     void m() { RenameCollisions.this.innerMethod(); }
   *   }
   * }
   * }</pre>
   *
   * <p>A field: a local variable or another field has the new name, so the occurrence would refer to it. It gets the class of the field as
   * the qualifier.
   * <pre>{@code
   * class RenameCollisions {
   *   private int myField = 7;
   *   private class InnerClass {
   *     void m() { int localVar3 = 0; int sum = localVar3 + myField; }
   *   }
   * }
   * // rename myField to localVar3
   * class RenameCollisions {
   *   private int localVar3 = 7;
   *   private class InnerClass {
   *     void m() { int localVar3 = 0; int sum = localVar3 + RenameCollisions.this.localVar3; }
   *   }
   * }
   * }</pre>
   *
   * <p>The result is null, as the occurrence keeps its form, if it has a qualifier, if nothing else takes the new name at the place, or
   * if the renamed element is not a member. Here {@code InnerClass} has no member of the new name:
   * <pre>{@code
   * class RenameCollisions {
   *   public static final int STATIC_FIELD = 5;
   *   private class InnerClass {
   *     public static final int INNER_STATIC_FIELD = 13;
   *     void m() { int var1 = INNER_STATIC_FIELD + STATIC_FIELD; }
   *   }
   * }
   * // rename RenameCollisions.STATIC_FIELD to SI_STATIC_FIELD
   * class RenameCollisions {
   *   public static final int SI_STATIC_FIELD = 5;
   *   private class InnerClass {
   *     public static final int INNER_STATIC_FIELD = 13;
   *     void m() { int var1 = INNER_STATIC_FIELD + SI_STATIC_FIELD; }
   *   }
   * }
   * }</pre>
   *
   * @return null if the occurrence keeps its form
   */
  private static @Nullable ModPlanEdit hiddenAfterRenameEdit(@NotNull PsiElement element,
                                                            @NotNull String newName,
                                                            @NotNull PsiReferenceExpression occurrence,
                                                            @NotNull Map<PsiElement, String> renamed) {
    if (occurrence.getQualifierExpression() != null || !(element instanceof PsiMember member)) return null;
    boolean isStatic = member.hasModifierProperty(PsiModifier.STATIC);
    if (element instanceof PsiMethod) {
      List<PsiMethod> renamedMethods =
        ContainerUtil.filter(ContainerUtil.filterIsInstance(renamed.keySet(), PsiMethod.class), method -> method.getContainingClass() != null);
      PsiClass methodClass = ResolveAfterRenameUtil.methodClass(renamedMethods, newName, occurrence);
      if (methodClass == null || inheritsRenamed(methodClass, renamedMethods)) return null;
      for (PsiClass outer = PsiTreeUtil.getParentOfType(methodClass, PsiClass.class); outer != null;
           outer = PsiTreeUtil.getParentOfType(outer, PsiClass.class)) {
        if (inheritsRenamed(outer, renamedMethods)) {
          return qualifiedMemberEdit(occurrence, newName, outer, isStatic);
        }
      }
      return null;
    }
    if (!(element instanceof PsiField field) || !(field.getContainingClass() instanceof PsiClass fieldClass)) return null;
    return switch (ResolveAfterRenameUtil.resolveField(field, newName, occurrence).kind()) {
      case RENAMED, NONE -> null;
      case OTHER, AMBIGUOUS -> qualifiedMemberEdit(occurrence, newName, fieldClass, isStatic);
    };
  }

  private static boolean inheritsRenamed(@NotNull PsiClass aClass, @NotNull List<PsiMethod> renamedMethods) {
    return ContainerUtil.exists(renamedMethods, method -> InheritanceUtil.isInheritorOrSelf(aClass, method.getContainingClass(), true));
  }

  /** As the legacy rename of a method: a static import of several members keeps the name of the ones that keep it. */
  private static boolean isImportOfOtherMembers(@NotNull PsiImportStaticReferenceElement reference,
                                                @NotNull String newName,
                                                @NotNull Map<PsiElement, String> renamed) {
    JavaResolveResult[] results = reference.multiResolve(false);
    return results.length > 1 && ContainerUtil.exists(results, result -> !newName.equals(renamed.get(result.getElement())));
  }

  /**
   * The edits of the imports that the optimization of the imports after the write decides, as the copy does not see the renamed elements
   * of the other files. An import on demand that a short reference comes through is kept, see
   * {@link ModPlanOptimizeImportsListener#keepImportEdit}. A single import of a renamed element is deleted in a file where every
   * reference to it is qualified now: by its form, see {@link JavaReferenceUsage#isQualified}, or by a collision with a static import.
   * The examples rename {@code p.Old} to {@code New}.
   * <p>
   * The copy does not see {@code p.New}, so the optimization would delete the import on demand. The edit keeps it.
   * <pre>{@code
   * package q; import p.*; class Use { Old o; }
   * // after the rename
   * package q; import p.*; class Use { New o; }
   * }</pre>
   * The member class {@code New} takes the short name, so the reference gets the qualified name, and the single import is unused.
   * <pre>{@code
   * package q; import p.Old; class Use { class New {} Old o; }
   * // after the rename
   * package q; class Use { class New {} p.New o; }
   * }</pre>
   * The user excluded the single import from the rename, so it keeps the old name. The short reference gets the new name, and an import of
   * it is added.
   * <pre>{@code
   * package q; import p.Old; class Use { Old o; }
   * // after the rename
   * package q; import p.Old; import p.New; class Use { New o; }
   * }</pre>
   *
   * @return the edits that keep an import, then the deletes
   */
  private static @NotNull List<ModPlanEdit> importEdits(@NotNull List<UsageInfo> usages, @NotNull Map<PsiElement, String> renamed) {
    Set<PsiFile> qualifiedIn = new HashSet<>();
    Set<PsiFile> shortIn = new HashSet<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof JavaReferenceUsage referenceUsage && referenceUsage.isQualified() ||
          usage instanceof MemberHidesStaticImportUsageInfo info && renamed.containsKey(info.getReferencedElement())) {
        qualifiedIn.add(usage.getFile());
      }
      else if (usage instanceof JavaReferenceUsage.ClassReferenceUsage classUsage && classUsage.isShortened()) {
        shortIn.add(usage.getFile());
      }
    }
    Map<PsiImportStatementBase, PsiFile> singleImports = new LinkedHashMap<>();
    Map<PsiImportStatementBase, ModPlanEdit> keptImports = new LinkedHashMap<>();
    Map<PsiImportStatementBase, String> singleImportsOfShortReferences = new LinkedHashMap<>();
    for (UsageInfo usage : referencesAmong(usages, renamed)) {
      if (usage.isNonCodeUsage || !(usage.getElement() instanceof PsiJavaCodeReferenceElement reference)) continue;
      if (reference.getParent() instanceof PsiImportStatementBase statement && !statement.isOnDemand()) {
        singleImports.put(statement, usage.getFile());
      }
      else if (!reference.isQualified()) {
        shortIn.add(usage.getFile());
        JavaResolveResult resolveResult = reference.advancedResolve(false);
        if (!(resolveResult.getCurrentFileResolveScope() instanceof PsiImportStatementBase statement)) continue;
        if (statement.isOnDemand()) {
          keptImports.computeIfAbsent(statement, ModPlanOptimizeImportsListener::keepImportEdit);
        }
        else if (resolveResult.getElement() instanceof PsiClass aClass && renamed.get(aClass) instanceof String newName) {
          singleImportsOfShortReferences.put(statement, PsiUtilCore.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName));
        }
      }
    }
    List<ModPlanEdit> result = new ArrayList<>(keptImports.values());
    // As bindToElement in the legacy rename: a short reference whose single import keeps the old name gets an import of the new name.
    singleImportsOfShortReferences.forEach((statement, qualifiedName) -> {
      if (singleImports.containsKey(statement) || !(statement.getContainingFile() instanceof PsiJavaFile file)) return;
      result.add(ModPlanOptimizeImportsListener.keepImportEdit(statement));
      result.add(JavaReferenceUsage.singleImportEdit(file, qualifiedName));
    });
    singleImports.forEach((statement, file) -> {
      if (qualifiedIn.contains(file) && !shortIn.contains(file)) result.add(ModPlanEdit.delete(statement));
    });
    return result;
  }

  /**
   * The edits of the declaration; none for the implicit accessor of a record component, see {@link #prepareRenaming}. A class gets the new name
   * with its constructors, and its file, if it has the name of the class, is moved to the new name: the copy cannot rename the file.
   */
  @Override
  protected @NotNull List<ModPlanEdit> declarationEdits(@NotNull PsiElement element, @NotNull String newName) {
    if (element instanceof LightRecordMethod) return List.of();
    if (!(element instanceof PsiClass aClass) || aClass.getNameIdentifier() == null) {
      return List.of(ModPlanRenameUtil.declarationEdit(element, newName));
    }
    String oldName = aClass.getName();
    List<ModPlanEdit> result = new ArrayList<>();
    result.add(ModPlanEdit.update(aClass, copy -> copy.setName(newName)));
    if (aClass.getParent() instanceof PsiFile file && FileUtilRt.getNameWithoutExtension(file.getName()).equals(oldName)) {
      String extension = FileUtilRt.getExtension(file.getName());
      result.add(ModPlanRenameUtil.declarationEdit(file, extension.isEmpty() ? newName : newName + "." + extension));
    }
    return result;
  }

  /** The rename of a label, which the default processor renames. */
  @ApiStatus.Internal
  public static final class ModPlanJavaLabelRenameProvider extends ModPlanJavaRenameProvider {
    public ModPlanJavaLabelRenameProvider() {
      super(RenamePsiElementProcessor.DEFAULT.getClass());
    }

    @Override
    public boolean isOwner(@NotNull SymbolPointer subject) {
      return super.isOwner(subject) && subject.element() instanceof PsiLabeledStatement;
    }
  }
}
