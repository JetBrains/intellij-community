// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public abstract class SearchQueryParser {
  public String searchQuery;

  @NotNull
  public static List<String> split(@NotNull String name, @Nullable String query) {
    List<String> result = new ArrayList<>();

    if (query == null) {
      result.add(name);
      return result;
    }

    int length = name.length();
    int queryLength = query.length();
    int index = 0;

    while (true) {
      int end = StringUtil.indexOfIgnoreCase(name, query, index);
      if (end == -1) {
        break;
      }
      result.add(name.substring(index, end));
      index = end + queryLength;
      result.add(name.substring(end, index));
    }

    if (index < length) {
      result.add(name.substring(index));
    }

    return result;
  }

  @NotNull
  protected static List<String> splitQuery(@NotNull String query) {
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
      while (index < length) {
        char nextCh = query.charAt(index++);
        if (nextCh == ':' || nextCh == ' ' || index == length) {
          words.add(query.substring(start, nextCh == ' ' ? index - 1 : index));
          break;
        }
      }
    }

    if (words.isEmpty() && length > 0) {
      words.add(query);
    }

    return words;
  }

  protected final void parse(@NotNull String query) {
    List<String> words = splitQuery(query);
    int size = words.size();

    if (size == 0) {
      return;
    }
    if (size == 1) {
      searchQuery = words.get(0);
      return;
    }

    int index = 0;
    while (index < size) {
      String name = words.get(index++);
      if (name.endsWith(":")) {
        if (index < size) {
          boolean invert = name.startsWith("-");
          name = name.substring(invert ? 1 : 0, name.length() - 1);
          handleAttribute(name, words.get(index++), invert);
        }
        else {
          searchQuery = query;
          return;
        }
      }
      else if (searchQuery == null) {
        searchQuery = name;
      }
      else {
        searchQuery = query;
        return;
      }
    }
  }

  protected abstract void handleAttribute(@NotNull String name, @NotNull String value, boolean invert);

  @NotNull
  public static String getTagQuery(@NotNull String tag) {
    return "tag:" + (tag.indexOf(' ') == -1 ? tag : StringUtil.wrapWithDoubleQuote(tag));
  }

  public static class Trending extends SearchQueryParser {
    public final Set<String> tags = new HashSet<>();
    public final Set<String> repositories = new HashSet<>();
    public String sortBy;

    public Trending(@NotNull String query) {
      parse(query);
    }

    @NotNull
    public String getUrlQuery() {
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

      if (searchQuery != null) {
        if (url.length() > 0) {
          url.append("&");
        }
        url.append("search=").append(URLUtil.encodeURIComponent(searchQuery));
      }

      return url.toString();
    }

    @Override
    protected void handleAttribute(@NotNull String name, @NotNull String value, boolean invert) {
      if (name.equals("tag")) {
        tags.add(value);
      }
      else if (name.equals("sortBy")) {
        sortBy = value;
      }
      else if (name.equals("repository")) {
        repositories.add(value);
      }
    }
  }

  public static class Installed extends SearchQueryParser {
    public Boolean enabled; // False == disabled
    public Boolean bundled; // False == custom
    public Boolean invalid;
    public Boolean needUpdate;
    public Boolean deleted;
    public Boolean needRestart; // inactive & after update
    public final boolean attributes;

    public Installed(@NotNull String query) {
      for (String word : splitQuery(query)) {
        if (word.startsWith("#")) {
          handleAttribute(word.substring(1), "", false);
        }
        else if (word.startsWith("-#")) {
          handleAttribute(word.substring(2), "", true);
        }
        else if (searchQuery == null) {
          searchQuery = word;
        }
        else {
          searchQuery = query;
          break;
        }
      }

      attributes = enabled != null || bundled != null || invalid != null || needUpdate != null || deleted != null || needRestart != null;
    }

    @Override
    protected void handleAttribute(@NotNull String name, @NotNull String value, boolean invert) {
      switch (name) {
        case "enabled":
          enabled = !invert;
          break;
        case "disabled":
          enabled = invert;
          break;

        case "bundled":
          bundled = !invert;
          break;
        case "custom":
          bundled = invert;
          break;

        case "invalid":
          invalid = !invert;
          break;

        case "outdated":
          needUpdate = !invert;
          break;

        case "uninstalled":
          deleted = !invert;
          break;

        case "inactive":
          needRestart = !invert;
          break;
      }
    }
  }
}