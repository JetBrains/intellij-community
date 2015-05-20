/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileNameMatcherEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class FileTypeAssocTable<T> {
  private final Map<CharSequence, T> myExtensionMappings;
  private final Map<CharSequence, T> myExactFileNameMappings;
  private final Map<CharSequence, T> myExactFileNameAnyCaseMappings;
  private boolean myHasAnyCaseExactMappings;
  private final List<Pair<FileNameMatcher, T>> myMatchingMappings;

  private FileTypeAssocTable(Map<CharSequence, T> extensionMappings, Map<CharSequence, T> exactFileNameMappings, Map<CharSequence, T> exactFileNameAnyCaseMappings, List<Pair<FileNameMatcher, T>> matchingMappings) {
    myExtensionMappings = new THashMap<CharSequence, T>(extensionMappings, CharSequenceHashingStrategy.CASE_INSENSITIVE);
    
    myExactFileNameMappings = new THashMap<CharSequence, T>(exactFileNameMappings, CharSequenceHashingStrategy.CASE_SENSITIVE);
    
    myExactFileNameAnyCaseMappings = new THashMap<CharSequence, T>(exactFileNameAnyCaseMappings, CharSequenceHashingStrategy.CASE_INSENSITIVE) {
      @Override
      public T remove(Object key) {
        T removed = super.remove(key);
        myHasAnyCaseExactMappings = size() > 0;
        return removed;
      }

      @Override
      public T put(CharSequence key, T value) {
        T result = super.put(key, value);
        myHasAnyCaseExactMappings = true;
        return result;
      }
    };
    myMatchingMappings = new ArrayList<Pair<FileNameMatcher, T>>(matchingMappings);
  }

  public FileTypeAssocTable() {
    this(Collections.<CharSequence, T>emptyMap(), Collections.<CharSequence, T>emptyMap(), Collections.<CharSequence, T>emptyMap(), Collections.<Pair<FileNameMatcher, T>>emptyList());
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
      myMatchingMappings.add(Pair.create(matcher, type));
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
      final Map<CharSequence, T> mapToUse;
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

  private boolean removeAssociationsFromMap(Map<CharSequence, T> extensionMappings, T type, boolean changed) {
    Set<CharSequence> exts = extensionMappings.keySet();
    CharSequence[] extsStrings = exts.toArray(new CharSequence[exts.size()]);
    for (CharSequence s : extsStrings) {
      if (extensionMappings.get(s) == type) {
        extensionMappings.remove(s);
        changed = true;
      }
    }
    return changed;
  }

  @Nullable
  public T findAssociatedFileType(@NotNull @NonNls CharSequence fileName) {
    T t = myExactFileNameMappings.get(fileName);
    if (t != null) return t;

    if (myHasAnyCaseExactMappings) {   // even hash lookup with case insensitive hasher is costly for isIgnored checks during compile
      t = myExactFileNameAnyCaseMappings.get(fileName);
      if (t != null) return t;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = myMatchingMappings.size(); i < n; i++) {
      final Pair<FileNameMatcher, T> mapping = myMatchingMappings.get(i);
      if (FileNameMatcherEx.acceptsCharSequence(mapping.getFirst(), fileName)) return mapping.getSecond();
    }

    return myExtensionMappings.get(FileUtilRt.getExtension(fileName));
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
    Map<CharSequence, T> extMap = myExtensionMappings;

    List<String> exts = new ArrayList<String>();
    for (CharSequence ext : extMap.keySet()) {
      if (extMap.get(ext) == type) {
        exts.add(ext.toString());
      }
    }
    return ArrayUtil.toStringArray(exts);
  }

  @NotNull
  public FileTypeAssocTable<T> copy() {
    return new FileTypeAssocTable<T>(myExtensionMappings, myExactFileNameMappings, myExactFileNameAnyCaseMappings, myMatchingMappings);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull T type) {
    List<FileNameMatcher> result = new ArrayList<FileNameMatcher>();
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getSecond() == type) {
        result.add(mapping.getFirst());
      }
    }

    for (Map.Entry<CharSequence, T> entries : myExactFileNameMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExactFileNameMatcher(entries.getKey().toString()));
      }
    }

    for (Map.Entry<CharSequence, T> entries : myExactFileNameAnyCaseMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExactFileNameMatcher(entries.getKey().toString(), true));
      }
    }

    for (Map.Entry<CharSequence, T> entries : myExtensionMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExtensionFileNameMatcher(entries.getKey().toString()));
      }
    }

    return result;
  }

  public boolean hasAssociationsFor(@NotNull T fileType) {
    if (myExtensionMappings.values().contains(fileType) ||
        myExactFileNameMappings.values().contains(fileType) ||
        myExactFileNameAnyCaseMappings.values().contains(fileType)) {
      return true;
    }
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getSecond() == fileType) {
        return true;
      }
    }
    return false;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FileTypeAssocTable<?> that = (FileTypeAssocTable)o;
    return myExtensionMappings.equals(that.myExtensionMappings) &&
           myMatchingMappings.equals(that.myMatchingMappings) &&
           myExactFileNameMappings.equals(that.myExactFileNameMappings) &&
           myExactFileNameAnyCaseMappings.equals(that.myExactFileNameAnyCaseMappings);
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
