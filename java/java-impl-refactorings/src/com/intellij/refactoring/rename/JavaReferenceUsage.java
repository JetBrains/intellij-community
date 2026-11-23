// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl.RenamedClassForm;
import com.intellij.psi.impl.source.resolve.RenamedClassView;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A reference to a renamed Java element that the write leaves in another form than the new name. The search decides the form, as the
 * collision classes decide their edits, see {@link ModPlanJavaRenameProvider#usagesOf}.
 */
abstract class JavaReferenceUsage extends MoveRenameUsageInfo implements ModPlanWritableUsageInfo {
  JavaReferenceUsage(@NotNull PsiReference reference, @NotNull PsiElement referencedElement) {
    super(reference, referencedElement);
  }

  /** @return whether the write qualifies the reference, so that a single import of the renamed element in the file may be unused */
  abstract boolean isQualified();

  /**
   * A reference to the renamed class that does not keep its form: the write qualifies it, shortens it, or shortens it with a single
   * import. The search decides the form for the edits of the imports, see {@link PsiJavaCodeReferenceElementImpl#formAfterRename}. The
   * write binds the reference to a {@link RenamedClassView}, which decides the form again in the copy.
   * <pre>{@code
   * package q; class Use { p.Old o; }
   * // rename p.Old to New
   * package q; import p.New; class Use { New o; }
   * }</pre>
   */
  static final class ClassReferenceUsage extends JavaReferenceUsage {
    private final @NotNull RenamedClassForm myForm;

    ClassReferenceUsage(@NotNull PsiReference reference, @NotNull PsiClass aClass, @NotNull RenamedClassForm form) {
      super(reference, aClass);
      myForm = form;
    }

    @Override
    boolean isQualified() {
      return myForm == RenamedClassForm.QUALIFIED;
    }

    /** @return whether the write shortens the reference, so that it needs the single import of the renamed class in the file */
    boolean isShortened() {
      return myForm == RenamedClassForm.SHORT;
    }

    /**
     * The edit that binds the reference to the view of the class. In the file of the class the edit also takes the class: its copy has the
     * new name already, and the view must know it.
     */
    @Override
    public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
      if (!(getElement() instanceof PsiJavaCodeReferenceElement reference) || !(getReferencedElement() instanceof PsiClass aClass)) {
        return List.of();
      }
      if (reference.getContainingFile() != aClass.getContainingFile()) {
        return List.of(ModPlanEdit.update(reference, copy -> copy.bindToElement(new RenamedClassView(aClass, newName, List.of(), null))));
      }
      return List.of(ModPlanEdit.update(List.of(reference, aClass), copies -> {
        RenamedClassView view = new RenamedClassView(aClass, newName, List.of(), (PsiClass)copies.get(1));
        ((PsiJavaCodeReferenceElement)copies.getFirst()).bindToElement(view);
      }));
    }
  }

  /** @return the edit that adds a single import of {@code qualifiedName} to the copy of {@code file}, unless the copy has one */
  static @NotNull ModPlanEdit singleImportEdit(@NotNull PsiJavaFile file, @NotNull String qualifiedName) {
    return ModPlanEdit.update(file, copy -> {
      PsiImportList importList = copy.getImportList();
      if (importList == null || importList.findSingleClassImportStatement(qualifiedName) != null) return;
      PsiFile holder = PsiFileFactory.getInstance(copy.getProject())
        .createFileFromText("_Dummy_.java", JavaFileType.INSTANCE, "import " + qualifiedName + ";");
      PsiImportList holderImports = ((PsiJavaFile)holder).getImportList();
      if (holderImports != null) importList.add(holderImports.getImportStatements()[0]);
    });
  }

  /**
   * An unqualified reference to the renamed field or method that another member takes under the new name. The write qualifies it, see
   * {@code hiddenAfterRenameEdit}.
   *
   * <pre>{@code
   * class RenameCollisions {
   *   public void method() {}
   *   class InnerClass {
   *     public void innerMethod() {}
   *     public void instanceContext() { method(); innerMethod(); }
   *   }
   * }
   * // rename RenameCollisions.method to innerMethod
   * class RenameCollisions {
   *   public void innerMethod() {}
   *   class InnerClass {
   *     public void innerMethod() {}
   *     public void instanceContext() { RenameCollisions.this.innerMethod(); innerMethod(); }
   *   }
   * }
   * }</pre>
   */
  static final class HiddenMemberUsage extends JavaReferenceUsage {
    private final @NotNull ModPlanEdit myEdit;

    HiddenMemberUsage(@NotNull PsiReference reference, @NotNull PsiElement referencedElement, @NotNull ModPlanEdit edit) {
      super(reference, referencedElement);
      myEdit = edit;
    }

    @Override
    boolean isQualified() {
      return true;
    }

    @Override
    public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
      return List.of(myEdit);
    }
  }

  /**
   * A static import of several members, of which some keep their names: it keeps its name, as in the legacy rename of a method. It stays
   * if the file uses a member that keeps the name. It goes if the rename leaves it with one member, which no reference of the file uses.
   *
   * <pre>{@code
   * package pack1;
   * public class A {
   *   public static void staticMethod(int i) {}
   *   public static void staticMethod(String s) {}
   * }
   *
   * package pack2;
   * import static pack1.A.staticMethod;
   * class Usage { { staticMethod(27); } }
   *
   * // rename A.staticMethod(int) to renamedStaticMethod
   * package pack2;
   * import pack1.A;
   * class Usage { { A.renamedStaticMethod(27); } }
   * }</pre>
   */
  static final class SeveralMembersStaticImportUsage extends JavaReferenceUsage {
    SeveralMembersStaticImportUsage(@NotNull PsiReference reference, @NotNull PsiElement referencedElement) {
      super(reference, referencedElement);
    }

    @Override
    boolean isQualified() {
      return false;
    }

    @Override
    public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
      if (!(getReference() instanceof PsiImportStaticReferenceElement reference)) return List.of();
      return ContainerUtil.createMaybeSingletonList(unusedStaticImportEdit(reference, newName, renamed));
    }
  }

  /**
   * The edit that deletes such an import if the rename leaves it unused: after the write it imports one member, which no reference of the
   * file uses. The copy still sees the renamed members, so it keeps the import.
   */
  private static @Nullable ModPlanEdit unusedStaticImportEdit(@NotNull PsiImportStaticReferenceElement reference,
                                                      @NotNull String newName,
                                                      @NotNull Map<PsiElement, String> renamed) {
    if (!(reference.getParent() instanceof PsiImportStaticStatement statement)) return null;
    List<PsiElement> keptMembers = new ArrayList<>();
    for (JavaResolveResult result : reference.multiResolve(false)) {
      if (!newName.equals(renamed.get(result.getElement()))) keptMembers.add(result.getElement());
    }
    if (keptMembers.size() != 1 || keptMembers.getFirst() == null) return null;
    for (PsiReference used : ReferencesSearch.search(keptMembers.getFirst(), new LocalSearchScope(statement.getContainingFile())).findAll()) {
      if (PsiTreeUtil.getParentOfType(used.getElement(), PsiImportStatementBase.class, false) == null) return null;
    }
    return ModPlanEdit.delete(statement);
  }
}
