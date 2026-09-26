// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class JvmIdentifierUtil {
  private JvmIdentifierUtil() { }

  public interface IdConsumer {
    /**
     * Handles valid JVM identifier extracted from the string
     * Warning: Implementation must be ready for rollback - several valid identifiers may be fed to {@code accept} before checking that there
     * is an invalid one present in this string and all previous ones should be rolled back.
     * @param sequence original sequence, from which the identifiers are extracted. Args correspond to offsets in the sequence.
     */
    void accept(@NotNull CharSequence sequence, int startIncl, int endExcl);
  }

  /**
   * Collects JVM identifiers from a given string and passes them to the provided IdConsumer.
   *
   * @param str The string to collect JVM identifiers from.
   * @param partsConsumer The IdConsumer that will receive the collected JVM identifiers.
   * @return true if JVM identifiers are collected successfully, false otherwise.
   */
  @Contract(mutates = "param2")
  public static boolean collectJvmIdentifiers(@NotNull String str, @NotNull IdConsumer partsConsumer) {
    if (str.isEmpty()) return false;

    if (str.charAt(str.length() - 1) == ';' || str.charAt(0) == '(') {
      // probably field descriptor, e.g. [[Ljava/lang/String; or [B or S
      // also it may be Signature, e.g.
      // Ljava/util/Map<[Ljava/lang/String;Ljava/util/List<Ljava/util/List<+Ljava/util/Comparator<Ljava/lang/String;>;>;>;>;
      // It doesn't make sense to take the strings out of it recursively, so instead parts between 'L' and (';' or '<') are searched
      int current = 0;
      int start;
      outer:
      while (current < str.length()) {
        char firstChar = str.charAt(current);
        if (firstChar == 'L') {
          current++;
          start = current;
          while (current < str.length()) {
            char ch = str.charAt(current);
            current++;
            if (ch == ';' || ch == '<') {
              if (!collectSlashSeparated(str, start, current - 1, partsConsumer)) {
                return false;
              }
              continue outer;
            }
          }
        }
        current++;
      }
      if (current == str.length()) {
        return true;
      }
    }

    return collectSlashSeparated(str, 0, str.length(), partsConsumer);
  }

  private static int indexOfSlash(String str, int startIndexIncl, int endIndexExcl) {
    int current = startIndexIncl;
    while (current < endIndexExcl) {
      char ch = str.charAt(current);
      if (ch == '/') {
        return current;
      }

      current++;
    }
    return -1;
  }

  private static boolean collectSlashSeparated(@NotNull String str, int startIncl, int endExcl, @NotNull IdConsumer partsConsumer) {
    int partStartIncl = startIncl;
    while (partStartIncl < endExcl) {
      int indexOfSlash = indexOfSlash(str, partStartIncl, endExcl);
      if (indexOfSlash == -1) {
        break;
      }
      if (partStartIncl == indexOfSlash) {
        return false;
      }
      if (!StringUtil.isJavaIdentifier(str, partStartIncl, indexOfSlash)) {
        return false;
      }
      partsConsumer.accept(str, partStartIncl, indexOfSlash);
      partStartIncl = indexOfSlash + 1;
      if (partStartIncl == endExcl) { // '/' is at the end
        return false;
      }
    }
    // handling rest
    if (partStartIncl == 0) {
      if (!StringUtil.isJavaIdentifier(str, 0, str.length())) {
        return false;
      }
      partsConsumer.accept(str, partStartIncl, endExcl);
    } else {
      if (!StringUtil.isJavaIdentifier(str, partStartIncl, endExcl)) {
        return false;
      }
      partsConsumer.accept(str, partStartIncl, endExcl);
    }
    return true;
  }

}
