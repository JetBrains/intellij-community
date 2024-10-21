// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.find.impl.FindSettingsImpl.getDefaultSearchScope;

@ApiStatus.Internal
public class FindSettingsBase extends FindSettings implements PersistentStateComponent<FindSettingsBase> {
  private static final @NonNls String FIND_DIRECTION_FORWARD = "forward";
  private static final @NonNls String FIND_DIRECTION_BACKWARD = "backward";
  private static final @NonNls String FIND_ORIGIN_FROM_CURSOR = "from_cursor";
  private static final @NonNls String FIND_ORIGIN_ENTIRE_SCOPE = "entire_scope";
  private static final @NonNls String FIND_SCOPE_GLOBAL = "global";
  private static final @NonNls String FIND_SCOPE_SELECTED = "selected";

  public FindSettingsBase() {
  }

  @Override
  public boolean isSearchOverloadedMethods() {
    return SEARCH_OVERLOADED_METHODS;
  }

  @Override
  public void setSearchOverloadedMethods(boolean search) {
    SEARCH_OVERLOADED_METHODS = search;
  }

  @SuppressWarnings("WeakerAccess") public boolean SEARCH_OVERLOADED_METHODS;
  @SuppressWarnings("WeakerAccess") public boolean SKIP_RESULTS_WHEN_ONE_USAGE;

  @SuppressWarnings("WeakerAccess") public String FIND_DIRECTION = FIND_DIRECTION_FORWARD;
  @SuppressWarnings("WeakerAccess") public String FIND_ORIGIN = FIND_ORIGIN_FROM_CURSOR;
  @SuppressWarnings("WeakerAccess") public String FIND_SCOPE = FIND_SCOPE_GLOBAL;

  @SuppressWarnings("WeakerAccess") public boolean CASE_SENSITIVE_SEARCH;
  @SuppressWarnings("WeakerAccess") public boolean LOCAL_CASE_SENSITIVE_SEARCH;
  @SuppressWarnings("WeakerAccess") public boolean PRESERVE_CASE_REPLACE;
  @SuppressWarnings("WeakerAccess") public boolean WHOLE_WORDS_ONLY;
  @SuppressWarnings("WeakerAccess") public boolean COMMENTS_ONLY;
  @SuppressWarnings("WeakerAccess") public boolean STRING_LITERALS_ONLY;
  @SuppressWarnings("WeakerAccess") public boolean EXCEPT_COMMENTS;
  @SuppressWarnings("WeakerAccess") public boolean EXCEPT_COMMENTS_AND_STRING_LITERALS;
  @SuppressWarnings("WeakerAccess") public boolean EXCEPT_STRING_LITERALS;
  @SuppressWarnings("WeakerAccess") public boolean LOCAL_WHOLE_WORDS_ONLY;
  @SuppressWarnings("WeakerAccess") public boolean REGULAR_EXPRESSIONS;
  @SuppressWarnings("WeakerAccess") public boolean LOCAL_REGULAR_EXPRESSIONS;
  @SuppressWarnings("WeakerAccess") public boolean WITH_SUBDIRECTORIES = true;
  @SuppressWarnings("WeakerAccess") public boolean SHOW_RESULTS_IN_SEPARATE_VIEW;

  @SuppressWarnings("WeakerAccess") public @Nls String SEARCH_SCOPE = getDefaultSearchScope();
  @SuppressWarnings("WeakerAccess") public String FILE_MASK;

  @Property(surroundWithTag = false)
  @XCollection(propertyElementName = "recentFileMasks", elementName = "mask", valueAttributeName = "")
  protected final List<String> recentFileMasks = new ArrayList<>();

  @Override
  public final void loadState(@NotNull FindSettingsBase state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public FindSettingsBase getState() {
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
  public @Nls String getDefaultScopeName() {
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
  }

  @Override
  public final @NlsSafe String @NotNull [] getRecentFileMasks() {
    return ArrayUtilRt.toStringArray(recentFileMasks);
  }

  @Override
  @Transient
  public final @Nullable @NlsSafe String getFileMask() {
    return FILE_MASK;
  }

  @Override
  public final void setFileMask(@Nullable @NlsSafe String fileMask) {
    FILE_MASK = fileMask;
    FindInProjectSettingsBase.addRecentStringToList(fileMask, recentFileMasks);
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
}
