// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.json.psi.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class JsonUtil {
  private JsonUtil() {
    // empty
  }

  /**
   * Clone of C# "as" operator.
   * Checks if expression has correct type and casts it if it has. Returns null otherwise.
   * It saves coder from "instanceof / cast" chains.
   *
   * Copied from PyCharm's {@code PyUtil}.
   *
   * @param expression expression to check
   * @param cls        class to cast
   * @param <T>        class to cast
   * @return expression casted to appropriate type (if could be casted). Null otherwise.
   */
  @SuppressWarnings("unchecked")
  public static @Nullable <T> T as(final @Nullable Object expression, final @NotNull Class<T> cls) {
    if (expression == null) {
      return null;
    }
    if (cls.isAssignableFrom(expression.getClass())) {
      return (T)expression;
    }
    return null;
  }

  public static @Nullable <T extends JsonElement> T getPropertyValueOfType(final @NotNull JsonObject object, final @NotNull String name,
                                                                           final @NotNull Class<T> clazz) {
    final JsonProperty property = object.findProperty(name);
    if (property == null) return null;
    return ObjectUtils.tryCast(property.getValue(), clazz);
  }

  public static boolean isArrayElement(@NotNull PsiElement element) {
    return element instanceof JsonValue && element.getParent() instanceof JsonArray;
  }

  public static int getArrayIndexOfItem(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    if (!(parent instanceof JsonArray)) return -1;
    List<JsonValue> elements = ((JsonArray)parent).getValueList();
    for (int i = 0; i < elements.size(); i++) {
      if (e == elements.get(i)) {
        return i;
      }
    }
    return -1;
  }

  @Contract("null -> null")
  public static @Nullable JsonObject getTopLevelObject(@Nullable JsonFile jsonFile) {
    return jsonFile != null ? ObjectUtils.tryCast(jsonFile.getTopLevelValue(), JsonObject.class) : null;
  }

  public static boolean isJsonFile(@NotNull VirtualFile file, @Nullable Project project) {
    FileType type = file.getFileType();
    if (type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() instanceof JsonLanguage) return true;
    if (project == null || !ScratchUtil.isScratch(file)) return false;
    RootType rootType = ScratchFileService.findRootType(file);
    return rootType != null && rootType.substituteLanguage(project, file) instanceof JsonLanguage;
  }
}
