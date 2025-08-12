// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.SearchScope
import com.intellij.util.PatternUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.UnknownNullability
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@Serializable
open class FindModel : UserDataHolder, Cloneable {

  private var myStringToFind: String? = null


  var stringToFind: String
    /**
     * Gets the string to find.
     *
     * @return the string to find.
     */
    get() = (if (myStringToFind == null) "" else myStringToFind)!!
    /**
     * Sets the string to find.
     */
    set(value) {
      val changed = !StringUtil.equals(value, myStringToFind)
      if (changed) {
        myStringToFind = value
        myPattern = PatternUtil.NOTHING
        notifyObservers()
      }
    }

  fun hasStringToFind(): Boolean {
    return myStringToFind != null
  }

  private var myStringToReplace: String? = ""


  var stringToReplace: String
    /**
     * Gets the string to replace with.
     *
     * @return the string to replace with.
     */
    get() = myStringToReplace!!
    /**
     * Sets the string to replace with.
     */
    set(value) {
      val changed = !StringUtil.equals(value, myStringToReplace)
      if (changed) {
        myStringToReplace = value
        notifyObservers()
      }
    }

  /**
   * The flag indicating whether the search operation is Find Next / Find Previous
   * after Highlight Usages in Files.
   *
   * @return true if the operation moves between highlighted regions, false otherwise.
   */
  @get:JvmName("isSearchHighlighters")
  @set:JvmName("setSearchHighlighters")
  var isSearchHighlighters: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The value indicating whether the operation is a Find or a Replace.
   *
   * @return true if the operation is a Replace, false if it is a Find.
   */

  @get:JvmName("isReplaceState")
  @set:JvmName("setReplaceState")
  var isReplaceState: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The Whole Words Only flag.
   */
  @get:JvmName("isWholeWordsOnly")
  @set:JvmName("setWholeWordsOnly")
  var isWholeWordsOnly: Boolean = false
    set(value) {
      val changed = value != field
      if (changed) {
        field = value
        notifyObservers()
      }
    }

  var searchContext: SearchContext = SearchContext.ANY
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The origin for the find.
   *
   * @return true if the origin is From Cursor, false if it is Entire Scope.
   */
  @get:JvmName("isFromCursor")
  @set:JvmName("setFromCursor")
  var isFromCursor: Boolean = true
    set(value) {
      val changed = value != field
      if (changed) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The direction for the find.
   *
   * @return true if the find is forward, false if it is backward.
   */
  @get:JvmName("isForward")
  @set:JvmName("setForward")
  var isForward: Boolean = true
    set(value) {
      val changed = value != field
      if (changed) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The scope of the operation.
   *
   * @return true if the operation affects the entire file, false if it affects the selected text.
   */
  @get:JvmName("isGlobal")
  @set:JvmName("setGlobal")
  var isGlobal: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The Regular Expressions flag.
   *
   * @return the value of the Regular Expressions flag.
   */
  @get:JvmName("isRegularExpressions")
  @set:JvmName("setRegularExpressions")
  var isRegularExpressions: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  @get:JvmName("getRegExpFlags")
  @set:JvmName("setRegExpFlags")
  @MagicConstant(flagsFromClass = Pattern::class)
  var regExpFlags: Int = 0
    set(@MagicConstant(flagsFromClass = Pattern::class) value) {
      if (value != field) {
        field = value
        myPattern = PatternUtil.NOTHING
        notifyObservers()
      }
    }

  /**
   * The Case Sensitive flag.
   *
   * @return the value of the Case Sensitive flag.
   */
  @get:JvmName("isCaseSensitive")
  @set:JvmName("setCaseSensitive")
  var isCaseSensitive: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        myPattern = PatternUtil.NOTHING
        notifyObservers()
      }
    }

  /**
   * Checks if the find or replace operation affects multiple files.
   *
   * @return true if the operation affects multiple files, false if it affects a single file.
   */
  @get:JvmName("isMultipleFiles")
  @set:JvmName("setMultipleFiles")
  var isMultipleFiles: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The Prompt on Replace flag.
   *
   * @return the value of the Prompt on Replace flag.
   */
  @get:JvmName("isPromptOnReplace")
  @set:JvmName("setPromptOnReplace")
  var isPromptOnReplace: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The Replace All flag.
   *
   * @return the value of the Replace All flag.
   */
  @get:JvmName("isReplaceAll")
  @set:JvmName("setReplaceAll")
  var isReplaceAll: Boolean = false
    set(value) {
      field = value
      notifyObservers()
    }

  /**
   * The flag indicating whether the Whole Project scope is selected for Find in Path /
   * Replace in Path.
   *
   * @return true if the whole project scope is selected, false otherwise.
   */
  @get:JvmName("isProjectScope")
  @set:JvmName("setProjectScope")
  var isProjectScope: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The flag indicating whether "Find All" button was used to initiate the find
   * operation.
   */
  @get:JvmName("isFindAll")
  @set:JvmName("setFindAll")
  var isFindAll: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The flag indicating whether "Find All" button is allowed for the operation.
   */
  @get:JvmName("isFindAllEnabled")
  @set:JvmName("setFindAllEnabled")
  var isFindAllEnabled: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The name of the module used as the scope for the Find in Path / Replace
   * in Path operation.
   *
   * @return the module name, or null if the selected scope is not "Module".
   */
  var moduleName: String? = null
    set(@NlsSafe value) {
      if (!StringUtil.equals(value, field)) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The directory used as a scope for Find in Path / Replace in Path.
   *
   * @return the directory used as a scope, or null if the selected scope is not "Directory".
   */
  var directoryName: String? = null
    set(@NlsSafe value) {
      if (!StringUtil.equals(value, field)) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The Recursive Search flag for Find in Path / Replace in Path.
   *
   * @return true if directories are searched recursively, false otherwise.
   */
  @get:JvmName("isWithSubdirectories")
  @set:JvmName("setWithSubdirectories")
  var isWithSubdirectories: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  /**
   * The file name filter used for Find in Path / Replace in Path operation.
   *
   * @return the file name filter text.
   */
  var fileFilter: String? = null
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }
  var customScopeName: @Nls String? = null
    set(value) {
      if (!StringUtil.equals(value, field)) {
        field = value
        notifyObservers()
      }
    }

  @Transient
  var customScope: SearchScope? = null
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  @ApiStatus.Internal
  var customScopeId: String? = null
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  @get:JvmName("isCustomScope")
  @set:JvmName("setCustomScope")
  var isCustomScope: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  @get:JvmName("isMultiline")
  @set:JvmName("setMultiline")
  var isMultiline: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        notifyObservers()
      }
    }

  private var mySearchInProjectFiles: Boolean = false

  @get:JvmName("isPreserveCase")
  @set:JvmName("setPreserveCase")
  var isPreserveCase: Boolean = false
    set(value) {
      val changed = value != field
      if (changed) {
        field = value
        notifyObservers()
      }
    }

  /**
   * Sets the Open in New Tab flag.
   *
   * @param showInNewTab the value of the Open in New Tab flag.
   */
  @Deprecated("and not used anymore")
  fun setOpenInNewTab(showInNewTab: Boolean) {
  }

  @get:Deprecated("and not used anymore")
  @set:Deprecated("and not used anymore")
  var isOpenInNewTabEnabled: Boolean
    /**
     * Gets the value indicating whether the Open in New Tab flag is enabled for the operation.
     *
     * @return true if Open in New Tab is enabled, false otherwise.
     */
    get() = true
    /**
     * Sets the value indicating whether the Open in New Tab flag is enabled for the operation.
     *
     * @param showInNewTabEnabled true if Open in New Tab is enabled, false otherwise.
     */
    set(showInNewTabEnabled) {
    }

  @Deprecated("and not used anymore")
  fun setOpenInNewTabVisible(showInNewTabVisible: Boolean) {
  }


  @set:Deprecated("Use {@link #setSearchContext(SearchContext)} instead")
  var isInStringLiteralsOnly: Boolean
    get() = searchContext == SearchContext.IN_STRING_LITERALS
    set(inStringLiteralsOnly) {
      doApplyContextChange(inStringLiteralsOnly, SearchContext.IN_STRING_LITERALS)
    }

  val isExceptComments: Boolean
    get() = searchContext == SearchContext.EXCEPT_COMMENTS

  val isExceptStringLiterals: Boolean
    get() = searchContext == SearchContext.EXCEPT_STRING_LITERALS

  @set:Deprecated("Use {@link #setSearchContext(SearchContext)} instead")
  var isInCommentsOnly: Boolean
    get() = searchContext == SearchContext.IN_COMMENTS
    set(inCommentsOnly) {
      doApplyContextChange(inCommentsOnly, SearchContext.IN_COMMENTS)
    }

  val isExceptCommentsAndStringLiterals: Boolean
    get() = searchContext == SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS

  private fun doApplyContextChange(newOptionValue: Boolean, option: SearchContext) {
    var changed = false
    if (newOptionValue) {
      changed = searchContext != option
      searchContext = option
    }
    else if (searchContext == option) { // do not reset unrelated value
      changed = true
      searchContext = SearchContext.ANY
    }

    if (changed) {
      notifyObservers()
    }
  }

  private fun doSetContext(newSearchContext: SearchContext) {
    val changed = newSearchContext != searchContext
    if (changed) {
      searchContext = newSearchContext
      notifyObservers()
    }
  }

  @get:JvmName("isSearchInProjectFiles")
  @set:JvmName("setSearchInProjectFiles")
  var isSearchInProjectFiles: Boolean
    get() {
      if (!mySearchInProjectFiles) {
        if (fileFilter != null) {
          val split = StringUtil.split(fileFilter!!, ",")

          if (split.any { it.endsWith(".iml") || it.endsWith(".ipr") || it.endsWith(".iws") }) {
            return true
          }
        }
        if (directoryName != null) {
          val path = FileUtil.toSystemIndependentName(directoryName!!)
          if (path.endsWith("/.idea") || path.contains("/.idea/")) {
            return true
          }
        }
      }
      return mySearchInProjectFiles
    }
    set(searchInProjectFiles) {
      val changed = mySearchInProjectFiles != searchInProjectFiles
      if (changed) {
        mySearchInProjectFiles = searchInProjectFiles
        notifyObservers()
      }
    }

  @Transient
  private var myPattern: Pattern? = PatternUtil.NOTHING

  fun compileRegExp(): Pattern? {
    var pattern = myPattern
    if (pattern == PatternUtil.NOTHING) {
      var toFind = this.stringToFind
      @MagicConstant(flagsFromClass = Pattern::class) var flags: Int
      if (regExpFlags != 0) {
        flags = regExpFlags // should separate case-sensitive setting be used here?
      }
      else {
        flags = if (isCaseSensitive) Pattern.MULTILINE else Pattern.MULTILINE or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
      }

      // SOE during matching regular expressions is considered to be feature
      // http://bugs.java.com/view_bug.do?bug_id=6882582
      // http://bugs.java.com/view_bug.do?bug_id=5050507
      // IDEA-175066 / https://stackoverflow.com/questions/31676277/stackoverflowerror-in-regular-expression
      if (toFind.contains("\\n") && Registry.`is`("jdk.regex.soe.workaround")) { // if needed use DOT_ALL for modified pattern to avoid SOE
        var modifiedStringToFind = StringUtil.replace(toFind, "\\n|.", ".")
        modifiedStringToFind = StringUtil.replace(modifiedStringToFind, ".|\\n", ".")

        if (modifiedStringToFind != toFind) {
          flags = flags or Pattern.DOTALL
          toFind = modifiedStringToFind
        }
      }
      try {
        pattern = Pattern.compile(toFind, flags)
        myPattern = pattern
      }
      catch (_: PatternSyntaxException) {
        pattern = null
        myPattern = pattern
      }
    }

    return pattern
  }

  /**
   * Copies all the settings from the specified model.
   *
   * @param model the model to copy settings from.
   */
  fun copyFrom(model: FindModel) {
    val changed = !equals(model)
    if (changed) {
      myStringToFind = model.myStringToFind
      myStringToReplace = model.myStringToReplace
      isReplaceState = model.isReplaceState
      isWholeWordsOnly = model.isWholeWordsOnly
      isFromCursor = model.isFromCursor
      isForward = model.isForward
      isGlobal = model.isGlobal
      isRegularExpressions = model.isRegularExpressions
      regExpFlags = model.regExpFlags
      isCaseSensitive = model.isCaseSensitive
      isMultipleFiles = model.isMultipleFiles
      isPromptOnReplace = model.isPromptOnReplace
      isReplaceAll = model.isReplaceAll
      isProjectScope = model.isProjectScope
      directoryName = model.directoryName
      isWithSubdirectories = model.isWithSubdirectories
      isPreserveCase = model.isPreserveCase
      fileFilter = model.fileFilter
      moduleName = model.moduleName
      customScopeName = model.customScopeName
      customScope = model.customScope
      customScopeId = model.customScopeId
      isCustomScope = model.isCustomScope
      isFindAll = model.isFindAll
      searchContext = model.searchContext
      isMultiline = model.isMultiline
      mySearchInProjectFiles = model.mySearchInProjectFiles
      myPattern = model.myPattern
      model.dataHolder.copyCopyableDataTo(dataHolder)
      notifyObservers()
    }
  }


  @Suppress("DuplicatedCode")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val findModel = other as? FindModel?: return false

    if (isCaseSensitive != findModel.isCaseSensitive) return false
    if (isCustomScope != findModel.isCustomScope) return false
    if (isFindAll != findModel.isFindAll) return false
    if (isFindAllEnabled != findModel.isFindAllEnabled) return false
    if (isForward != findModel.isForward) return false
    if (isFromCursor != findModel.isFromCursor) return false
    if (isGlobal != findModel.isGlobal) return false
    if (searchContext != findModel.searchContext) return false

    if (isMultiline != findModel.isMultiline) return false
    if (isMultipleFiles != findModel.isMultipleFiles) return false
    if (isPreserveCase != findModel.isPreserveCase) return false
    if (isProjectScope != findModel.isProjectScope) return false
    if (isPromptOnReplace != findModel.isPromptOnReplace) return false
    if (isRegularExpressions != findModel.isRegularExpressions) return false
    if (regExpFlags != findModel.regExpFlags) return false
    if (isReplaceAll != findModel.isReplaceAll) return false
    if (isReplaceState != findModel.isReplaceState) return false
    if (isSearchHighlighters != findModel.isSearchHighlighters) return false
    if (isWholeWordsOnly != findModel.isWholeWordsOnly) return false
    if (isWithSubdirectories != findModel.isWithSubdirectories) return false
    if (if (customScope != null) (customScope != findModel.customScope) else findModel.customScope != null) return false
    if (if (customScopeId != null) (customScopeId != findModel.customScopeId) else findModel.customScopeId != null) return false
    if (if (customScopeName != null) (customScopeName != findModel.customScopeName) else findModel.customScopeName != null) return false
    if (if (directoryName != null) (directoryName != findModel.directoryName) else findModel.directoryName != null) return false
    if (if (fileFilter != null) (fileFilter != findModel.fileFilter) else findModel.fileFilter != null) return false
    if (if (moduleName != null) (moduleName != findModel.moduleName) else findModel.moduleName != null) return false
    if (if (myStringToFind != null) (myStringToFind != findModel.myStringToFind) else findModel.myStringToFind != null) return false
    if (if (myStringToReplace != null) (myStringToReplace != findModel.myStringToReplace) else findModel.myStringToReplace != null) {
      return false
    }
    if (mySearchInProjectFiles != findModel.mySearchInProjectFiles) return false
    if (!dataHolder.isCopyableDataEqual(findModel.dataHolder)) return false

    return true
  }


  @Suppress("DuplicatedCode")
  override fun hashCode(): Int {
    var result: Int = (myStringToFind?.hashCode() ?: 0)
    result = 31 * result + (myStringToReplace?.hashCode() ?: 0)
    result = 31 * result + (if (isSearchHighlighters) 1 else 0)
    result = 31 * result + (if (isReplaceState) 1 else 0)
    result = 31 * result + (if (isWholeWordsOnly) 1 else 0)
    result = 31 * result + (searchContext.ordinal)
    result = 31 * result + (if (isFromCursor) 1 else 0)
    result = 31 * result + (if (isForward) 1 else 0)
    result = 31 * result + (if (isGlobal) 1 else 0)
    result = 31 * result + (if (isRegularExpressions) 1 else 0)
    result = 31 * result + regExpFlags
    result = 31 * result + (if (isCaseSensitive) 1 else 0)
    result = 31 * result + (if (isMultipleFiles) 1 else 0)
    result = 31 * result + (if (isPromptOnReplace) 1 else 0)
    result = 31 * result + (if (isReplaceAll) 1 else 0)
    result = 31 * result + (if (isProjectScope) 1 else 0)
    result = 31 * result + (if (isFindAll) 1 else 0)
    result = 31 * result + (if (isFindAllEnabled) 1 else 0)
    result = 31 * result + (moduleName?.hashCode() ?: 0)
    result = 31 * result + (directoryName?.hashCode() ?: 0)
    result = 31 * result + (if (isWithSubdirectories) 1 else 0)
    result = 31 * result + (fileFilter?.hashCode() ?: 0)
    result = 31 * result + (customScopeName?.hashCode() ?: 0)
    result = 31 * result + (customScope?.hashCode() ?: 0)
    result = 31 * result + (customScopeId?.hashCode() ?: 0)
    result = 31 * result + (if (isCustomScope) 1 else 0)
    result = 31 * result + (if (isMultiline) 1 else 0)
    result = 31 * result + (if (isPreserveCase) 1 else 0)
    result = 31 * result + (if (mySearchInProjectFiles) 1 else 0)
    return result
  }


  override fun toString(): String {
    return "--- FIND MODEL ---\n" +
           "myStringToFind = " + (if (myStringToFind == null) "null\n" else "'$myStringToFind'\n") +
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
           "userDataMap = " + dataHolder + "\n"
  }

  @Transient
  private val dataHolder = UserDataHolderBase()

  companion object {
    @JvmStatic
    fun initStringToFind(findModel: FindModel, s: String?) {
      if (!StringUtil.isEmpty(s)) {
        if (StringUtil.containsLineBreak(s!!)) {
          findModel.isMultiline = true
        }
        findModel.stringToFind = s
      }
    }
  }

  fun interface FindModelObserver {
    fun findModelChanged(findModel: FindModel)
  }

  @Transient
  private val myObservers = ContainerUtil.createLockFreeCopyOnWriteList<FindModelObserver>()

  fun addObserver(observer: FindModelObserver) {
    myObservers.add(observer)
  }

  fun removeObserver(observer: FindModelObserver) {
    myObservers.remove(observer)
  }

  fun refresh() {
    notifyObservers()
  }

   private fun notifyObservers() {
    for (observer in myObservers) {
      observer.findModelChanged(this)
    }
  }

  public override fun clone(): FindModel {
    try {
      val clone = super.clone() as FindModel
      this.dataHolder.copyUserDataTo(clone.dataHolder)
      return clone
    }
    catch (_: CloneNotSupportedException) {
      Logger.getInstance(FindModel::class.java).error("Cannot clone FindModel: CloneNotSupportedException:")
      return FindModel().apply { copyFrom(this@FindModel) }
    }
  }

  @Serializable
  enum class SearchContext {
    ANY, IN_STRING_LITERALS, IN_COMMENTS, EXCEPT_STRING_LITERALS, EXCEPT_COMMENTS, EXCEPT_COMMENTS_AND_STRING_LITERALS
  }

  /**
   * Determines whether an already running or prepared search can be reused without a restart.
   *
   * Returns true when:
   * - all settings are identical between the given [oldModel] and this model; or
   * - the only differences are Preserve Case ([isPreserveCase]),
   * Replace/Find mode ([isReplaceState]), and/or String to Replace ([myStringToReplace]).
   *
   * Any change to any other field requires a restart, and the method returns false.
   *
   * @param oldModel previously used FindModel to compare with
   * @return true if no restart is needed (only Preserve Case, Replace State, and/or String to Replace may differ), false otherwise
   */
  @ApiStatus.Internal
  fun noRestartSearchNeeded(oldModel: FindModel): Boolean {
    if (oldModel == this) return true
    val adjusted = this.clone()
    adjusted.isPreserveCase = oldModel.isPreserveCase
    adjusted.isReplaceState = oldModel.isReplaceState
    adjusted.myStringToReplace = oldModel.myStringToReplace
    return adjusted == oldModel
  }

  override fun <T> getUserData(key: Key<T>): T? {
    return dataHolder.getUserData<T>(key)
  }

  override fun <T> putUserData(key: Key<T>, value: T?) {
    val changed = value != getUserData(key)
    dataHolder.putUserData<T>(key, value)
    if (changed) {
      notifyObservers()
    }
  }

  fun <T> putCopyableUserData(key: Key<T>, value: T?) {
    val changed = value != dataHolder.getCopyableUserData(key)
    dataHolder.putCopyableUserData<T>(key, value)
    if (changed) {
      notifyObservers()
    }
  }

  fun <T> getCopyableUserData(key: Key<T>): @UnknownNullability T? {
    return dataHolder.getCopyableUserData<T?>(key)
  }
}