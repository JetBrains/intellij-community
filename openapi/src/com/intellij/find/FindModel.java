
package com.intellij.find;

public class FindModel implements Cloneable {
  private String myStringToFind;
  private String myStringToReplace;
  private boolean isSearchHighlighters = false;
  private boolean isReplaceState = false;
  private boolean isWholeWordsOnly = false;
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
  private String moduleName;
  private String directoryName = null;
  private boolean isWithSubdirectories = true;
  private String fileFilter;

  public boolean isPreserveCase() {
    return isPreserveCase;
  }

  public void setPreserveCase(boolean preserveCase) {
    isPreserveCase = preserveCase;
  }

  private boolean isPreserveCase = false;

  public void copyFrom (FindModel model) {
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
  }

  public String getStringToFind() {
    return myStringToFind;
  }

  public void setStringToFind(String s) {
    myStringToFind = s;
  }

  public String getStringToReplace() {
    return myStringToReplace;
  }

  public void setStringToReplace(String s) {
    myStringToReplace = s;
  }

  public boolean isReplaceState() {
    return isReplaceState;
  }

  public void setReplaceState(boolean val) {
    isReplaceState = val;
  }

  public boolean isFromCursor() {
    return isFromCursor;
  }

  public void setFromCursor(boolean val) {
    isFromCursor = val;
  }

  public boolean isForward() {
    return isForward;
  }

  public void setForward(boolean val) {
    isForward = val;
  }

  public boolean isRegularExpressions() {
    return isRegularExpressions;
  }

  public void setRegularExpressions(boolean val) {
    isRegularExpressions = val;
  }

  public boolean isCaseSensitive() {
    return isCaseSensitive;
  }

  public void setCaseSensitive(boolean val) {
    isCaseSensitive = val;
  }

  public boolean isMultipleFiles() {
    return isMultipleFiles;
  }

  public void setMultipleFiles(boolean val) {
    isMultipleFiles = val;
  }

  public boolean isPromptOnReplace() {
    return isPromptOnReplace;
  }

  public void setPromptOnReplace(boolean val) {
    isPromptOnReplace = val;
  }

  public boolean isWholeWordsOnly() {
    return isWholeWordsOnly;
  }

  public void setWholeWordsOnly(boolean isWholeWordsOnly) {
    this.isWholeWordsOnly = isWholeWordsOnly;
  }

  public boolean isGlobal() {
    return isGlobal;
  }

  public void setGlobal(boolean isGlobal) {
    this.isGlobal = isGlobal;
  }

  public boolean isReplaceAll() {
    return isReplaceAll;
  }

  public void setReplaceAll(boolean replaceAll) {
    this.isReplaceAll = replaceAll;
  }

  public boolean isOpenInNewTab() {
    return isOpenNewTab;
  }

  public void setOpenInNewTab(boolean showInNewTab) {
    isOpenNewTab = showInNewTab;
  }

  public boolean isOpenInNewTabEnabled() {
    return isOpenInNewTabEnabled;
  }

  public void setOpenInNewTabEnabled(boolean showInNewTabEnabled) {
    isOpenInNewTabEnabled = showInNewTabEnabled;
  }

  public boolean isOpenInNewTabVisible() {
    return isOpenNewTabVisible;
  }

  public void setOpenInNewTabVisible(boolean showInNewTabVisible) {
    isOpenNewTabVisible = showInNewTabVisible;
  }

  public String getDirectoryName() {
    return directoryName;
  }

  public void setDirectoryName(String directoryName) {
    this.directoryName = directoryName;
  }

  public boolean isWithSubdirectories() {
    return isWithSubdirectories;
  }

  public void setWithSubdirectories(boolean withSubdirectories) {
    isWithSubdirectories = withSubdirectories;
  }

  public boolean isProjectScope() {
    return isProjectScope;
  }

  public void setProjectScope(boolean projectScope) {
    isProjectScope = projectScope;
  }

  public Object clone() {
    // throws CloneNotSupportedException {
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }


  public String toString(){
    StringBuffer buffer=new StringBuffer();
    buffer.append("--- FIND MODEL ---\n");
    buffer.append("myStringToFind ="+ myStringToFind+"\n");
    buffer.append("myStringToReplace ="+ myStringToReplace + "\n");
    buffer.append("isReplaceState ="+ isReplaceState + "\n");
    buffer.append("isWholeWordsOnly ="+ isWholeWordsOnly + "\n");
    buffer.append("isFromCursor ="+ isFromCursor + "\n");
    buffer.append("isForward ="+ isForward + "\n");
    buffer.append("isGlobal ="+ isGlobal + "\n");
    buffer.append("isRegularExpressions ="+ isRegularExpressions + "\n");
    buffer.append("isCaseSensitive ="+ isCaseSensitive + "\n");
    buffer.append("isMultipleFiles ="+ isMultipleFiles + "\n");
    buffer.append("isPromptOnReplace ="+ isPromptOnReplace + "\n");
    buffer.append("isReplaceAll ="+ isReplaceAll + "\n");
    buffer.append("isOpenNewTab ="+ isOpenNewTab + "\n");
    buffer.append("isOpenInNewTabEnabled ="+ isOpenInNewTabEnabled + "\n");
    buffer.append("isOpenNewTabVisible ="+ isOpenNewTabVisible + "\n");
    buffer.append("isProjectScope ="+ isProjectScope + "\n");
    buffer.append("directoryName ="+ directoryName + "\n");
    buffer.append("isWithSubdirectories ="+ isWithSubdirectories + "\n");
    buffer.append("fileFilter ="+ fileFilter + "\n");
    buffer.append("moduleName ="+ moduleName + "\n");
    return buffer.toString();
  }

  public boolean searchHighlighters() {
    return isSearchHighlighters;
  }

  public void setSearchHighlighters(boolean search) {
    isSearchHighlighters = search;
  }

  public String getFileFilter() {
    return fileFilter;
  }

  public void setFileFilter(String fileFilter) {
    this.fileFilter = fileFilter;
  }

  public String getModuleName() {
    return moduleName;
  }

  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }
}