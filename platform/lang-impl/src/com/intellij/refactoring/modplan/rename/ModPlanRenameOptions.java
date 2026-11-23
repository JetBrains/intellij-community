// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
   * The automatic renamers that run, such as the one of the variables named after a renamed class: the
   * {@link AutomaticRenamerFactory#getOptionId ids} of their factories, one per line. A factory runs only if its id is here. This is
   * also true for a factory with no checkbox in the rename dialog. Factories of several languages share an id, such as the variables of
   * Java and Kotlin. With no value, no automatic renamer runs, so a caller with no user renames the element alone. A line with the
   * prefix {@code -} is an id that is off, see {@link ModPlanOptionValues#withChosenKeys}.
   */
  public static final @NotNull ModPlanOption<String> RENAMERS =
    ModPlanOption.text("renamers", "", RefactoringBundle.messagePointer("rename.related.elements.option.name"));
  /**
   * The chosen related renames, one per line: {@link #relatedRename} of the renamer, the element and its new name. With a value, only
   * these renames are made: an element with no line, with an empty new name or with the prefix {@code -} keeps its name, see
   * {@link ModPlanOptionValues#withChosenKeys}. With no value, every suggested rename is made, as the legacy rename does in a unit test.
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

  /** @return the ids of the factories that {@link #RENAMERS} of {@code options} turns on */
  public static @NotNull Set<String> renamerIds(@NotNull ModPlanOptionValues options) {
    return options.chosenKeys(RENAMERS);
  }

  /**
   * @param isOn whether the checkbox of a factory in the rename dialog is on
   * @return the ids of the factories whose checkbox is on, and of the factories with no checkbox, which run always in the legacy rename.
   * A factory with no checkbox follows the checkbox of a factory with the same id, such as the Kotlin variables of a Java class follow the
   * variables of Java. A factory with no {@link AutomaticRenamerFactory#getOptionId id} cannot run, so it has none here
   */
  public static @NotNull Set<String> renamerIds(@NotNull Predicate<? super AutomaticRenamerFactory> isOn) {
    Set<String> idsWithCheckbox = new HashSet<>();
    Set<String> idsWithoutCheckbox = new LinkedHashSet<>();
    Set<String> result = new LinkedHashSet<>();
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      String id = factory.getOptionId();
      if (id == null) continue;
      if (factory.getOptionName() == null) {
        idsWithoutCheckbox.add(id);
      }
      else {
        idsWithCheckbox.add(id);
        if (isOn.test(factory)) {
          result.add(id);
        }
      }
    }
    for (String id : idsWithoutCheckbox) {
      if (!idsWithCheckbox.contains(id)) {
        result.add(id);
      }
    }
    return result;
  }

  /** @return {@code options} with {@link #RENAMERS} that turns on the factories of {@code renamerIds} */
  public static @NotNull ModPlanOptionValues withRenamers(@NotNull ModPlanOptionValues options, @NotNull Collection<String> renamerIds) {
    return options.with(RENAMERS, String.join("\n", renamerIds));
  }

  /**
   * @return {@code options} with {@link #RENAMERS} as the settings of the IDE keep the checkboxes of the factories, as the inline rename
   * runs them. With {@code RefactoringSettings.RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG} off, no factory runs, as in the legacy rename
   */
  public static @NotNull ModPlanOptionValues withRenamersOfSettings(@NotNull ModPlanOptionValues options) {
    if (!RefactoringSettings.getInstance().RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG) return withRenamers(options, List.of());
    return withRenamers(options, renamerIds(AutomaticRenamerFactory::isEnabled));
  }

  /**
   * @param renamerId the {@link AutomaticRenamerFactory#getOptionId id} of the factory of the renamer that suggests the rename
   * @return the line of {@link #RELATED_RENAMES} that renames {@code element} to {@code newName}: the renamer id, the path of the file and
   * the 1-based line and column of the element, then the new name, after a tab; null if the element has no file or no document
   */
  @RequiresReadLock
  public static @Nullable String relatedRename(@NotNull String renamerId, @NotNull PsiElement element, @NotNull String newName) {
    String key = keyOf(renamerId, element);
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

  /**
   * @return the key of a line of {@link #RELATED_RENAMES}, see {@link #relatedRename}. It has no tab, because the option trims every line,
   * and a line with an empty new name ends with the tab
   */
  @RequiresReadLock
  static @Nullable String keyOf(@NotNull String renamerId, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (virtualFile == null || document == null) return null;
    int offset = element.getTextOffset();
    int line = document.getLineNumber(offset);
    int column = offset - document.getLineStartOffset(line);
    return renamerId + " " + virtualFile.getPath() + ":" + (line + 1) + ":" + (column + 1);
  }
}
