// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public abstract class SearchQueryParser {
  public String searchQuery;

  protected void addToSearchQuery(@NotNull String query) {
    if (searchQuery == null) {
      searchQuery = query;
    }
    else {
      searchQuery += " " + query;
    }
  }

  protected static @NotNull List<String> splitQuery(@NotNull String query) {
    List<String> words = new ArrayList<>();

    int length = query.length();
    int index = 0;

    while (index < length) {
      char startCh = query.charAt(index++);
      if (startCh == ' ') {
        continue;
      }
      if (startCh == '"') {
        int end = query.indexOf('"', index);
        if (end == -1) {
          break;
        }
        words.add(query.substring(index, end));
        index = end + 1;
        continue;
      }

      int start = index - 1;
      while (index <= length) {
        if (index == length) {
          words.add(query.substring(start));
          break;
        }
        char nextCh = query.charAt(index++);
        if (nextCh == ':' || nextCh == ' ' || index == length) {
          words.add(query.substring(start, nextCh == ' ' ? index - 1 : index));
          break;
        }
      }
    }

    return words;
  }

  public static @NotNull String getTagQuery(@NotNull String tag) {
    return "/tag:" + (tag.indexOf(' ') == -1 ? tag : StringUtil.wrapWithDoubleQuote(tag));
  }

  public static @NotNull String wrapAttribute(@NotNull String value) {
    return StringUtil.containsAnyChar(value, " ,:") ? StringUtil.wrapWithDoubleQuote(value) : value;
  }

  public static class Marketplace extends SearchQueryParser {
    public final Set<String> vendors = new HashSet<>();
    public final Set<String> tags = new HashSet<>();
    public final Set<String> repositories = new HashSet<>();
    public String sortBy;
    public boolean suggested;

    public Marketplace(@NotNull String query) {
      parse(query);
    }

    private void parse(@NotNull String query) {
      List<String> words = splitQuery(query);
      int size = words.size();

      if (size == 0) {
        return;
      }
      if (size == 1) {
        addToSearchQuery(words.get(0));
        return;
      }

      int index = 0;
      while (index < size) {
        String name = words.get(index++);
        if (name.endsWith(":")) {
          if (index < size) {
            handleAttribute(name, words.get(index++));
          }
          else {
            addToSearchQuery(query);
            return;
          }
        }
        else {
          addToSearchQuery(name);
        }
      }
    }

    @Override
    protected void addToSearchQuery(@NotNull String query) {
      if (query.equals(SearchWords.SUGGESTED.getValue())) {
        suggested = true;
      }
      else {
        super.addToSearchQuery(query);
      }
    }

    protected void handleAttribute(@NotNull String name, @NotNull String value) {
      if (name.equals(SearchWords.TAG.getValue())) {
        tags.add(value);
      }
      else if (name.equals(SearchWords.SORT_BY.getValue())) {
        sortBy = value;
      }
      else if (name.equals(SearchWords.REPOSITORY.getValue())) {
        repositories.add(value);
      }
      else if (name.equals(SearchWords.ORGANIZATION.getValue())) {
        vendors.add(value);
      }
    }

    public @NotNull String getUrlQuery() {
      StringBuilder url = new StringBuilder();

      if ("featured".equals(sortBy)) {
        url.append("is_featured_search=true");
      }
      else if ("updated".equals(sortBy)) {
        url.append("orderBy=update+date");
      }
      else if ("downloads".equals(sortBy)) {
        url.append("orderBy=downloads");
      }
      else if ("rating".equals(sortBy)) {
        url.append("orderBy=rating");
      }
      else if ("name".equals(sortBy)) {
        url.append("orderBy=name");
      }

      for (String tag : tags) {
        if (url.length() > 0) {
          url.append("&");
        }
        url.append("tags=").append(URLUtil.encodeURIComponent(tag));
      }

      for (String vendor : vendors) {
        if (url.length() > 0) {
          url.append("&");
        }
        url.append("organization=").append(URLUtil.encodeURIComponent(vendor));
      }

      if (searchQuery != null) {
        if (url.length() > 0) {
          url.append("&");
        }
        url.append("search=").append(URLUtil.encodeURIComponent(searchQuery));
      }

      return url.toString();
    }
  }

  public static class Installed extends SearchQueryParser {
    public final Set<String> vendors = new HashSet<>();
    public final Set<String> tags = new HashSet<>();
    public boolean enabled;
    public boolean disabled;
    public boolean bundled;
    public boolean downloaded;
    public boolean invalid;
    public boolean needUpdate;
    public boolean attributes;

    public Installed(@NotNull String query) {
      parse(query);
    }

    private void parse(@NotNull String query) {
      List<String> words = splitQuery(query);
      int size = words.size();

      if (size == 0) {
        return;
      }

      int index = 0;
      while (index < size) {
        String name = words.get(index++);
        if (name.startsWith("/")) {
          if (name.equals(SearchWords.ORGANIZATION.getValue()) || name.equals(SearchWords.TAG.getValue())) {
            if (index < size) {
              handleAttribute(name, words.get(index++));
            }
            else {
              addToSearchQuery(query);
              break;
            }
          }
          else {
            handleAttribute(name, "");
          }
        }
        else {
          addToSearchQuery(name);
        }
      }

      attributes = enabled || disabled || bundled || downloaded || invalid || needUpdate;
    }

    protected void handleAttribute(@NotNull String name, @NotNull String value) {
      if ("/enabled".equals(name)) {
        enabled = true;
      }
      else if ("/disabled".equals(name)) {
        disabled = true;
      }
      else if ("/bundled".equals(name)) {
        bundled = true;
      }
      else if ("/downloaded".equals(name)) {
        downloaded = true;
      }
      else if ("/invalid".equals(name)) {
        invalid = true;
      }
      else if ("/outdated".equals(name)) {
        needUpdate = true;
      }
      else if (SearchWords.ORGANIZATION.getValue().equals(name)) {
        vendors.add(value);
      }
      else if (SearchWords.TAG.getValue().equals(name)) {
        tags.add(value);
      }
    }
  }
}