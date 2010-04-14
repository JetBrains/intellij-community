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

package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "FindSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class FindSettingsImpl extends FindSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindSettingsImpl");

  @NonNls private static final String FIND_DIRECTION_FORWARD = "forward";
  @NonNls private static final String FIND_DIRECTION_BACKWARD = "backward";
  @NonNls private static final String FIND_ORIGIN_FROM_CURSOR = "from_cursor";
  @NonNls private static final String FIND_ORIGIN_ENTIRE_SCOPE = "entire_scope";
  @NonNls private static final String FIND_SCOPE_GLOBAL = "global";
  @NonNls private static final String FIND_SCOPE_SELECTED = "selected";
  private static final String DEFAULT_SEARCH_SCOPE = FindBundle.message("find.scope.all.project.classes");
  
  private static final int MAX_RECENT_SIZE = 30;


  public boolean isSearchOverloadedMethods() {
    return SEARCH_OVERLOADED_METHODS;
  }

  public void setSearchOverloadedMethods(boolean search) {
    SEARCH_OVERLOADED_METHODS = search;
  }

  @SuppressWarnings({"WeakerAccess"}) public boolean SEARCH_OVERLOADED_METHODS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean SEARCH_IN_LIBRARIES = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean SEARCH_FOR_TEXT_OCCURENCES = true;
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
  @SuppressWarnings({"WeakerAccess"}) public boolean LOCAL_WHOLE_WORDS_ONLY = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean REGULAR_EXPRESSIONS = false;
  @SuppressWarnings({"WeakerAccess"}) public boolean WITH_SUBDIRECTORIES = true;

  @SuppressWarnings({"WeakerAccess"}) public String SEARCH_SCOPE = DEFAULT_SEARCH_SCOPE;
  @SuppressWarnings({"WeakerAccess"}) public String FILE_MASK;

  @SuppressWarnings({"WeakerAccess"}) public JDOMExternalizableStringList RECENT_FIND_STRINGS = new JDOMExternalizableStringList();
  @SuppressWarnings({"WeakerAccess"}) public JDOMExternalizableStringList RECENT_REPLACE_STRINGS = new JDOMExternalizableStringList();
  @SuppressWarnings({"WeakerAccess"}) public JDOMExternalizableStringList RECENT_DIR_STRINGS = new JDOMExternalizableStringList();
  @SuppressWarnings({"WeakerAccess"}) @NonNls public JDOMExternalizableStringList RECENT_FILE_MASKS = new JDOMExternalizableStringList();


  public void loadState(final Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.info(e);
    }
    if (RECENT_FILE_MASKS.isEmpty()) {
      RECENT_FILE_MASKS.add("*.properties");
      RECENT_FILE_MASKS.add("*.html");
      RECENT_FILE_MASKS.add("*.jsp");
      RECENT_FILE_MASKS.add("*.xml");
      RECENT_FILE_MASKS.add("*.java");
      RECENT_FILE_MASKS.add("*.php");
      RECENT_FILE_MASKS.add("*.js");
      RECENT_FILE_MASKS.add("*.as");
      RECENT_FILE_MASKS.add("*.css");
      RECENT_FILE_MASKS.add("*.mxml");
    }
  }

  public Element getState() {
    Element element = new Element("state");
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    return element;
  }

  public boolean isSkipResultsWithOneUsage(){
    return SKIP_RESULTS_WHEN_ONE_USAGE;
  }

  public void setSkipResultsWithOneUsage(boolean skip){
    SKIP_RESULTS_WHEN_ONE_USAGE = skip;
  }

  public boolean isSearchForTextOccurences(){
    return SEARCH_FOR_TEXT_OCCURENCES;
  }

  public void setSearchForTextOccurences(boolean search){
    SEARCH_FOR_TEXT_OCCURENCES = search;
  }

  public String getDefaultScopeName() {
    return SEARCH_SCOPE;
  }

  public void setDefaultScopeName(String scope) {
    SEARCH_SCOPE = scope;
  }

  public boolean isForward(){
    return FIND_DIRECTION_FORWARD.equals(FIND_DIRECTION);
  }

  public void setForward(boolean findDirectionForward){
    FIND_DIRECTION = findDirectionForward ? FIND_DIRECTION_FORWARD : FIND_DIRECTION_BACKWARD;
  }

  public boolean isFromCursor(){
    return FIND_ORIGIN_FROM_CURSOR.equals(FIND_ORIGIN);
  }

  public void setFromCursor(boolean findFromCursor){
    FIND_ORIGIN = findFromCursor ? FIND_ORIGIN_FROM_CURSOR : FIND_ORIGIN_ENTIRE_SCOPE;
  }

  public boolean isGlobal(){
    return FIND_SCOPE_GLOBAL.equals(FIND_SCOPE);
  }

  public void setGlobal(boolean findGlobalScope){
    FIND_SCOPE = findGlobalScope ? FIND_SCOPE_GLOBAL : FIND_SCOPE_SELECTED;
  }

  public boolean isCaseSensitive(){
    return CASE_SENSITIVE_SEARCH;
  }

  public void setCaseSensitive(boolean caseSensitiveSearch){
    CASE_SENSITIVE_SEARCH = caseSensitiveSearch;
  }

  public boolean isLocalCaseSensitive() {
    return LOCAL_CASE_SENSITIVE_SEARCH;
  }

  public boolean isLocalWholeWordsOnly() {
    return LOCAL_WHOLE_WORDS_ONLY;
  }

  public void setLocalCaseSensitive(final boolean caseSensitiveSearch) {
    LOCAL_CASE_SENSITIVE_SEARCH = caseSensitiveSearch;
  }

  public void setLocalWholeWordsOnly(final boolean wholeWordsOnly) {
    LOCAL_WHOLE_WORDS_ONLY = wholeWordsOnly;
  }

  public boolean isPreserveCase() {
    return PRESERVE_CASE_REPLACE;
  }

  public void setPreserveCase(boolean preserveCase) {
    PRESERVE_CASE_REPLACE = preserveCase;
  }

  public boolean isWholeWordsOnly(){
    return WHOLE_WORDS_ONLY;
  }

  public void setWholeWordsOnly(boolean wholeWordsOnly){
    WHOLE_WORDS_ONLY = wholeWordsOnly;
  }

  public boolean isRegularExpressions(){
    return REGULAR_EXPRESSIONS;
  }

  public void setRegularExpressions(boolean regularExpressions){
    REGULAR_EXPRESSIONS = regularExpressions;
  }

  public void setWithSubdirectories(boolean b){
    WITH_SUBDIRECTORIES = b;
  }

  private boolean isWithSubdirectories(){
    return WITH_SUBDIRECTORIES;
  }

  public void initModelBySetings(FindModel model){
    model.setCaseSensitive(isCaseSensitive());
    model.setForward(isForward());
    model.setFromCursor(isFromCursor());
    model.setGlobal(isGlobal());
    model.setRegularExpressions(isRegularExpressions());
    model.setWholeWordsOnly(isWholeWordsOnly());
    model.setInCommentsOnly(isInCommentsOnly());
    model.setInStringLiteralsOnly(isInStringLiteralsOnly());
    model.setWithSubdirectories(isWithSubdirectories());
    model.setFileFilter(FILE_MASK);

    model.setCustomScopeName(FIND_SCOPE);
  }

  private static void addStringToList(String str, List<String> list, int maxSize){
    if(list.contains(str)){
      list.remove(str);
    }
    list.add(str);
    while(list.size() > maxSize){
      list.remove(0);
    }
  }

  public void addStringToFind(String s){
    if (s == null || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0){
      return;
    }
    addStringToList(s, RECENT_FIND_STRINGS, MAX_RECENT_SIZE);
  }

  public void addStringToReplace(String s) {
    if (s == null || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0){
      return;
    }
    addStringToList(s, RECENT_REPLACE_STRINGS, MAX_RECENT_SIZE);
  }

  public void addDirectory(String s) {
    if (s == null || s.length() == 0){
      return;
    }
    addStringToList(s, RECENT_DIR_STRINGS, MAX_RECENT_SIZE);
  }

  public String[] getRecentFindStrings(){
    return ArrayUtil.toStringArray(RECENT_FIND_STRINGS);
  }

  public String[] getRecentReplaceStrings(){
    return ArrayUtil.toStringArray(RECENT_REPLACE_STRINGS);
  }

  public String[] getRecentFileMasks() {
    return ArrayUtil.toStringArray(RECENT_FILE_MASKS);
  }

  public ArrayList<String> getRecentDirectories(){
    return new ArrayList<String>(RECENT_DIR_STRINGS);
  }

  public String getFileMask() {
    return FILE_MASK;
  }

  public void setFileMask(String _fileMask) {
    FILE_MASK = _fileMask;
    if (_fileMask != null && _fileMask.length() > 0) {
      addStringToList(_fileMask, RECENT_FILE_MASKS, MAX_RECENT_SIZE);
    }
  }

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

  public void setCustomScope(final String SEARCH_SCOPE) {
    this.SEARCH_SCOPE = SEARCH_SCOPE;
  }
}
