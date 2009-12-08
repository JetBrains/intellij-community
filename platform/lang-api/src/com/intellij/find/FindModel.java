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
import com.intellij.psi.search.SearchScope;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the settings of a Find, Replace, Find in Path or Replace in Path
 * operations.
 */
public class FindModel extends UserDataHolderBase implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.FindModel");

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
    isPreserveCase = preserveCase;
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
    isFindAll = model.isFindAll;

    isInCommentsOnly = model.isInCommentsOnly;
    isInStringLiteralsOnly = model.isInStringLiteralsOnly;
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
    LOG.assertTrue(s.length() > 0);
    myStringToFind = s;
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
    myStringToReplace = s;
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
    isReplaceState = val;
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
    isFromCursor = val;
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
    isForward = val;
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
    isRegularExpressions = val;
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
    isCaseSensitive = val;
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
    isMultipleFiles = val;
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
    isPromptOnReplace = val;
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
    this.isWholeWordsOnly = isWholeWordsOnly;
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
    this.isGlobal = isGlobal;
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
    isOpenNewTab = showInNewTab;
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
    isOpenInNewTabEnabled = showInNewTabEnabled;
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
    isOpenNewTabVisible = showInNewTabVisible;
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
    this.directoryName = directoryName;
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
    isWithSubdirectories = withSubdirectories;
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
    isProjectScope = projectScope;
  }

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
    isSearchHighlighters = search;
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
    this.fileFilter = fileFilter;
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
    this.moduleName = moduleName;
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
    isFindAll = findAll;
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
    isFindAllEnabled = findAllEnabled;
  }

  public String getCustomScopeName() {
    return customScopeName;
  }

  public void setCustomScopeName(String customScopeName) {
    this.customScopeName = customScopeName;
  }

  public SearchScope getCustomScope() {
    return customScope;
  }

  public void setCustomScope(final SearchScope customScope) {
    this.customScope = customScope;
  }

  public boolean isInStringLiteralsOnly() {
    return isInStringLiteralsOnly;
  }

  public void setInStringLiteralsOnly(boolean inStringLiteralsOnly) {
    isInStringLiteralsOnly = inStringLiteralsOnly;
  }

  public boolean isInCommentsOnly() {
    return isInCommentsOnly;
  }

  public void setInCommentsOnly(boolean inCommentsOnly) {
    isInCommentsOnly = inCommentsOnly;
  }
}