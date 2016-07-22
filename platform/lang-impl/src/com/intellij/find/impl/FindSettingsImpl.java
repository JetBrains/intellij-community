/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@State(
  name = "FindSettings",
  storages = {
    @Storage("find.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class FindSettingsImpl extends FindSettings implements PersistentStateComponent<FindSettingsImpl> {
  @NonNls private static final String FIND_DIRECTION_FORWARD = "forward";
  @NonNls private static final String FIND_DIRECTION_BACKWARD = "backward";
  @NonNls private static final String FIND_ORIGIN_FROM_CURSOR = "from_cursor";
  @NonNls private static final String FIND_ORIGIN_ENTIRE_SCOPE = "entire_scope";
  @NonNls private static final String FIND_SCOPE_GLOBAL = "global";
  @NonNls private static final String FIND_SCOPE_SELECTED = "selected";
  private static final String DEFAULT_SEARCH_SCOPE = FindBundle.message("find.scope.all.project.classes");
  
  private static final int MAX_RECENT_SIZE = 30;

  public FindSettingsImpl() {
    recentFileMasks.add("*.properties");
    recentFileMasks.add("*.html");
    recentFileMasks.add("*.jsp");
    recentFileMasks.add("*.xml");
    recentFileMasks.add("*.java");
    recentFileMasks.add("*.js");
    recentFileMasks.add("*.as");
    recentFileMasks.add("*.css");
    recentFileMasks.add("*.mxml");
    if (PlatformUtils.isPyCharm()) {
      recentFileMasks.add("*.py");
    }
    else if (PlatformUtils.isRubyMine()) {
      recentFileMasks.add("*.rb");
    }
    else if (PlatformUtils.isPhpStorm()) {
      recentFileMasks.add("*.php");
    }
    else if (PlatformUtils.isDatabaseIDE()) {
      recentFileMasks.add("*.sql");
    }
  }

  @Override
  public boolean isSearchOverloadedMethods() {
    return SEARCH_OVERLOADED_METHODS;
  }

  @Override
  public void setSearchOverloadedMethods(boolean search) {
    SEARCH_OVERLOADED_METHODS = search;
  }

  @SuppressWarnings({"WeakerAccess"}) public boolean SEARCH_OVERLOADED_METHODS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean SEARCH_IN_LIBRARIES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean SKIP_RESULTS_WHEN_ONE_USAGE = false;

  @SuppressWarnings({"WeakerAccess"}) public String FIND_DIRECTION = FIND_DIRECTION_FORWARD;
  @SuppressWarnings({"WeakerAccess"}) public String FIND_ORIGIN = FIND_ORIGIN_FROM_CURSOR;
  @SuppressWarnings({"WeakerAccess"}) public String FIND_SCOPE = FIND_SCOPE_GLOBAL;
  @SuppressWarnings({"WeakerAccess"}) public String FIND_CUSTOM_SCOPE = null;

  @SuppressWarnings({"WeakerAccess"}) public boolean CASE_SENSITIVE_SEARCH = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean LOCAL_CASE_SENSITIVE_SEARCH = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean PRESERVE_CASE_REPLACE = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean WHOLE_WORDS_ONLY = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean COMMENTS_ONLY = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean STRING_LITERALS_ONLY = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean EXCEPT_COMMENTS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean EXCEPT_COMMENTS_AND_STRING_LITERALS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean EXCEPT_STRING_LITERALS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean LOCAL_WHOLE_WORDS_ONLY = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean REGULAR_EXPRESSIONS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean LOCAL_REGULAR_EXPRESSIONS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean WITH_SUBDIRECTORIES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean SHOW_RESULTS_IN_SEPARATE_VIEW = false;

  @SuppressWarnings({"WeakerAccess"}) public String SEARCH_SCOPE = DEFAULT_SEARCH_SCOPE;
  @SuppressWarnings({"WeakerAccess"}) public String FILE_MASK;

  @Tag("recentFileMasks")
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "mask", elementValueAttribute = "")
  public List<String> recentFileMasks = new ArrayList<>();

  @Override
  public void loadState(FindSettingsImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public FindSettingsImpl getState() {
    return this;
  }

  @Override
  public boolean isSkipResultsWithOneUsage(){
    return SKIP_RESULTS_WHEN_ONE_USAGE;
  }

  @Override
  public void setSkipResultsWithOneUsage(boolean skip){
    SKIP_RESULTS_WHEN_ONE_USAGE = skip;
  }

  @Override
  public String getDefaultScopeName() {
    return SEARCH_SCOPE;
  }

  @Override
  public void setDefaultScopeName(String scope) {
    SEARCH_SCOPE = scope;
  }

  @Override
  public boolean isForward(){
    return FIND_DIRECTION_FORWARD.equals(FIND_DIRECTION);
  }

  @Override
  public void setForward(boolean findDirectionForward){
    FIND_DIRECTION = findDirectionForward ? FIND_DIRECTION_FORWARD : FIND_DIRECTION_BACKWARD;
  }

  @Override
  public boolean isFromCursor(){
    return FIND_ORIGIN_FROM_CURSOR.equals(FIND_ORIGIN);
  }

  @Override
  public void setFromCursor(boolean findFromCursor){
    FIND_ORIGIN = findFromCursor ? FIND_ORIGIN_FROM_CURSOR : FIND_ORIGIN_ENTIRE_SCOPE;
  }

  @Override
  public boolean isGlobal(){
    return FIND_SCOPE_GLOBAL.equals(FIND_SCOPE);
  }

  @Override
  public void setGlobal(boolean findGlobalScope){
    FIND_SCOPE = findGlobalScope ? FIND_SCOPE_GLOBAL : FIND_SCOPE_SELECTED;
  }

  @Override
  public boolean isCaseSensitive(){
    return CASE_SENSITIVE_SEARCH;
  }

  @Override
  public void setCaseSensitive(boolean caseSensitiveSearch){
    CASE_SENSITIVE_SEARCH = caseSensitiveSearch;
  }

  @Override
  public boolean isLocalCaseSensitive() {
    return LOCAL_CASE_SENSITIVE_SEARCH;
  }

  @Override
  public boolean isLocalWholeWordsOnly() {
    return LOCAL_WHOLE_WORDS_ONLY;
  }

  @Override
  public void setLocalCaseSensitive(final boolean caseSensitiveSearch) {
    LOCAL_CASE_SENSITIVE_SEARCH = caseSensitiveSearch;
  }

  @Override
  public void setLocalWholeWordsOnly(final boolean wholeWordsOnly) {
    LOCAL_WHOLE_WORDS_ONLY = wholeWordsOnly;
  }

  @Override
  public boolean isPreserveCase() {
    return PRESERVE_CASE_REPLACE;
  }

  @Override
  public void setPreserveCase(boolean preserveCase) {
    PRESERVE_CASE_REPLACE = preserveCase;
  }

  @Override
  public boolean isWholeWordsOnly(){
    return WHOLE_WORDS_ONLY;
  }

  @Override
  public void setWholeWordsOnly(boolean wholeWordsOnly){
    WHOLE_WORDS_ONLY = wholeWordsOnly;
  }

  @Override
  public boolean isRegularExpressions(){
    return REGULAR_EXPRESSIONS;
  }

  @Override
  public void setRegularExpressions(boolean regularExpressions){
    REGULAR_EXPRESSIONS = regularExpressions;
  }

  @Override
  public boolean isLocalRegularExpressions() {
    return LOCAL_REGULAR_EXPRESSIONS;
  }

  @Override
  public void setLocalRegularExpressions(boolean regularExpressions) {
    LOCAL_REGULAR_EXPRESSIONS = regularExpressions;
  }

  @Override
  public void setWithSubdirectories(boolean b){
    WITH_SUBDIRECTORIES = b;
  }

  private boolean isWithSubdirectories(){
    return WITH_SUBDIRECTORIES;
  }

  @Override
  public void initModelBySetings(@NotNull FindModel model){
    model.setCaseSensitive(isCaseSensitive());
    model.setForward(isForward());
    model.setFromCursor(isFromCursor());
    model.setGlobal(isGlobal());
    model.setRegularExpressions(isRegularExpressions());
    model.setWholeWordsOnly(isWholeWordsOnly());
    FindModel.SearchContext searchContext = isInCommentsOnly() ?
                                      FindModel.SearchContext.IN_COMMENTS :
                                      isInStringLiteralsOnly() ?
                                      FindModel.SearchContext.IN_STRING_LITERALS :
                                      isExceptComments() ?
                                      FindModel.SearchContext.EXCEPT_COMMENTS :
                                      isExceptStringLiterals() ?
                                      FindModel.SearchContext.EXCEPT_STRING_LITERALS :
                                      isExceptCommentsAndLiterals() ?
                                      FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS :
                                      FindModel.SearchContext.ANY;
    model.setSearchContext(searchContext);
    model.setWithSubdirectories(isWithSubdirectories());
    model.setFileFilter(FILE_MASK);

    model.setCustomScopeName(FIND_SCOPE);
  }

  private static void addStringToList(@NotNull String str, @NotNull List<String> list, int maxSize) {
    if (list.contains(str)) {
      list.remove(str);
    }
    list.add(str);
    while (list.size() > maxSize) {
      list.remove(0);
    }
  }

  @Override
  public void addStringToFind(@NotNull String s){
    if (s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0){
      return;
    }
    addStringToList(s, FindRecents.getInstance().findStrings, MAX_RECENT_SIZE);
  }

  @Override
  public void addStringToReplace(@NotNull String s) {
    if (s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0){
      return;
    }
    addStringToList(s, FindRecents.getInstance().replaceStrings, MAX_RECENT_SIZE);
  }

  @Override
  public void addDirectory(@NotNull String s) {
    if (s.isEmpty()){
      return;
    }
    addStringToList(s, FindRecents.getInstance().dirStrings, MAX_RECENT_SIZE);
  }

  @NotNull
  @Override
  public String[] getRecentFindStrings(){
    return ArrayUtil.toStringArray(FindRecents.getInstance().findStrings);
  }

  @NotNull
  @Override
  public String[] getRecentReplaceStrings(){
    return ArrayUtil.toStringArray(FindRecents.getInstance().replaceStrings);
  }

  @NotNull
  @Override
  public String[] getRecentFileMasks() {
    return ArrayUtil.toStringArray(recentFileMasks);
  }

  @NotNull
  @Override
  public List<String> getRecentDirectories(){
    return new ArrayList<>(FindRecents.getInstance().dirStrings);
  }

  @Override
  @Transient
  public String getFileMask() {
    return FILE_MASK;
  }

  @Override
  public void setFileMask(String _fileMask) {
    FILE_MASK = _fileMask;
    if (!StringUtil.isEmptyOrSpaces(_fileMask)) {
      addStringToList(_fileMask, recentFileMasks, MAX_RECENT_SIZE);
    }
  }

  @Override
  public String getCustomScope() {
    return SEARCH_SCOPE;
  }

  @Override
  public boolean isInStringLiteralsOnly() {
    return STRING_LITERALS_ONLY;
  }

  @Override
  public boolean isInCommentsOnly() {
    return COMMENTS_ONLY;
  }

  @Override
  public void setInCommentsOnly(boolean selected) {
    COMMENTS_ONLY = selected;
  }

  @Override
  public void setInStringLiteralsOnly(boolean selected) {
    STRING_LITERALS_ONLY = selected;
  }

  @Override
  public void setCustomScope(final String SEARCH_SCOPE) {
    this.SEARCH_SCOPE = SEARCH_SCOPE;
  }

  @Override
  public boolean isExceptComments() {
    return EXCEPT_COMMENTS;
  }

  @Override
  public void setExceptCommentsAndLiterals(boolean selected) {
    EXCEPT_COMMENTS_AND_STRING_LITERALS = selected;
  }

  @Override
  public boolean isShowResultsInSeparateView() {
    return SHOW_RESULTS_IN_SEPARATE_VIEW;
  }

  @Override
  public void setShowResultsInSeparateView(boolean optionValue) {
    SHOW_RESULTS_IN_SEPARATE_VIEW = optionValue;
  }

  @Override
  public boolean isExceptCommentsAndLiterals() {
    return EXCEPT_COMMENTS_AND_STRING_LITERALS;
  }

  @Override
  public void setExceptComments(boolean selected) {
    EXCEPT_COMMENTS = selected;
  }

  @Override
  public boolean isExceptStringLiterals() {
    return EXCEPT_STRING_LITERALS;
  }

  @Override
  public void setExceptStringLiterals(boolean selected) {
    EXCEPT_STRING_LITERALS = selected;
  }

  @State(
    name = "FindRecents",
    storages = {@Storage(value = "find.recents.xml", roamingType = RoamingType.DISABLED)}
  )
  static final class FindRecents implements PersistentStateComponent<FindRecents> {
    public static FindRecents getInstance() {
      return ServiceManager.getService(FindRecents.class);
    }

    @Tag("findStrings")
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "find", elementValueAttribute = "")
    public List<String> findStrings = new ArrayList<>();

    @Tag("replaceStrings")
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "replace", elementValueAttribute = "")
    public List<String> replaceStrings = new ArrayList<>();

    @Tag("dirStrings")
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "dir", elementValueAttribute = "")
    public List<String> dirStrings = new ArrayList<>();

    @Override
    public void loadState(FindRecents state) {
      XmlSerializerUtil.copyBean(state, this);
      //Avoid duplicates
      LinkedHashSet<String> tmp = new LinkedHashSet<>(findStrings);
      findStrings.clear();
      findStrings.addAll(tmp);

      tmp.clear();
      tmp.addAll(replaceStrings);
      replaceStrings.clear();
      replaceStrings.addAll(tmp);

      tmp.clear();
      tmp.addAll(dirStrings);
      dirStrings.clear();
      dirStrings.addAll(tmp);
    }

    @Override
    public FindRecents getState() {
      return this;
    }
  }
}
