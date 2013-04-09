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

package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class FileTypeAssocTable<T> {
  private final Map<String, T> myExtensionMappings;
  private final Map<String, T> myExactFileNameMappings;
  private final Map<String, T> myExactFileNameAnyCaseMappings;
  private boolean myHasAnyCaseExactMappings;
  private final List<Pair<FileNameMatcher, T>> myMatchingMappings;

  private FileTypeAssocTable(Map<String, T> extensionMappings, Map<String, T> exactFileNameMappings, Map<String, T> exactFileNameAnyCaseMappings, List<Pair<FileNameMatcher, T>> matchingMappings) {
    myExtensionMappings = new THashMap<String, T>(extensionMappings);
    myExactFileNameMappings = new THashMap<String, T>(exactFileNameMappings);
    myExactFileNameAnyCaseMappings = new THashMap<String, T>(exactFileNameAnyCaseMappings, CaseInsensitiveStringHashingStrategy.INSTANCE) {
      @Override
      public T remove(Object key) {
        T removed = super.remove(key);
        myHasAnyCaseExactMappings = size() > 0;
        return removed;
      }

      @Override
      public T put(String key, T value) {
        T result = super.put(key, value);
        myHasAnyCaseExactMappings = true;
        return result;
      }
    };
    myMatchingMappings = new ArrayList<Pair<FileNameMatcher, T>>(matchingMappings);
  }

  public FileTypeAssocTable() {
    this(Collections.<String, T>emptyMap(), Collections.<String, T>emptyMap(), Collections.<String, T>emptyMap(), Collections.<Pair<FileNameMatcher, T>>emptyList());
  }

  public boolean isAssociatedWith(T type, FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher || matcher instanceof ExactFileNameMatcher) {
      return findAssociatedFileType(matcher) == type;
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst()) && type == mapping.getSecond()) return true;
    }

    return false;
  }

  public void addAssociation(FileNameMatcher matcher, T type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      myExtensionMappings.put(((ExtensionFileNameMatcher)matcher).getExtension(), type);
    }
    else if (matcher instanceof ExactFileNameMatcher) {
      final ExactFileNameMatcher exactFileNameMatcher = (ExactFileNameMatcher)matcher;

      if (exactFileNameMatcher.isIgnoreCase()) {
        myExactFileNameAnyCaseMappings.put(exactFileNameMatcher.getFileName(), type);
      } else {
        myExactFileNameMappings.put(exactFileNameMatcher.getFileName(), type);
      }
    } else {
      myMatchingMappings.add(new Pair<FileNameMatcher, T>(matcher, type));
    }
  }

  public boolean removeAssociation(FileNameMatcher matcher, T type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      String extension = ((ExtensionFileNameMatcher)matcher).getExtension();
      if (myExtensionMappings.get(extension) == type) {
        myExtensionMappings.remove(extension);
        return true;
      }
      return false;
    }

    if (matcher instanceof ExactFileNameMatcher) {
      final ExactFileNameMatcher exactFileNameMatcher = (ExactFileNameMatcher)matcher;
      final Map<String, T> mapToUse;
      String fileName = exactFileNameMatcher.getFileName();

      if (exactFileNameMatcher.isIgnoreCase()) {
        mapToUse = myExactFileNameAnyCaseMappings;
      } else {
        mapToUse = myExactFileNameMappings;
      }
      if(mapToUse.get(fileName) == type) {
        mapToUse.remove(fileName);
        return true;
      }
      return false;
    }

    List<Pair<FileNameMatcher, T>> copy = new ArrayList<Pair<FileNameMatcher, T>>(myMatchingMappings);
    for (Pair<FileNameMatcher, T> assoc : copy) {
      if (matcher.equals(assoc.getFirst())) {
        myMatchingMappings.remove(assoc);
        return true;
      }
    }

    return false;
  }

  public boolean removeAllAssociations(T type) {
    boolean changed = removeAssociationsFromMap(myExtensionMappings, type, false);

    changed = removeAssociationsFromMap(myExactFileNameAnyCaseMappings, type, changed);
    changed = removeAssociationsFromMap(myExactFileNameMappings, type, changed);

    List<Pair<FileNameMatcher, T>> copy = new ArrayList<Pair<FileNameMatcher, T>>(myMatchingMappings);
    for (Pair<FileNameMatcher, T> assoc : copy) {
      if (assoc.getSecond() == type) {
        myMatchingMappings.remove(assoc);
        changed = true;
      }
    }

    return changed;
  }

  private boolean removeAssociationsFromMap(Map<String, T> extensionMappings, T type, boolean changed) {
    Set<String> exts = extensionMappings.keySet();
    String[] extsStrings = ArrayUtil.toStringArray(exts);
    for (String s : extsStrings) {
      if (extensionMappings.get(s) == type) {
        extensionMappings.remove(s);
        changed = true;
      }
    }
    return changed;
  }

  @Nullable
  public T findAssociatedFileType(@NotNull @NonNls String fileName) {
    T t = myExactFileNameMappings.get(fileName);
    if (t != null) return t;

    if (myHasAnyCaseExactMappings) {   // even hash lookup with case insensitive hasher is costly for isIgnored checks during compile
      t = myExactFileNameAnyCaseMappings.get(fileName);
      if (t != null) return t;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = myMatchingMappings.size(); i < n; i++) {
      final Pair<FileNameMatcher, T> mapping = myMatchingMappings.get(i);
      if (mapping.getFirst().accept(fileName)) return mapping.getSecond();
    }

    return myExtensionMappings.get(StringUtil.toLowerCase(FileUtilRt.getExtension(fileName)));
  }

  @Nullable
  public T findAssociatedFileType(final FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      return myExtensionMappings.get(((ExtensionFileNameMatcher)matcher).getExtension());
    }

    if (matcher instanceof ExactFileNameMatcher) {
      final ExactFileNameMatcher exactFileNameMatcher = (ExactFileNameMatcher)matcher;

      if (exactFileNameMatcher.isIgnoreCase()) {
        return myExactFileNameAnyCaseMappings.get(exactFileNameMatcher.getFileName());
      } else {
        return myExactFileNameMappings.get(exactFileNameMatcher.getFileName());
      }
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst())) return mapping.getSecond();
    }

    return null;
  }

  @Deprecated
  @NotNull
  public String[] getAssociatedExtensions(T type) {
    Map<String, T> extMap = myExtensionMappings;

    List<String> exts = new ArrayList<String>();
    for (String ext : extMap.keySet()) {
      if (extMap.get(ext) == type) {
        exts.add(ext);
      }
    }
    return ArrayUtil.toStringArray(exts);
  }

  @NotNull
  public FileTypeAssocTable<T> copy() {
    return new FileTypeAssocTable<T>(myExtensionMappings, myExactFileNameMappings, myExactFileNameAnyCaseMappings, myMatchingMappings);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(final T type) {
    List<FileNameMatcher> result = new ArrayList<FileNameMatcher>();
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getSecond() == type) {
        result.add(mapping.getFirst());
      }
    }

    for (Map.Entry<String, T> entries : myExactFileNameMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExactFileNameMatcher(entries.getKey()));
      }
    }

    for (Map.Entry<String, T> entries : myExactFileNameAnyCaseMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExactFileNameMatcher(entries.getKey(), true));
      }
    }

    for (Map.Entry<String, T> entries : myExtensionMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExtensionFileNameMatcher(entries.getKey()));
      }
    }

    return result;
  }

  public boolean hasAssociationsFor(final T fileType) {
    if (myExtensionMappings.values().contains(fileType)) return true;
    if (myExactFileNameMappings.values().contains(fileType)) return true;
    if (myExactFileNameAnyCaseMappings.values().contains(fileType)) return true;
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getSecond() == fileType) return true;
    }
    return false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileTypeAssocTable that = (FileTypeAssocTable)o;

    if (!myExtensionMappings.equals(that.myExtensionMappings)) return false;
    if (!myMatchingMappings.equals(that.myMatchingMappings)) return false;
    if (!myExactFileNameMappings.equals(that.myExactFileNameMappings)) return false;
    if (!myExactFileNameAnyCaseMappings.equals(that.myExactFileNameAnyCaseMappings)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myExtensionMappings.hashCode();
    result = 31 * result + myMatchingMappings.hashCode();
    result = 31 * result + myExactFileNameMappings.hashCode();
    result = 31 * result + myExactFileNameAnyCaseMappings.hashCode();
    return result;
  }
}
