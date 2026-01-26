// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.enums.SortBy
import com.intellij.ide.plugins.enums.SortBy.Companion.getByQueryOrNull
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SearchQueryParser {
  @JvmField var searchQuery: String? = null

  protected open fun addToSearchQuery(query: String) {
    if (searchQuery == null) {
      searchQuery = query
    }
    else {
      searchQuery += " $query"
    }
  }

  @ApiStatus.Internal
  open class Marketplace(query: String) : SearchQueryParser() {
    @JvmField val vendors: MutableSet<String> = HashSet()
    @JvmField val tags: MutableSet<String> = HashSet()
    @JvmField val repositories: MutableSet<String> = HashSet()
    @JvmField var sortBy: SortBy? = null
    @JvmField var suggested: Boolean = false
    @JvmField var internal: Boolean = false

    var staffPicks: Boolean = false

    init {
      parse(query)
    }

    private fun parse(query: String) {
      val words: MutableList<String> = splitQuery(query)
      val size = words.size

      if (size == 0) {
        return
      }
      if (size == 1) {
        addToSearchQuery(words[0])
        return
      }

      var index = 0
      while (index < size) {
        val name = words[index++]
        if (name.endsWith(":")) {
          if (index < size) {
            handleAttribute(name, words[index++])
          }
          else {
            addToSearchQuery(query)
            return
          }
        }
        else {
          addToSearchQuery(name)
        }
      }
    }

    override fun addToSearchQuery(query: String) {
      when (query) {
        SearchWords.SUGGESTED.value -> suggested = true
        SearchWords.INTERNAL.value -> internal = true
        SearchWords.STAFF_PICKS.value -> staffPicks = true
        else -> super.addToSearchQuery(query)
      }
    }

    protected open fun handleAttribute(name: String, value: String) {
      when (name) {
        SearchWords.TAG.value -> tags.add(value)
        SearchWords.SORT_BY.value -> sortBy = getByQueryOrNull(value)
        SearchWords.REPOSITORY.value -> repositories.add(value)
        SearchWords.VENDOR.value -> vendors.add(value)
      }
    }

    val urlQuery: String
      get() {
        val url = StringBuilder()

        if (sortBy != null) {
          url.append(sortBy!!.mpParameter)
        }

        if (staffPicks) {
          if (!url.isEmpty()) {
            url.append("&")
          }
          url.append("is_featured_search=true")
        }

        for (tag in tags) {
          if (!url.isEmpty()) {
            url.append("&")
          }
          url.append("tags=").append(URLUtil.encodeURIComponent(tag))
        }

        for (vendor in vendors) {
          if (!url.isEmpty()) {
            url.append("&")
          }
          url.append("organization=").append(URLUtil.encodeURIComponent(vendor))
        }

        if (searchQuery != null) {
          if (!url.isEmpty()) {
            url.append("&")
          }
          url.append("search=").append(URLUtil.encodeURIComponent(searchQuery!!))
        }

        return url.toString()
      }
  }

  @ApiStatus.Internal
  open class Installed(query: String) : SearchQueryParser() {
    @JvmField val vendors: MutableSet<String> = HashSet()
    @JvmField val tags: MutableSet<String> = HashSet()
    @JvmField var enabled: Boolean = false
    @JvmField var disabled: Boolean = false
    @JvmField var bundled: Boolean = false
    @JvmField var downloaded: Boolean = false
    @JvmField var invalid: Boolean = false
    @JvmField var needUpdate: Boolean = false
    @JvmField var attributes: Boolean = false

    init {
      parse(query)
    }

    private fun parse(query: String) {
      val words: MutableList<String> = splitQuery(query)
      val size = words.size

      if (size == 0) {
        return
      }

      var index = 0
      while (index < size) {
        val name = words[index++]
        if (name.startsWith("/")) {
          if (name == SearchWords.VENDOR.value || name == SearchWords.TAG.value) {
            if (index < size) {
              handleAttribute(name, words[index++])
            }
            else {
              addToSearchQuery(query)
              break
            }
          }
          else {
            handleAttribute(name, "")
          }
        }
        else {
          addToSearchQuery(name)
        }
      }

      attributes = enabled || disabled || bundled || downloaded || invalid || needUpdate
    }

    protected open fun handleAttribute(name: String, value: String) {
      if ("/enabled" == name) {
        enabled = true
      }
      else if ("/disabled" == name) {
        disabled = true
      }
      else if ("/bundled" == name) {
        bundled = true
      }
      else if ("/downloaded" == name) {
        downloaded = true
      }
      else if ("/invalid" == name) {
        invalid = true
      }
      else if ("/outdated" == name) {
        needUpdate = true
      }
      else if (SearchWords.VENDOR.value == name) {
        vendors.add(value)
      }
      else if (SearchWords.TAG.value == name) {
        tags.add(value)
      }
    }
  }

  companion object {
    protected fun splitQuery(query: String): MutableList<String> {
      val words: MutableList<String> = ArrayList()

      val length = query.length
      var index = 0

      while (index < length) {
        val startCh = query[index++]
        if (startCh == ' ') {
          continue
        }
        if (startCh == '"') {
          val end = query.indexOf('"', index)
          if (end == -1) {
            break
          }
          words.add(query.substring(index, end))
          index = end + 1
          continue
        }

        val start = index - 1
        while (index <= length) {
          if (index == length) {
            words.add(query.substring(start))
            break
          }
          val nextCh = query[index++]
          if (nextCh == ':' || nextCh == ' ' || index == length) {
            words.add(query.substring(start, if (nextCh == ' ') index - 1 else index))
            break
          }
        }
      }

      return words
    }

    @JvmStatic
    fun getTagQuery(tag: String): String {
      return "/tag:" + (if (tag.indexOf(' ') == -1) tag else StringUtil.wrapWithDoubleQuote(tag))
    }

    @JvmStatic
    fun wrapAttribute(value: String): String {
      return if (StringUtil.containsAnyChar(value, " ,:")) StringUtil.wrapWithDoubleQuote(value) else value
    }
  }
}