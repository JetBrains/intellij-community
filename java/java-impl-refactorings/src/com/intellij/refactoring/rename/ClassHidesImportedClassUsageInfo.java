// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl.RenamedClassForm;
import com.intellij.psi.impl.source.resolve.RenamedClassView;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanWritableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A reference to an imported class whose simple name the renamed class takes. After the rename the name means the renamed class, so the
 * write gives the reference the qualified name of the imported class.
 *
 * <pre>{@code
 * // pack2/Usage.java
 * import java.util.List;
 * import pack1.*;
 * class Usage { void m() { List list = null; MyList myList = null; } }
 * // rename pack1.MyList to List
 * import pack1.*;
 * class Usage { void m() { java.util.List list = null; List myList = null; } }
 * }</pre>
 */
public class ClassHidesImportedClassUsageInfo extends ResolvableCollisionUsageInfo implements ModPlanWritableUsageInfo {
  private final PsiClass myHiddenClass;
  private final PsiJavaCodeReferenceElement myCollisionReference;

  public ClassHidesImportedClassUsageInfo(PsiJavaCodeReferenceElement collisionReference, PsiClass renamedClass, PsiClass hiddenClass) {
    super(collisionReference, renamedClass);
    myHiddenClass = hiddenClass;
    myCollisionReference = collisionReference;
  }

  public void resolveCollision() throws IncorrectOperationException {
    if (!myCollisionReference.isValid()) return;
    final PsiManager manager = myCollisionReference.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

    String qualifiedName = myHiddenClass.getQualifiedName();
    if (qualifiedName == null) return;
    if (myCollisionReference instanceof PsiReferenceExpression) {
      myCollisionReference.replace(factory.createExpressionFromText(qualifiedName, myCollisionReference));
    } else {
      myCollisionReference.bindToElement(myHiddenClass);
    }
  }

  /**
   * The reference keeps the hidden class: its qualified name, shortened in the file of the renamed class, whose copy has the rename. In
   * another file a reference that the rename qualifies anyway keeps it as it is.
   *
   * <pre>{@code
   * // the file of the renamed class
   * import java.io.*;
   * class Loader { Reader reader; }
   * // rename Loader to Reader
   * import java.io.*;
   * class Reader { java.io.Reader reader; }
   * }</pre>
   */
  @Override
  public @NotNull List<ModPlanEdit> asModPlanEdits(@NotNull String newName, @NotNull Map<PsiElement, String> renamed) {
    if (!(getElement() instanceof PsiJavaCodeReferenceElement reference) || !(reference.resolve() instanceof PsiClass hidden) ||
        hidden.getQualifiedName() == null) {
      return List.of();
    }
    PsiElement renamedClass = getReferencedElement();
    boolean inRenamedFile = renamedClass != null && reference.getContainingFile() == renamedClass.getContainingFile();
    if (!inRenamedFile && renamedClass instanceof PsiClass aClass) {
      RenamedClassView view = new RenamedClassView(aClass, newName, collidingImports(reference, hidden), null);
      if (PsiJavaCodeReferenceElementImpl.formAfterRename(reference, view) == RenamedClassForm.QUALIFIED) return List.of();
    }
    return List.of(ModPlanJavaRenameProvider.nameEdit(reference, hidden.getQualifiedName(), inRenamedFile));
  }

  /** @return the single import of {@code hidden} in the file of {@code reference}, which {@link CollidingClassImportUsageInfo} deletes */
  private static @NotNull List<PsiImportStatementBase> collidingImports(@NotNull PsiJavaCodeReferenceElement reference,
                                                                        @NotNull PsiClass hidden) {
    PsiImportList importList = reference.getContainingFile() instanceof PsiJavaFile file ? file.getImportList() : null;
    PsiImportStatement statement = importList == null ? null : importList.findSingleClassImportStatement(hidden.getQualifiedName());
    return statement == null ? List.of() : List.of(statement);
  }
}
