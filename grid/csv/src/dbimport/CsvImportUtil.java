package com.intellij.database.dbimport;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CsvImportUtil {
  public static @NotNull TypeMerger getPreferredTypeMergerBasedOnContent(@NotNull Iterable<@Nullable String> values, @NotNull TypeMerger stringMerger, TypeMerger @NotNull ... mergers) {
    TypeMerger merger = null;
    for (String value : values) {
      if (value == null) continue;
      TypeMerger nextMerger = ObjectUtils.notNull(getType(value, mergers), stringMerger);
      merger = merger == null ? nextMerger : merger.merge(nextMerger);
    }
    return merger == null ? stringMerger : merger;
  }

  public static @Nullable TypeMerger getType(String string, TypeMerger @NotNull [] mergers) {
    for (TypeMerger merger : mergers) {
      if (merger.isSuitable(string)) return merger;
    }
    return null;
  }
}
