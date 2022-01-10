// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class FileTypeAssocTable<T> {
  private final Map<CharSequence, T> myExtensionMappings;
  private final Map<CharSequence, T> myExactFileNameMappings;
  private final Map<CharSequence, T> myExactFileNameAnyCaseMappings;
  private final List<Pair<FileNameMatcher, T>> myMatchingMappings;
  private final Map<String, T> myHashBangMap;

  private FileTypeAssocTable(@NotNull Map<? extends CharSequence, ? extends T> extensionMappings,
                             @NotNull Map<? extends CharSequence, ? extends T> exactFileNameMappings,
                             @NotNull Map<? extends CharSequence, ? extends T> exactFileNameAnyCaseMappings,
                             @NotNull Map<String, ? extends T> hashBangMap,
                             @NotNull List<? extends Pair<FileNameMatcher, T>> matchingMappings) {
    myExtensionMappings = createCharSequenceConcurrentMap(extensionMappings);
    myExactFileNameMappings = new ConcurrentHashMap<>(exactFileNameMappings);
    myExactFileNameAnyCaseMappings = createCharSequenceConcurrentMap(exactFileNameAnyCaseMappings);
    myHashBangMap = new ConcurrentHashMap<>(hashBangMap);
    myMatchingMappings = new CopyOnWriteArrayList<>(matchingMappings);
  }

  public FileTypeAssocTable() {
    this(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
  }

  boolean isAssociatedWith(@NotNull T type, @NotNull FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher || matcher instanceof ExactFileNameMatcher) {
      return type.equals(findAssociatedFileType(matcher));
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst()) && type.equals(mapping.getSecond())) return true;
    }

    return false;
  }

  /**
   * @return old association
   */
  public T addAssociation(@NotNull FileNameMatcher matcher, @NotNull T type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      String extension = ((ExtensionFileNameMatcher)matcher).getExtension();
      return myExtensionMappings.put(extension, type);
    }
    if (matcher instanceof ExactFileNameMatcher) {
      ExactFileNameMatcher exactFileNameMatcher = (ExactFileNameMatcher)matcher;

      Map<CharSequence, T> mapToUse = exactFileNameMatcher.isIgnoreCase() ? myExactFileNameAnyCaseMappings : myExactFileNameMappings;
      return mapToUse.put(exactFileNameMatcher.getFileName(), type);
    }
    int i = ContainerUtil.indexOf(myMatchingMappings, p -> p.first.equals(matcher));
    if (i == -1) {
      myMatchingMappings.add(Pair.create(matcher, type));
      return null;
    }
    Pair<FileNameMatcher, T> old = myMatchingMappings.get(i);
    myMatchingMappings.set(i, Pair.create(matcher, type));
    return Pair.getSecond(old);
  }

  void addHashBangPattern(@NotNull String hashBang, @NotNull T type) {
    myHashBangMap.put(hashBang, type);
  }
  void removeHashBangPattern(@NotNull String hashBang, @NotNull T type) {
    myHashBangMap.remove(hashBang, type);
  }

  void removeAssociation(@NotNull FileNameMatcher matcher, @NotNull T type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      String extension = ((ExtensionFileNameMatcher)matcher).getExtension();
      if (type.equals(myExtensionMappings.get(extension))) {
        myExtensionMappings.remove(extension);
      }
      return;
    }

    if (matcher instanceof ExactFileNameMatcher) {
      final ExactFileNameMatcher exactFileNameMatcher = (ExactFileNameMatcher)matcher;
      String fileName = exactFileNameMatcher.getFileName();

      final Map<CharSequence, T> mapToUse = exactFileNameMatcher.isIgnoreCase() ? myExactFileNameAnyCaseMappings : myExactFileNameMappings;
      if (type.equals(mapToUse.get(fileName))) {
        mapToUse.remove(fileName);
      }
      return;
    }
    myMatchingMappings.removeIf(assoc -> matcher.equals(assoc.getFirst()));
  }

  void removeAllAssociations(@NotNull T type) {
    removeAllAssociations(bean -> bean.equals(type));
  }

  @Nullable
  public T findAssociatedFileType(@NotNull @NonNls CharSequence fileName) {
    if (!myExactFileNameMappings.isEmpty()) {
      T t = myExactFileNameMappings.get(fileName);
      if (t != null) return t;
    }

    if (!myExactFileNameAnyCaseMappings.isEmpty()) {   // even hash lookup with case-insensitive hasher is costly for isIgnored checks during compile
      T t = myExactFileNameAnyCaseMappings.get(fileName);
      if (t != null) return t;
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getFirst().acceptsCharSequence(fileName)) return mapping.getSecond();
    }

    return findByExtension(FileUtilRt.getExtension(fileName));
  }

  @Nullable
  T findAssociatedFileTypeByHashBang(@NotNull CharSequence content) {
    for (Map.Entry<String, T> entry : myHashBangMap.entrySet()) {
      String hashBang = entry.getKey();
      if (FileUtil.isHashBangLine(content, hashBang)) return entry.getValue();
    }
    return null;
  }

  @Nullable
  T findAssociatedFileType(@NotNull FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      return findByExtension(((ExtensionFileNameMatcher)matcher).getExtension());
    }

    if (matcher instanceof ExactFileNameMatcher) {
      final ExactFileNameMatcher exactFileNameMatcher = (ExactFileNameMatcher)matcher;

      Map<CharSequence, T> mapToUse = exactFileNameMatcher.isIgnoreCase() ? myExactFileNameAnyCaseMappings : myExactFileNameMappings;
      return mapToUse.get(exactFileNameMatcher.getFileName());
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst())) return mapping.getSecond();
    }

    return null;
  }

  T findByExtension(@NotNull CharSequence extension) {
    return myExtensionMappings.get(extension);
  }

  String @NotNull [] getAssociatedExtensions(@NotNull T type) {
    List<String> extensions = new ArrayList<>();
    for (Map.Entry<CharSequence, T> entry : myExtensionMappings.entrySet()) {
      if (type.equals(entry.getValue())) {
        extensions.add(entry.getKey().toString());
      }
    }
    return ArrayUtilRt.toStringArray(extensions);
  }

  @NotNull
  public FileTypeAssocTable<T> copy() {
    return new FileTypeAssocTable<>(myExtensionMappings, myExactFileNameMappings, myExactFileNameAnyCaseMappings, myHashBangMap, myMatchingMappings);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull T type) {
    List<FileNameMatcher> result = new ArrayList<>();
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (type.equals(mapping.getSecond())) {
        result.add(mapping.getFirst());
      }
    }

    for (Map.Entry<CharSequence, T> entry : myExactFileNameMappings.entrySet()) {
      if (type.equals(entry.getValue())) {
        result.add(new ExactFileNameMatcher(entry.getKey().toString(), false));
      }
    }
    for (Map.Entry<CharSequence, T> entry : myExactFileNameAnyCaseMappings.entrySet()) {
      if (type.equals(entry.getValue())) {
        result.add(new ExactFileNameMatcher(entry.getKey().toString(), true));
      }
    }
    for (Map.Entry<CharSequence, T> entry : myExtensionMappings.entrySet()) {
      if (type.equals(entry.getValue())) {
        result.add(new ExtensionFileNameMatcher(entry.getKey().toString()));
      }
    }

    return result;
  }

  @NotNull
  public List<String> getHashBangPatterns(@NotNull T type) {
    return myHashBangMap.entrySet().stream()
      .filter(e -> e.getValue().equals(type))
      .map(e -> e.getKey())
      .collect(Collectors.toList());
  }

  boolean hasAssociationsFor(@NotNull T fileType) {
    if (myExtensionMappings.containsValue(fileType) ||
        myExactFileNameMappings.containsValue(fileType) ||
        myHashBangMap.containsValue(fileType) ||
        myExactFileNameAnyCaseMappings.containsValue(fileType)) {
      return true;
    }
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (fileType.equals(mapping.getSecond())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  Map<FileNameMatcher, T> getRemovedMappings(@NotNull FileTypeAssocTable<T> newTable, @NotNull Collection<? extends T> keys) {
    Map<FileNameMatcher, T> map = new HashMap<>();
    for (T key : keys) {
      List<FileNameMatcher> associations = getAssociations(key);
      associations.removeAll(newTable.getAssociations(key));
      for (FileNameMatcher matcher : associations) {
        map.put(matcher, key);
      }
    }
    return map;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FileTypeAssocTable<?> that = (FileTypeAssocTable<?>)o;
    return myExtensionMappings.equals(that.myExtensionMappings) &&
           myMatchingMappings.equals(that.myMatchingMappings) &&
           myExactFileNameMappings.equals(that.myExactFileNameMappings) &&
           myHashBangMap.equals(that.myHashBangMap) &&
           myExactFileNameAnyCaseMappings.equals(that.myExactFileNameAnyCaseMappings);
  }

  @Override
  public int hashCode() {
    int result = myExtensionMappings.hashCode();
    result = 31 * result + myMatchingMappings.hashCode();
    result = 31 * result + myHashBangMap.hashCode();
    result = 31 * result + myExactFileNameMappings.hashCode();
    result = 31 * result + myExactFileNameAnyCaseMappings.hashCode();
    return result;
  }

  @NotNull
  Map<String, T> getInternalRawHashBangPatterns() {
    return CollectionFactory.createSmallMemoryFootprintMap(myHashBangMap);
  }

  private static @NotNull <T> Map<CharSequence, T> createCharSequenceConcurrentMap(@NotNull Map<? extends CharSequence, ? extends T> source) {
    // todo convert to ConcurrentCollectionFactory when it's available in the classpath
    Map<CharSequence, T> map = CollectionFactory.createCharSequenceMap(false, source.size(), 0.5f);
    map.putAll(source);
    return Collections.synchronizedMap(map);
  }

  void removeAllAssociations(@NotNull Predicate<? super T> predicate) {
    myExtensionMappings.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    myExactFileNameMappings.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    myExactFileNameAnyCaseMappings.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    myMatchingMappings.removeIf(entry -> predicate.test(entry.getSecond()));
    myHashBangMap.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
  }

  @Override
  public String toString() {
    return "FileTypeAssocTable. myExtensionMappings="+myExtensionMappings+";\n"
      +"myExactFileNameMappings="+myExactFileNameMappings+";\n"
      +"myExactFileNameAnyCaseMappings="+myExactFileNameAnyCaseMappings+";\n"
      +"myMatchingMappings="+myMatchingMappings+";\n"
      +"myHashBangMap="+myHashBangMap+";";
  }

  @TestOnly
  public void clear() {
    myHashBangMap.clear();
    myMatchingMappings.clear();
    myExtensionMappings.clear();
    myExactFileNameMappings.clear();
    myExactFileNameAnyCaseMappings.clear();
  }
}
