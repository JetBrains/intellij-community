// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents the settings of a Find, Replace, Find in Path or Replace in Path
 * operations.
 */
public class FindModel extends UserDataHolderBase implements Cloneable {

  public static void initStringToFind(FindModel findModel, String s) {
    if (!StringUtil.isEmpty(s)) {
      if (StringUtil.containsLineBreak(s)) {
        findModel.setMultiline(true);
      }
      findModel.setStringToFind(s);
    }
  }

  @FunctionalInterface
  public interface FindModelObserver {
    void findModelChanged(FindModel findModel);
  }

  private final List<FindModelObserver> myObservers = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addObserver(@NotNull FindModelObserver observer) {
    myObservers.add(observer);
  }

  public void removeObserver(@NotNull FindModelObserver observer) {
    myObservers.remove(observer);
  }

  public void refresh() {
    notifyObservers();
  }

  private void notifyObservers() {
    for (FindModelObserver observer : myObservers) {
      observer.findModelChanged(this);
    }
  }

  private String myStringToFind = null;
  private String myStringToReplace = "";
  private boolean isSearchHighlighters;
  private boolean isReplaceState;
  private boolean isWholeWordsOnly;
  private SearchContext searchContext = SearchContext.ANY;
  private boolean isFromCursor = true;
  private boolean isForward = true;
  private boolean isGlobal = true;
  private boolean isRegularExpressions;
  private @MagicConstant(flagsFromClass = Pattern.class) int regExpFlags;
  private boolean isCaseSensitive;
  private boolean isMultipleFiles;
  private boolean isPromptOnReplace = true;
  private boolean isReplaceAll;
  private boolean isProjectScope = true;
  private boolean isFindAll;
  private boolean isFindAllEnabled;
  private String moduleName;
  private String directoryName;
  private boolean isWithSubdirectories = true;
  private String fileFilter;
  private @Nls String customScopeName;
  private SearchScope customScope;
  private boolean isCustomScope;
  private boolean isMultiline;
  private boolean mySearchInProjectFiles;

  public boolean isMultiline() {
    return isMultiline;
  }

  public void setMultiline(boolean multiline) {
    if (multiline != isMultiline) {
      isMultiline = multiline;
      notifyObservers();
    }
  }

  /**
   * Gets the Preserve Case flag.
   *
   * @return the value of the Preserve Case flag.
   */
  public boolean isPreserveCase() {
    return isPreserveCase;
  }

  /**
   * Sets the Preserve Case flag.
   *
   * @param preserveCase the value of the Preserve Case flag.
   */
  public void setPreserveCase(boolean preserveCase) {
    boolean changed = isPreserveCase != preserveCase;
    if (changed) {
      isPreserveCase = preserveCase;
      notifyObservers();
    }
  }

  private boolean isPreserveCase;

  /**
   * Copies all the settings from the specified model.
   *
   * @param model the model to copy settings from.
   */
  public void copyFrom(FindModel model) {
    boolean changed = !equals(model);
    if (changed) {
      myStringToFind = model.myStringToFind;
      myStringToReplace = model.myStringToReplace;
      isReplaceState = model.isReplaceState;
      isWholeWordsOnly = model.isWholeWordsOnly;
      isFromCursor = model.isFromCursor;
      isForward = model.isForward;
      isGlobal = model.isGlobal;
      isRegularExpressions = model.isRegularExpressions;
      regExpFlags = model.regExpFlags;
      isCaseSensitive = model.isCaseSensitive;
      isMultipleFiles = model.isMultipleFiles;
      isPromptOnReplace = model.isPromptOnReplace;
      isReplaceAll = model.isReplaceAll;
      isProjectScope = model.isProjectScope;
      directoryName = model.directoryName;
      isWithSubdirectories = model.isWithSubdirectories;
      isPreserveCase = model.isPreserveCase;
      fileFilter = model.fileFilter;
      moduleName = model.moduleName;
      customScopeName = model.customScopeName;
      customScope = model.customScope;
      isCustomScope = model.isCustomScope;
      isFindAll = model.isFindAll;
      searchContext = model.searchContext;
      isMultiline = model.isMultiline;
      mySearchInProjectFiles = model.mySearchInProjectFiles;
      myPattern = model.myPattern;
      model.copyCopyableDataTo(this);
      notifyObservers();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FindModel findModel = (FindModel)o;

    if (isCaseSensitive != findModel.isCaseSensitive) return false;
    if (isCustomScope != findModel.isCustomScope) return false;
    if (isFindAll != findModel.isFindAll) return false;
    if (isFindAllEnabled != findModel.isFindAllEnabled) return false;
    if (isForward != findModel.isForward) return false;
    if (isFromCursor != findModel.isFromCursor) return false;
    if (isGlobal != findModel.isGlobal) return false;
    if (searchContext != findModel.searchContext) return false;

    if (isMultiline != findModel.isMultiline) return false;
    if (isMultipleFiles != findModel.isMultipleFiles) return false;
    if (isPreserveCase != findModel.isPreserveCase) return false;
    if (isProjectScope != findModel.isProjectScope) return false;
    if (isPromptOnReplace != findModel.isPromptOnReplace) return false;
    if (isRegularExpressions != findModel.isRegularExpressions) return false;
    if (regExpFlags != findModel.regExpFlags) return false;
    if (isReplaceAll != findModel.isReplaceAll) return false;
    if (isReplaceState != findModel.isReplaceState) return false;
    if (isSearchHighlighters != findModel.isSearchHighlighters) return false;
    if (isWholeWordsOnly != findModel.isWholeWordsOnly) return false;
    if (isWithSubdirectories != findModel.isWithSubdirectories) return false;
    if (customScope != null ? !customScope.equals(findModel.customScope) : findModel.customScope != null) return false;
    if (customScopeName != null ? !customScopeName.equals(findModel.customScopeName) : findModel.customScopeName != null) return false;
    if (directoryName != null ? !directoryName.equals(findModel.directoryName) : findModel.directoryName != null) return false;
    if (fileFilter != null ? !fileFilter.equals(findModel.fileFilter) : findModel.fileFilter != null) return false;
    if (moduleName != null ? !moduleName.equals(findModel.moduleName) : findModel.moduleName != null) return false;
    if (myStringToFind != null ? !myStringToFind.equals(findModel.myStringToFind) : findModel.myStringToFind != null) return false;
    if (myStringToReplace != null ? !myStringToReplace.equals(findModel.myStringToReplace) : findModel.myStringToReplace != null) {
      return false;
    }
    if (mySearchInProjectFiles != findModel.mySearchInProjectFiles) return false;
    if (!isCopyableDataEqual(findModel)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    result = 31 * result + (myStringToFind != null ? myStringToFind.hashCode() : 0);
    result = 31 * result + (myStringToReplace != null ? myStringToReplace.hashCode() : 0);
    result = 31 * result + (isSearchHighlighters ? 1 : 0);
    result = 31 * result + (isReplaceState ? 1 : 0);
    result = 31 * result + (isWholeWordsOnly ? 1 : 0);
    result = 31 * result + (searchContext.ordinal());
    result = 31 * result + (isFromCursor ? 1 : 0);
    result = 31 * result + (isForward ? 1 : 0);
    result = 31 * result + (isGlobal ? 1 : 0);
    result = 31 * result + (isRegularExpressions ? 1 : 0);
    result = 31 * result + regExpFlags;
    result = 31 * result + (isCaseSensitive ? 1 : 0);
    result = 31 * result + (isMultipleFiles ? 1 : 0);
    result = 31 * result + (isPromptOnReplace ? 1 : 0);
    result = 31 * result + (isReplaceAll ? 1 : 0);
    result = 31 * result + (isProjectScope ? 1 : 0);
    result = 31 * result + (isFindAll ? 1 : 0);
    result = 31 * result + (isFindAllEnabled ? 1 : 0);
    result = 31 * result + (moduleName != null ? moduleName.hashCode() : 0);
    result = 31 * result + (directoryName != null ? directoryName.hashCode() : 0);
    result = 31 * result + (isWithSubdirectories ? 1 : 0);
    result = 31 * result + (fileFilter != null ? fileFilter.hashCode() : 0);
    result = 31 * result + (customScopeName != null ? customScopeName.hashCode() : 0);
    result = 31 * result + (customScope != null ? customScope.hashCode() : 0);
    result = 31 * result + (isCustomScope ? 1 : 0);
    result = 31 * result + (isMultiline ? 1 : 0);
    result = 31 * result + (isPreserveCase ? 1 : 0);
    result = 31 * result + (mySearchInProjectFiles ? 1 : 0);
    return result;
  }

  /**
   * Gets the string to find.
   *
   * @return the string to find.
   */
  public @NotNull String getStringToFind() {
    return (myStringToFind == null) ? "" : myStringToFind;
  }

  public boolean hasStringToFind() {
    return myStringToFind != null;
  }

  /**
   * Sets the string to find.
   *
   * @param s the string to find.
   */
  public void setStringToFind(@NotNull String s) {
    boolean changed = !StringUtil.equals(s, myStringToFind);
    if (changed) {
      myStringToFind = s;
      myPattern = PatternUtil.NOTHING;
      notifyObservers();
    }
  }

  /**
   * Gets the string to replace with.
   *
   * @return the string to replace with.
   */
  public @NotNull String getStringToReplace() {
    return myStringToReplace;
  }

  /**
   * Sets the string to replace with.
   *
   * @param s the string to replace with.
   */
  public void setStringToReplace(@NotNull String s) {
    boolean changed = !StringUtil.equals(s, myStringToReplace);
    if (changed) {
      myStringToReplace = s;
      notifyObservers();
    }
  }

  /**
   * Gets the value indicating whether the operation is a Find or a Replace.
   *
   * @return true if the operation is a Replace, false if it is a Find.
   */
  public boolean isReplaceState() {
    return isReplaceState;
  }

  /**
   * Sets the value indicating whether the operation is a Find or a Replace.
   *
   * @param val true if the operation is a Replace, false if it is a Find.
   */
  public void setReplaceState(boolean val) {
    boolean changed = val != isReplaceState;
    if (changed) {
      isReplaceState = val;
      notifyObservers();
    }
  }

  /**
   * Gets the origin for the find.
   *
   * @return true if the origin is From Cursor, false if it is Entire Scope.
   */
  public boolean isFromCursor() {
    return isFromCursor;
  }

  /**
   * Sets the origin for the find.
   *
   * @param val true if the origin is From Cursor, false if it is Entire Scope.
   */
  public void setFromCursor(boolean val) {
    boolean changed = val != isFromCursor;
    if (changed) {
      isFromCursor = val;
      notifyObservers();
    }
  }

  /**
   * Gets the direction for the find.
   *
   * @return true if the find is forward, false if it is backward.
   */
  public boolean isForward() {
    return isForward;
  }

  /**
   * Sets the direction for the find.
   *
   * @param val true if the find is forward, false if it is backward.
   */
  public void setForward(boolean val) {
    boolean changed = val != isForward;
    if (changed) {
      isForward = val;
      notifyObservers();
    }
  }

  /**
   * Gets the Regular Expressions flag.
   *
   * @return the value of the Regular Expressions flag.
   */
  public boolean isRegularExpressions() {
    return isRegularExpressions;
  }

  /**
   * Sets the Regular Expressions flag.
   *
   * @param val the value of the Regular Expressions flag.
   */
  public void setRegularExpressions(boolean val) {
    boolean changed = val != isRegularExpressions;
    if (changed) {
      isRegularExpressions = val;
      notifyObservers();
    }
  }

  public int getRegExpFlags() {
    return regExpFlags;
  }

  public void setRegExpFlags(@MagicConstant(flagsFromClass = Pattern.class) int flags) {
    boolean changed = flags != regExpFlags;
    if (changed) {
      regExpFlags = flags;
      myPattern = PatternUtil.NOTHING;
      notifyObservers();
    }
  }

  /**
   * Gets the Case Sensitive flag.
   *
   * @return the value of the Case Sensitive flag.
   */
  public boolean isCaseSensitive() {
    return isCaseSensitive;
  }

  /**
   * Sets the Case Sensitive flag.
   *
   * @param val the value of the Case Sensitive flag.
   */
  public void setCaseSensitive(boolean val) {
    boolean changed = val != isCaseSensitive;
    if (changed) {
      isCaseSensitive = val;
      myPattern = PatternUtil.NOTHING;
      notifyObservers();
    }
  }

  /**
   * Checks if the find or replace operation affects multiple files.
   *
   * @return true if the operation affects multiple files, false if it affects a single file.
   */
  public boolean isMultipleFiles() {
    return isMultipleFiles;
  }

  /**
   * Sets the value indicating whether the find or replace operation affects multiple files.
   *
   * @param val true if the operation affects multiple files, false if it affects a single file.
   */
  public void setMultipleFiles(boolean val) {
    boolean changed = val != isMultipleFiles;
    if (changed) {
      isMultipleFiles = val;
      notifyObservers();
    }
  }

  /**
   * Gets the Prompt on Replace flag.
   *
   * @return the value of the Prompt on Replace flag.
   */
  public boolean isPromptOnReplace() {
    return isPromptOnReplace;
  }

  /**
   * Sets the Prompt on Replace flag.
   *
   * @param val the value of the Prompt on Replace flag.
   */
  public void setPromptOnReplace(boolean val) {
    boolean changed = val != isPromptOnReplace;
    if (changed) {
      isPromptOnReplace = val;
      notifyObservers();
    }
  }

  /**
   * Gets the Whole Words Only flag.
   *
   * @return the value of the Whole Words Only flag.
   */
  public boolean isWholeWordsOnly() {
    return isWholeWordsOnly;
  }

  /**
   * Sets the Whole Words Only flag.
   *
   * @param isWholeWordsOnly the value of the Whole Words Only flag.
   */
  public void setWholeWordsOnly(boolean isWholeWordsOnly) {
    boolean changed = isWholeWordsOnly != this.isWholeWordsOnly;
    if (changed) {
      this.isWholeWordsOnly = isWholeWordsOnly;
      notifyObservers();
    }
  }

  /**
   * Gets the scope of the operation.
   *
   * @return true if the operation affects the entire file, false if it affects the selected text.
   */
  public boolean isGlobal() {
    return isGlobal;
  }

  /**
   * Sets the scope of the operation.
   *
   * @param isGlobal true if the operation affects the entire file, false if it affects the selected text.
   */
  public void setGlobal(boolean isGlobal) {
    boolean changed = this.isGlobal != isGlobal;
    if (changed) {
      this.isGlobal = isGlobal;
      notifyObservers();
    }
  }

  /**
   * Gets the Replace All flag.
   *
   * @return the value of the Replace All flag.
   */
  public boolean isReplaceAll() {
    return isReplaceAll;
  }

  /**
   * Sets the Replace All flag.
   *
   * @param replaceAll the value of the Replace All flag.
   */
  public void setReplaceAll(boolean replaceAll) {
    isReplaceAll = replaceAll;
    notifyObservers();
  }

  /**
   * Sets the Open in New Tab flag.
   *
   * @param showInNewTab the value of the Open in New Tab flag.
   * @deprecated and not used anymore
   */
  @Deprecated(forRemoval = true)
  public void setOpenInNewTab(boolean showInNewTab) {
  }

  /**
   * Gets the value indicating whether the Open in New Tab flag is enabled for the operation.
   *
   * @return true if Open in New Tab is enabled, false otherwise.
   * @deprecated and not used anymore
   */
  @Deprecated(forRemoval = true)
  public boolean isOpenInNewTabEnabled() {
    return true;
  }

  /**
   * Sets the value indicating whether the Open in New Tab flag is enabled for the operation.
   *
   * @param showInNewTabEnabled true if Open in New Tab is enabled, false otherwise.
   * @deprecated and not used anymore
   */
  @Deprecated(forRemoval = true)
  public void setOpenInNewTabEnabled(boolean showInNewTabEnabled) {
  }

  /**
   * @deprecated and not used anymore
   */
  @Deprecated(forRemoval = true)
  public void setOpenInNewTabVisible(boolean showInNewTabVisible) {
  }

  /**
   * Gets the directory used as a scope for Find in Path / Replace in Path.
   *
   * @return the directory used as a scope, or null if the selected scope is not "Directory".
   */
  public @Nullable @NlsSafe String getDirectoryName() {
    return directoryName;
  }

  /**
   * Sets the directory used as a scope for Find in Path / Replace in Path.
   *
   * @param directoryName the directory scope.
   */
  public void setDirectoryName(@NlsSafe @Nullable String directoryName) {
    boolean changed = !StringUtil.equals(this.directoryName, directoryName);
    if (changed) {
      this.directoryName = directoryName;
      notifyObservers();
    }
  }

  /**
   * Gets the Recursive Search flag for Find in Path / Replace in Path.
   *
   * @return true if directories are searched recursively, false otherwise.
   */
  public boolean isWithSubdirectories() {
    return isWithSubdirectories;
  }

  /**
   * Sets the Recursive Search flag for Find in Path / Replace in Path.
   *
   * @param withSubdirectories true if directories are searched recursively, false otherwise.
   */
  public void setWithSubdirectories(boolean withSubdirectories) {
    boolean changed = withSubdirectories != isWithSubdirectories;
    if (changed) {
      isWithSubdirectories = withSubdirectories;
      notifyObservers();
    }
  }

  /**
   * Gets the flag indicating whether the Whole Project scope is selected for Find in Path /
   * Replace in Path.
   *
   * @return true if the whole project scope is selected, false otherwise.
   */
  public boolean isProjectScope() {
    return isProjectScope;
  }

  /**
   * Sets the flag indicating whether the Whole Project scope is selected for Find in Path /
   * Replace in Path.
   *
   * @param projectScope true if the whole project scope is selected, false otherwise.
   */
  public void setProjectScope(boolean projectScope) {
    boolean changed = projectScope != isProjectScope;
    if (changed) {
      isProjectScope = projectScope;
      notifyObservers();
    }
  }

  @Override
  public FindModel clone() {
    return (FindModel)super.clone();
  }

  @Override
  public String toString() {
    return "--- FIND MODEL ---\n" +
           "myStringToFind = " + (myStringToFind == null ? "null\n" : "'" + myStringToFind + "'\n") +
           "myStringToReplace = '" + myStringToReplace + "'\n" +
           "isReplaceState = " + isReplaceState + "\n" +
           "isWholeWordsOnly = " + isWholeWordsOnly + "\n" +
           "searchContext = '" + searchContext + "'\n" +
           "isFromCursor = " + isFromCursor + "\n" +
           "isForward = " + isForward + "\n" +
           "isGlobal = " + isGlobal + "\n" +
           "isRegularExpressions = " + isRegularExpressions + "\n" +
           "regExpFlags = " + regExpFlags + "\n" +
           "isCaseSensitive = " + isCaseSensitive + "\n" +
           "isMultipleFiles = " + isMultipleFiles + "\n" +
           "isPromptOnReplace = " + isPromptOnReplace + "\n" +
           "isReplaceAll = " + isReplaceAll + "\n" +
           "isProjectScope = " + isProjectScope + "\n" +
           "directoryName = '" + directoryName + "'\n" +
           "isWithSubdirectories = " + isWithSubdirectories + "\n" +
           "fileFilter = " + fileFilter + "\n" +
           "moduleName = '" + moduleName + "'\n" +
           "customScopeName = '" + customScopeName + "'\n" +
           "searchInProjectFiles = " + mySearchInProjectFiles + "\n" +
           "userDataMap = " + getUserMap() + "\n";
  }

  /**
   * Gets the flag indicating whether the search operation is Find Next / Find Previous
   * after Highlight Usages in Files.
   *
   * @return true if the operation moves between highlighted regions, false otherwise.
   */
  public boolean searchHighlighters() {
    return isSearchHighlighters;
  }

  /**
   * Sets the flag indicating whether the search operation is Find Next / Find Previous
   * after Highlight Usages in Files.
   *
   * @param search true if the operation moves between highlighted regions, false otherwise.
   */
  public void setSearchHighlighters(boolean search) {
    boolean changed = search != isSearchHighlighters;
    if (changed) {
      isSearchHighlighters = search;
      notifyObservers();
    }
  }

  /**
   * Gets the file name filter used for Find in Path / Replace in Path operation.
   *
   * @return the file name filter text.
   */
  public String getFileFilter() {
    return fileFilter;
  }

  /**
   * Sets the file name filter used for Find in Path / Replace in Path operation.
   *
   * @param fileFilter the file name filter text.
   */
  public void setFileFilter(@Nullable String fileFilter) {
    boolean changed = !StringUtil.equals(fileFilter, this.fileFilter);
    if (changed) {
      this.fileFilter = fileFilter;
      notifyObservers();
    }
  }

  /**
   * Gets the name of the module used as the scope for the Find in Path / Replace
   * in Path operation.
   *
   * @return the module name, or null if the selected scope is not "Module".
   */
  public @Nullable @NlsSafe String getModuleName() {
    return moduleName;
  }

  /**
   * Sets the name of the module used as the scope for the Find in Path / Replace
   * in Path operation.
   *
   * @param moduleName the name of the module used as the scope.
   */
  public void setModuleName(@NlsSafe String moduleName) {
    boolean changed = !StringUtil.equals(moduleName, this.moduleName);
    if (changed) {
      this.moduleName = moduleName;
      notifyObservers();
    }
  }

  /**
   * Gets the flag indicating whether "Find All" button was used to initiate the find
   * operation.
   *
   * @return true if the operation is a "Find All", false otherwise.
   */
  public boolean isFindAll() {
    return isFindAll;
  }

  /**
   * Sets the flag indicating whether "Find All" button was used to initiate the find
   * operation.
   *
   * @param findAll true if the operation is a "Find All", false otherwise.
   */
  public void setFindAll(boolean findAll) {
    boolean changed = isFindAll != findAll;
    if (changed) {
      isFindAll = findAll;
      notifyObservers();
    }
  }

  /**
   * Gets the flag indicating whether "Find All" button is allowed for the operation.
   *
   * @return true if "Find All" is enabled, false otherwise.
   */
  public boolean isFindAllEnabled() {
    return isFindAllEnabled;
  }

  /**
   * Sets the flag indicating whether "Find All" button is allowed for the operation.
   *
   * @param findAllEnabled true if "Find All" is enabled, false otherwise.
   */
  public void setFindAllEnabled(boolean findAllEnabled) {
    boolean changed = isFindAllEnabled != findAllEnabled;
    if (changed) {
      isFindAllEnabled = findAllEnabled;
      notifyObservers();
    }
  }

  public @Nls String getCustomScopeName() {
    return customScopeName;
  }

  public void setCustomScopeName(@Nls String customScopeName) {
    boolean changed = !StringUtil.equals(customScopeName, this.customScopeName);
    if (changed) {
      this.customScopeName = customScopeName;
      notifyObservers();
    }
  }

  public SearchScope getCustomScope() {
    return customScope;
  }

  public void setCustomScope(SearchScope customScope) {
    boolean changed = !Objects.equals(this.customScope, customScope);
    if (changed) {
      this.customScope = customScope;
      notifyObservers();
    }
  }

  public boolean isCustomScope() {
    return isCustomScope;
  }

  public void setCustomScope(boolean customScope) {
    boolean changed = isCustomScope != customScope;
    if (changed) {
      isCustomScope = customScope;
      notifyObservers();
    }
  }

  public enum SearchContext {
    ANY, IN_STRING_LITERALS, IN_COMMENTS, EXCEPT_STRING_LITERALS, EXCEPT_COMMENTS, EXCEPT_COMMENTS_AND_STRING_LITERALS
  }

  public boolean isInStringLiteralsOnly() {
    return searchContext == SearchContext.IN_STRING_LITERALS;
  }

  public boolean isExceptComments() {
    return searchContext == SearchContext.EXCEPT_COMMENTS;
  }

  public boolean isExceptStringLiterals() {
    return searchContext == SearchContext.EXCEPT_STRING_LITERALS;
  }

  public boolean isInCommentsOnly() {
    return searchContext == SearchContext.IN_COMMENTS;
  }

  public boolean isExceptCommentsAndStringLiterals() {
    return searchContext == SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS;
  }

  /**
   * @deprecated Use {@link #setSearchContext(SearchContext)} instead
   */
  @Deprecated
  public void setInCommentsOnly(boolean inCommentsOnly) {
    doApplyContextChange(inCommentsOnly, SearchContext.IN_COMMENTS);
  }

  /**
   * @deprecated Use {@link #setSearchContext(SearchContext)} instead
   */
  @Deprecated
  public void setInStringLiteralsOnly(boolean inStringLiteralsOnly) {
    doApplyContextChange(inStringLiteralsOnly, SearchContext.IN_STRING_LITERALS);
  }

  private void doApplyContextChange(boolean newOptionValue, SearchContext option) {
    boolean changed = false;
    if (newOptionValue) {
      changed = searchContext != option;
      searchContext = option;
    } else if (searchContext == option) { // do not reset unrelated value
      changed = true;
      searchContext = SearchContext.ANY;
    }

    if (changed) {
      notifyObservers();
    }
  }

  public @NotNull SearchContext getSearchContext() {
    return searchContext;
  }

  public void setSearchContext(@NotNull SearchContext _searchContext) {
    doSetContext(_searchContext);
  }

  private void doSetContext(SearchContext newSearchContext) {
    boolean changed = newSearchContext != searchContext;
    if (changed) {
      searchContext = newSearchContext;
      notifyObservers();
    }
  }

  public boolean isSearchInProjectFiles() {
    if (!mySearchInProjectFiles) {
      if (fileFilter != null) {
        List<String> split = StringUtil.split(fileFilter, ",");
        if (ContainerUtil.exists(split, s -> s.endsWith(".iml") || s.endsWith(".ipr") || s.endsWith(".iws"))) {
          return true;
        }
      }
      if (directoryName != null) {
        String path = FileUtil.toSystemIndependentName(directoryName);
        if (path.endsWith("/.idea") || path.contains("/.idea/")) {
          return true;
        }
      }
    }
    return mySearchInProjectFiles;
  }

  public void setSearchInProjectFiles(boolean searchInProjectFiles) {
    boolean changed = mySearchInProjectFiles != searchInProjectFiles;
    if (changed) {
      mySearchInProjectFiles = searchInProjectFiles;
      notifyObservers();
    }
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    boolean changed = !Objects.equals(value, getUserData(key));
    super.putUserData(key, value);
    if (changed) {
      notifyObservers();
    }
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    boolean changed = !Objects.equals(value, getCopyableUserData(key));
    super.putCopyableUserData(key, value);
    if (changed) {
      notifyObservers();
    }
  }

  private Pattern myPattern = PatternUtil.NOTHING;

  public Pattern compileRegExp() {
    Pattern pattern = myPattern;
    if (pattern == PatternUtil.NOTHING) {
      String toFind = getStringToFind();
      @MagicConstant(flagsFromClass = Pattern.class) int flags;
      if (regExpFlags != 0) {
        flags = getRegExpFlags(); // should separate case-sensitive setting be used here?
      }
      else {
        flags = isCaseSensitive() ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
      }

      // SOE during matching regular expressions is considered to be feature
      // http://bugs.java.com/view_bug.do?bug_id=6882582
      // http://bugs.java.com/view_bug.do?bug_id=5050507
      // IDEA-175066 / https://stackoverflow.com/questions/31676277/stackoverflowerror-in-regular-expression
      if (toFind.contains("\\n") && Registry.is("jdk.regex.soe.workaround")) { // if needed use DOT_ALL for modified pattern to avoid SOE
        String modifiedStringToFind = StringUtil.replace(toFind, "\\n|.", ".");
        modifiedStringToFind = StringUtil.replace(modifiedStringToFind, ".|\\n", ".");

        if (!modifiedStringToFind.equals(toFind)) {
          flags |= Pattern.DOTALL;
          toFind = modifiedStringToFind;
        }
      }
      try {
        myPattern = pattern = Pattern.compile(toFind, flags);
      }
      catch (PatternSyntaxException e) {
        myPattern = pattern = null;
      }
    }

    return pattern;
  }
}
