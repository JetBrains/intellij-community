/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents the settings of a Find, Replace, Find in Path or Replace in Path
 * operations.
 */
public class FindModel extends UserDataHolderBase implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.FindModel");

  public static void initStringToFindNoMultiline(FindModel findModel, String s) {
    if (!StringUtil.isEmpty(s)) {
      if (!s.contains("\r") && !s.contains("\n")) {
        findModel.setStringToFind(s);
      }
      else {
        findModel.setStringToFind(StringUtil.escapeToRegexp(s));
        findModel.setRegularExpressions(true);
      }
    }
  }

  public interface FindModelObserver {
    void findModelChanged(FindModel findModel);
  }

  private final List<FindModelObserver> myObservers = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addObserver(FindModelObserver observer) {
    myObservers.add(observer);
  }

  public void removeObserver(FindModelObserver observer) {
    myObservers.remove(observer);
  }

  private void notifyObservers() {
    for (FindModelObserver observer : myObservers) {
      observer.findModelChanged(this);
    }
  }

  private String myStringToFind = "";
  private String myStringToReplace = "";
  private boolean isSearchHighlighters = false;
  private boolean isReplaceState = false;
  private boolean isWholeWordsOnly = false;
  private boolean isInCommentsOnly;
  private boolean isInStringLiteralsOnly;
  private boolean isFromCursor = true;
  private boolean isForward = true;
  private boolean isGlobal = true;
  private boolean isRegularExpressions = false;
  private boolean isCaseSensitive = false;
  private boolean isMultipleFiles = false;
  private boolean isPromptOnReplace = true;
  private boolean isReplaceAll = false;
  private boolean isOpenNewTab = false;
  private boolean isOpenInNewTabEnabled = false;
  private boolean isOpenNewTabVisible = false;
  private boolean isProjectScope = true;
  private boolean isFindAll = false;
  private boolean isFindAllEnabled = false;
  private String moduleName;
  private String directoryName = null;
  private boolean isWithSubdirectories = true;
  private String fileFilter;
  private String customScopeName;
  private SearchScope customScope;
  private boolean isCustomScope = false;
  private boolean isMultiline = false;

  public boolean isMultiline() {
    return isMultiline;
  }

  public void setMultiline(boolean multiline) {
    if (multiline != isMultiline) {
      if (!multiline) {
        initStringToFindNoMultiline(this, getStringToFind());
      }
      else {
        setRegularExpressions(false);
      }
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
    isPreserveCase = preserveCase;
    if (changed) {
      notifyObservers();
    }
  }

  private boolean isPreserveCase = false;

  /**
   * Copies all the settings from the specified model.
   *
   * @param model the model to copy settings from.
   */
  public void copyFrom(FindModel model) {
    myStringToFind = model.myStringToFind;
    myStringToReplace = model.myStringToReplace;
    isReplaceState = model.isReplaceState;
    isWholeWordsOnly = model.isWholeWordsOnly;
    isFromCursor = model.isFromCursor;
    isForward = model.isForward;
    isGlobal = model.isGlobal;
    isRegularExpressions = model.isRegularExpressions;
    isCaseSensitive = model.isCaseSensitive;
    isMultipleFiles = model.isMultipleFiles;
    isPromptOnReplace = model.isPromptOnReplace;
    isReplaceAll = model.isReplaceAll;
    isOpenNewTab = model.isOpenNewTab;
    isOpenInNewTabEnabled = model.isOpenInNewTabEnabled;
    isOpenNewTabVisible = model.isOpenNewTabVisible;
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

    isInCommentsOnly = model.isInCommentsOnly;
    isInStringLiteralsOnly = model.isInStringLiteralsOnly;

    isMultiline = model.isMultiline;
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
    if (isInCommentsOnly != findModel.isInCommentsOnly) return false;
    if (isInStringLiteralsOnly != findModel.isInStringLiteralsOnly) return false;
    if (isMultiline != findModel.isMultiline) return false;
    if (isMultipleFiles != findModel.isMultipleFiles) return false;
    if (isOpenInNewTabEnabled != findModel.isOpenInNewTabEnabled) return false;
    if (isOpenNewTab != findModel.isOpenNewTab) return false;
    if (isOpenNewTabVisible != findModel.isOpenNewTabVisible) return false;
    if (isPreserveCase != findModel.isPreserveCase) return false;
    if (isProjectScope != findModel.isProjectScope) return false;
    if (isPromptOnReplace != findModel.isPromptOnReplace) return false;
    if (isRegularExpressions != findModel.isRegularExpressions) return false;
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
    result = 31 * result + (isInCommentsOnly ? 1 : 0);
    result = 31 * result + (isInStringLiteralsOnly ? 1 : 0);
    result = 31 * result + (isFromCursor ? 1 : 0);
    result = 31 * result + (isForward ? 1 : 0);
    result = 31 * result + (isGlobal ? 1 : 0);
    result = 31 * result + (isRegularExpressions ? 1 : 0);
    result = 31 * result + (isCaseSensitive ? 1 : 0);
    result = 31 * result + (isMultipleFiles ? 1 : 0);
    result = 31 * result + (isPromptOnReplace ? 1 : 0);
    result = 31 * result + (isReplaceAll ? 1 : 0);
    result = 31 * result + (isOpenNewTab ? 1 : 0);
    result = 31 * result + (isOpenInNewTabEnabled ? 1 : 0);
    result = 31 * result + (isOpenNewTabVisible ? 1 : 0);
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
    result = 31 * result + (myPattern != null ? myPattern.hashCode() : 0);
    return result;
  }

  /**
   * Gets the string to find.
   *
   * @return the string to find.
   */
  @NotNull
  public String getStringToFind() {
    return myStringToFind;
  }

  /**
   * Sets the string to find.
   *
   * @param s the string to find.
   */
  public void setStringToFind(@NotNull String s) {
    boolean changed = !StringUtil.equals(s, myStringToFind);
    myStringToFind = s;
    myPattern = NO_PATTERN;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the string to replace with.
   *
   * @return the string to replace with.
   */
  @NotNull
  public String getStringToReplace() {
    return myStringToReplace;
  }

  /**
   * Sets the string to replace with.
   *
   * @param s the string to replace with.
   */
  public void setStringToReplace(@NotNull String s) {
    boolean changed = !StringUtil.equals(s, myStringToReplace);
    myStringToReplace = s;
    if (changed) {
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
    isReplaceState = val;
    if (changed) {
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
    isFromCursor = val;
    if (changed) {
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
    isForward = val;
    if (changed) {
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
    isRegularExpressions = val;
    if (changed) {
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
    isCaseSensitive = val;
    if (changed) {
      myPattern = NO_PATTERN;
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
    isMultipleFiles = val;
    if (changed) {
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
    isPromptOnReplace = val;
    if (changed) {
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
    this.isWholeWordsOnly = isWholeWordsOnly;
    if (changed) {
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
    this.isGlobal = isGlobal;
    if (changed) {
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
    boolean changed = isReplaceAll != replaceAll;
    isReplaceAll = replaceAll;
    notifyObservers();
  }

  /**
   * Gets the Open in New Tab flag.
   *
   * @return the value of the Open in New Tab flag.
   */
  public boolean isOpenInNewTab() {
    return isOpenNewTab;
  }

  /**
   * Sets the Open in New Tab flag.
   *
   * @param showInNewTab the value of the Open in New Tab flag.
   */
  public void setOpenInNewTab(boolean showInNewTab) {
    boolean changed = showInNewTab != isOpenNewTab;
    isOpenNewTab = showInNewTab;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the value indicating whether the Open in New Tab flag is enabled for the operation.
   *
   * @return true if Open in New Tab is enabled, false otherwise.
   */
  public boolean isOpenInNewTabEnabled() {
    return isOpenInNewTabEnabled;
  }

  /**
   * Sets the value indicating whether the Open in New Tab flag is enabled for the operation.
   *
   * @param showInNewTabEnabled true if Open in New Tab is enabled, false otherwise.
   */
  public void setOpenInNewTabEnabled(boolean showInNewTabEnabled) {
    boolean changed = isOpenInNewTabEnabled != showInNewTabEnabled;
    isOpenInNewTabEnabled = showInNewTabEnabled;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the value indicating whether the Open in New Tab flag is visible for the operation.
   *
   * @return true if Open in New Tab is visible, false otherwise.
   */
  public boolean isOpenInNewTabVisible() {
    return isOpenNewTabVisible;
  }

  /**
   * Sets the value indicating whether the Open in New Tab flag is enabled for the operation.
   *
   * @param showInNewTabVisible true if Open in New Tab is visible, false otherwise.
   */
  public void setOpenInNewTabVisible(boolean showInNewTabVisible) {
    boolean changed = showInNewTabVisible != isOpenNewTabVisible;
    isOpenNewTabVisible = showInNewTabVisible;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the directory used as a scope for Find in Path / Replace in Path.
   *
   * @return the directory used as a scope, or null if the selected scope is not "Directory".
   */
  @Nullable
  public String getDirectoryName() {
    return directoryName;
  }

  /**
   * Sets the directory used as a scope for Find in Path / Replace in Path.
   *
   * @param directoryName the directory scope.
   */
  public void setDirectoryName(String directoryName) {
    boolean changed = !StringUtil.equals(directoryName, directoryName);
    this.directoryName = directoryName;
    if (changed) {
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
    isWithSubdirectories = withSubdirectories;
    if (changed) {
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
    isProjectScope = projectScope;
    if (changed) {
      notifyObservers();
    }
  }

  @Override
  public Object clone() {
    return super.clone();
  }


  public String toString() {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("--- FIND MODEL ---\n");
    buffer.append("myStringToFind =").append(myStringToFind).append("\n");
    buffer.append("myStringToReplace =").append(myStringToReplace).append("\n");
    buffer.append("isReplaceState =").append(isReplaceState).append("\n");
    buffer.append("isWholeWordsOnly =").append(isWholeWordsOnly).append("\n");
    buffer.append("isInStringLiterals =").append(isInStringLiteralsOnly).append("\n");
    buffer.append("isInComments =").append(isInCommentsOnly).append("\n");
    buffer.append("isFromCursor =").append(isFromCursor).append("\n");
    buffer.append("isForward =").append(isForward).append("\n");
    buffer.append("isGlobal =").append(isGlobal).append("\n");
    buffer.append("isRegularExpressions =").append(isRegularExpressions).append("\n");
    buffer.append("isCaseSensitive =").append(isCaseSensitive).append("\n");
    buffer.append("isMultipleFiles =").append(isMultipleFiles).append("\n");
    buffer.append("isPromptOnReplace =").append(isPromptOnReplace).append("\n");
    buffer.append("isReplaceAll =").append(isReplaceAll).append("\n");
    buffer.append("isOpenNewTab =").append(isOpenNewTab).append("\n");
    buffer.append("isOpenInNewTabEnabled =").append(isOpenInNewTabEnabled).append("\n");
    buffer.append("isOpenNewTabVisible =").append(isOpenNewTabVisible).append("\n");
    buffer.append("isProjectScope =").append(isProjectScope).append("\n");
    buffer.append("directoryName =").append(directoryName).append("\n");
    buffer.append("isWithSubdirectories =").append(isWithSubdirectories).append("\n");
    buffer.append("fileFilter =").append(fileFilter).append("\n");
    buffer.append("moduleName =").append(moduleName).append("\n");
    buffer.append("customScopeName =").append(customScopeName).append("\n");
    return buffer.toString();
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
    isSearchHighlighters = search;
    if (changed) {
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
  public void setFileFilter(String fileFilter) {
    boolean changed = !StringUtil.equals(fileFilter, this.fileFilter);
    this.fileFilter = fileFilter;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the name of the module used as the scope for the Find in Path / Replace
   * in Path operation.
   *
   * @return the module name, or null if the selected scope is not "Module".
   */
  @Nullable
  public String getModuleName() {
    return moduleName;
  }

  /**
   * Sets the name of the module used as the scope for the Find in Path / Replace
   * in Path operation.
   *
   * @param moduleName the name of the module used as the scope.
   */
  public void setModuleName(String moduleName) {
    boolean changed = !StringUtil.equals(moduleName, this.moduleName);
    this.moduleName = moduleName;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the flag indicating whether "Find All" button was used to initiate the find
   * operation.
   *
   * @return true if the operation is a "Find All", false otherwise.
   * @since 5.1
   */
  public boolean isFindAll() {
    return isFindAll;
  }

  /**
   * Sets the flag indicating whether "Find All" button was used to initiate the find
   * operation.
   *
   * @param findAll true if the operation is a "Find All", false otherwise.
   * @since 5.1
   */
  public void setFindAll(final boolean findAll) {
    boolean changed = isFindAll != findAll;
    isFindAll = findAll;
    if (changed) {
      notifyObservers();
    }
  }

  /**
   * Gets the flag indicating whether "Find All" button is allowed for the operation.
   *
   * @return true if "Find All" is enabled, false otherwise.
   * @since 5.1
   */
  public boolean isFindAllEnabled() {
    return isFindAllEnabled;
  }

  /**
   * Sets the flag indicating whether "Find All" button is allowed for the operation.
   *
   * @param findAllEnabled true if "Find All" is enabled, false otherwise.
   * @since 5.1
   */
  public void setFindAllEnabled(final boolean findAllEnabled) {
    boolean changed = isFindAllEnabled != findAllEnabled;
    isFindAllEnabled = findAllEnabled;
    if (changed) {
      notifyObservers();
    }
  }

  public String getCustomScopeName() {
    return customScopeName;
  }

  public void setCustomScopeName(String customScopeName) {
    boolean changed = !StringUtil.equals(customScopeName, this.customScopeName);
    this.customScopeName = customScopeName;
    if (changed) {
      notifyObservers();
    }
  }

  public SearchScope getCustomScope() {
    return customScope;
  }

  public void setCustomScope(final SearchScope customScope) {
    boolean changed = this.customScope != null ? this.customScope.equals(customScope) : customScope != null;
    this.customScope = customScope;
    if (changed) {
      notifyObservers();
    }
  }

  public boolean isCustomScope() {
    return isCustomScope;
  }

  public void setCustomScope(boolean customScope) {
    boolean changed = isCustomScope != customScope;
    isCustomScope = customScope;
    if (changed) {
      notifyObservers();
    }
  }

  public boolean isInStringLiteralsOnly() {
    return isInStringLiteralsOnly;
  }

  public void setInStringLiteralsOnly(boolean inStringLiteralsOnly) {
    boolean changed = isInStringLiteralsOnly != inStringLiteralsOnly;
    isInStringLiteralsOnly = inStringLiteralsOnly;
    if (changed) {
      notifyObservers();
    }
  }

  public boolean isInCommentsOnly() {
    return isInCommentsOnly;
  }

  public void setInCommentsOnly(boolean inCommentsOnly) {
    boolean changed = isInCommentsOnly != inCommentsOnly;
    isInCommentsOnly = inCommentsOnly;
    if (changed) {
      notifyObservers();
    }
  }

  private static final Pattern NO_PATTERN = Pattern.compile("");
  private Pattern myPattern = NO_PATTERN;

  public Pattern compileRegExp() {
    String toFind = getStringToFind();

    Pattern pattern = myPattern;
    if (pattern == NO_PATTERN) {
      try {
        myPattern = pattern = Pattern.compile(toFind, isCaseSensitive() ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
      }
      catch (PatternSyntaxException e) {
        LOG.error("Regexp:'" + toFind + "'", e);
        myPattern = null;
        return null;
      }
    }

    return pattern;
  }
}