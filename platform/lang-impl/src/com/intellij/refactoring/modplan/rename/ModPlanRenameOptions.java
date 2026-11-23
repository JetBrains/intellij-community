// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rename options that every language may read.
 */
public final class ModPlanRenameOptions {
  public static final @NotNull ModPlanOption<Boolean> SEARCH_IN_COMMENTS =
    ModPlanOption.bool("searchInComments", false, RefactoringBundle.messagePointer("search.in.comments.and.strings"));
  public static final @NotNull ModPlanOption<Boolean> SEARCH_TEXT_OCCURRENCES =
    ModPlanOption.bool("searchTextOccurrences", false, RefactoringBundle.messagePointer("search.for.text.occurrences"));
  /** The new name of the requested subject: the caller sets it, see {@link ModPlanRenameRefactoring#request}. */
  public static final @NotNull ModPlanOption<String> NEW_NAME =
    ModPlanOption.text("newName", "", RefactoringBundle.messagePointer("automatic.renaming.dialog.new.name.label"));
  /**
   * @param index the index of a subject of the request after the first one
   * @return the option of the new name of that subject: the caller sets it, see {@link ModPlanRenameRefactoring#request}
   */
  public static @NotNull ModPlanOption<String> newNameOfSubject(int index) {
    return ModPlanOption.text("newName" + index, "", RefactoringBundle.messagePointer("automatic.renaming.dialog.new.name.label"));
  }

  /**
   * Whether the provider may rename another subject instead of the requested one, such as the base method of an override, and ask
   * about it. A caller that asked what is renamed with its own UI, such as the legacy rename dialog, turns it off.
   */
  public static final @NotNull ModPlanOption<Boolean> SUBSTITUTE_TARGET =
    ModPlanOption.bool("substituteTarget", true, RefactoringBundle.messagePointer("rename.title"));
  /**
   * Whether the related elements are renamed at all, such as the variables named after a renamed class: off, no automatic renamer runs,
   * not even one with no {@link #optionForRenamer option}. Off by default, so a caller with no user renames the element alone; the IDE
   * keeps it in {@code RefactoringSettings.RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG}, without which the legacy rename leaves them too.
   */
  public static final @NotNull ModPlanOption<Boolean> RENAME_RELATED =
    ModPlanOption.bool("renameRelated", false, RefactoringBundle.messagePointer("rename.related.elements.option.name"));
  /**
   * The answer of the choice of the related renames, such as the variables named after a renamed class: on, only the ones of
   * {@link #RELATED_RENAMES} are made; off, every suggested one is, as the legacy rename does in a unit test. With no explicit value, a
   * value of {@link #RELATED_RENAMES} turns it on: the fallback form of a client such as LSP sets only that option.
   */
  public static final @NotNull ModPlanOption<Boolean> CHOOSE_RELATED =
    ModPlanOption.bool("chooseRelated", false, RefactoringBundle.messagePointer("automatic.renaming.dialog.rename.selected.title"));
  /**
   * The chosen related renames, one per line: {@link #relatedRename} of the element and its new name, empty for an element that keeps its
   * name. A line with the prefix {@code -} also keeps the name, see {@link ModPlanOptionValues#withChosenKeys}. Set by a custom question,
   * see {@link #CHOOSE_RELATED}.
   */
  public static final @NotNull ModPlanOption<String> RELATED_RENAMES =
    ModPlanOption.text("relatedRenames", "", RefactoringBundle.messagePointer("automatic.renamer.rename.to.column"));
  /**
   * Whether a usage in a read-only file keeps its old name and the rest is renamed. Off by default: the write of a plan with such a
   * usage is refused. A read-only file with a renamed declaration refuses the write also with the option on. The plan does not
   * report the usages that it skips.
   */
  public static final @NotNull ModPlanOption<Boolean> SKIP_READ_ONLY_USAGES =
    ModPlanOption.bool("skipReadOnlyUsages", false, RefactoringBundle.messagePointer("modplan.rename.skip.read.only.usages"));
  private ModPlanRenameOptions() {
  }

  /**
   * @return the option that turns on the renamers of {@code factory}, as its checkbox in the rename dialog does; off by default. Null if the
   * factory has no option, which runs it always, or it has no {@link AutomaticRenamerFactory#getOptionId id}, which never runs it here
   */
  public static @Nullable ModPlanOption<Boolean> optionForRenamer(@NotNull AutomaticRenamerFactory factory) {
    String id = factory.getOptionId();
    if (id == null || factory.getOptionName() == null) return null;
    return ModPlanOption.bool(id, false, () -> StringUtil.notNullize(factory.getOptionName()));
  }

  /**
   * @return {@code options} with {@link #RENAME_RELATED} and the {@link #optionForRenamer option} of every factory as the settings of the
   * IDE keep them, as the inline rename runs the factories
   */
  public static @NotNull ModPlanOptionValues withRenamersOfSettings(@NotNull ModPlanOptionValues options) {
    ModPlanOptionValues result = options.with(RENAME_RELATED, RefactoringSettings.getInstance().RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      ModPlanOption<Boolean> renamerOption = optionForRenamer(factory);
      if (renamerOption != null) {
        result = result.with(renamerOption, factory.isEnabled());
      }
    }
    return result;
  }

  /**
   * @return the line of {@link #RELATED_RENAMES} that renames {@code element} to {@code newName}: the path of its file and its offset, then
   * the new name, after a tab; null if the element has no file
   */
  @RequiresReadLock
  public static @Nullable String relatedRename(@NotNull PsiElement element, @NotNull String newName) {
    String key = keyOf(element);
    return key == null ? null : key + "\t" + newName;
  }

  /** @return the new names of {@code relatedRenames} by the keys of their elements */
  static @NotNull Map<String, String> relatedRenames(@NotNull String relatedRenames) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String line : StringUtil.splitByLinesDontTrim(relatedRenames)) {
      int tab = line.lastIndexOf('\t');
      if (tab > 0) {
        result.put(line.substring(0, tab), line.substring(tab + 1));
      }
    }
    return result;
  }

  @RequiresReadLock
  static @Nullable String keyOf(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    return virtualFile == null ? null : virtualFile.getPath() + ":" + element.getTextOffset();
  }
}
