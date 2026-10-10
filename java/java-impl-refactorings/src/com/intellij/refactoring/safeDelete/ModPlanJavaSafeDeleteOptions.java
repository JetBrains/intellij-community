// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import com.intellij.refactoring.modplan.ModPlanOption;

/**
 * The options of the Java safe delete, besides those of {@link com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions}.
 */
@ApiStatus.Experimental
public final class ModPlanJavaSafeDeleteOptions {
  /**
   * The general answer of the question "delete the getters and setters of the field too?" for every deleted field. Only a request sets
   * it; the dialog answers with {@link #ACCESSORS_OF}. An explicit value answers the question of every field. The names of
   * {@link #ACCESSORS_OF} narrow a true.
   */
  public static final @NotNull ModPlanOption<Boolean> DELETE_ACCESSORS =
    ModPlanOption.bool("javaDeleteAccessors", true, JavaRefactoringBundle.messagePointer("delete.accessors"));
  /**
   * The fields whose accessors are deleted, one {@link com.intellij.psi.util.PsiFormatUtil#getExternalName} per line: a field of a list
   * that names only other fields keeps its accessors. The answers of the dialogs add up to this list, with the prefix {@code -} for an
   * asked one that is not chosen. {@link #DELETE_ACCESSORS} false keeps them anyway.
   */
  public static final @NotNull ModPlanOption<String> ACCESSORS_OF =
    ModPlanOption.text("javaAccessorsOf", "", JavaRefactoringBundle.messagePointer("delete.accessors"));
  /**
   * The general answer of the question "the method overrides a method, delete the base methods too?" for every deleted method: true
   * deletes them, false keeps them. Only a request sets it; the dialog answers with {@link #SUPER_METHODS_OF}. An explicit value answers
   * the question of every method. The names of {@link #SUPER_METHODS_OF} narrow a true; with no names and no explicit value, the method
   * is deleted alone.
   */
  public static final @NotNull ModPlanOption<Boolean> DELETE_SUPER_METHODS =
    ModPlanOption.bool("javaDeleteSuperMethods", false, JavaRefactoringBundle.messagePointer("delete.super.methods"));
  /**
   * The methods whose base methods are deleted, one {@link com.intellij.psi.util.PsiFormatUtil#getExternalName} per line: a method of a
   * list that names only other methods keeps its base methods. The answers of the dialogs add up to this list, with the prefix {@code -}
   * for an asked one that is not chosen. {@link #DELETE_SUPER_METHODS} false keeps them anyway.
   */
  public static final @NotNull ModPlanOption<String> SUPER_METHODS_OF =
    ModPlanOption.text("javaSuperMethodsOf", "", JavaRefactoringBundle.messagePointer("delete.super.methods"));
  /**
   * The general answer about the unused overriding methods of every deleted method: true deletes them all, false keeps them all. Only a
   * request sets it, such as the one of the MCP tool; the dialog answers with {@link #OVERRIDERS_TO_DELETE}. An explicit value answers the
   * question of every method. The names of {@link #OVERRIDERS_TO_DELETE} narrow a true; with no names and no explicit value, the overriding
   * methods are deleted.
   */
  public static final @NotNull ModPlanOption<Boolean> DELETE_OVERRIDERS =
    ModPlanOption.bool("javaDeleteOverriders", true, JavaRefactoringBundle.messagePointer("unused.overriding.methods.title"));
  /**
   * The overriding methods to delete, one {@link com.intellij.psi.util.PsiFormatUtil#getExternalName} per line: a method whose overriding
   * methods are named here deletes only them, and a method of a list that names only other methods keeps them all. The answers of the
   * dialogs add up to this list, with the prefix {@code -} for an offered one that is not chosen. {@link #DELETE_OVERRIDERS} false keeps
   * them all anyway.
   */
  public static final @NotNull ModPlanOption<String> OVERRIDERS_TO_DELETE =
    ModPlanOption.text("javaOverridersToDelete", "", JavaRefactoringBundle.messagePointer("unused.overriding.methods.title"));
  /**
   * The general answer about the unused callees of every deleted member: true deletes them all, false keeps them all. Only a request sets
   * it; the dialog answers with {@link #CALLEES_TO_DELETE}. An explicit value answers the question of every member. The names of
   * {@link #CALLEES_TO_DELETE} narrow a true; with no names and no explicit value, the callees stay.
   */
  public static final @NotNull ModPlanOption<Boolean> DELETE_UNUSED_CALLEES =
    ModPlanOption.bool("javaDeleteUnusedCallees", false,
                       JavaRefactoringBundle.messagePointer("safe.delete.select.members.to.propagate.dialog.title"));
  /**
   * The unused callees to delete, one {@link com.intellij.psi.util.PsiFormatUtil#getExternalName} per line, the nested ones included: a
   * member whose callees are named here deletes only them. The answers of the dialogs add up to this list, with the prefix {@code -} for an
   * offered one that is not chosen. {@link #DELETE_UNUSED_CALLEES} false keeps them all anyway.
   */
  public static final @NotNull ModPlanOption<String> CALLEES_TO_DELETE =
    ModPlanOption.text("javaCalleesToDelete", "",
                       JavaRefactoringBundle.messagePointer("safe.delete.select.members.to.propagate.dialog.title"));
  /**
   * The general answer about the callers whose parameter only passes on the deleted parameter or field: true deletes the parameter in
   * them all, false keeps it in them all. Only a request sets it; the dialog answers with {@link #CALLERS_TO_DELETE_PARAMETER_IN}. An
   * explicit value answers the question of every parameter. The names of {@link #CALLERS_TO_DELETE_PARAMETER_IN} narrow a true; with no
   * names and no explicit value, the callers keep the parameter, as the chooser starts unchecked.
   */
  public static final @NotNull ModPlanOption<Boolean> DELETE_PARAMETER_IN_CALLERS =
    ModPlanOption.bool("javaDeleteParameterInCallers", false,
                       JavaRefactoringBundle.messagePointer("safe.delete.select.methods.to.propagate.delete.parameters.dialog.title"));
  /**
   * The callers whose parameter is deleted, one {@link com.intellij.psi.util.PsiFormatUtil#getExternalName} per line, the callers of the
   * callers included: a parameter whose callers are named here is deleted only in them. The answers of the dialogs add up to this list,
   * with the prefix {@code -} for an offered one that is not chosen. {@link #DELETE_PARAMETER_IN_CALLERS} false keeps the parameter in
   * them all anyway.
   */
  public static final @NotNull ModPlanOption<String> CALLERS_TO_DELETE_PARAMETER_IN =
    ModPlanOption.text("javaCallersToDeleteParameterIn", "",
                       JavaRefactoringBundle.messagePointer("safe.delete.select.methods.to.propagate.delete.parameters.dialog.title"));

  private ModPlanJavaSafeDeleteOptions() {
  }
}
