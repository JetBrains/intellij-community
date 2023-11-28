// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Internal API. Do not use directly. */
@ApiStatus.Internal
public final class InjectedDataKeys {
  private InjectedDataKeys() { }

  private static final String ourInjectedPrefix = "$injected$.";
  private static final Map<String, String> ourInjectableIds = new HashMap<>();

  public static final DataKey<Editor> EDITOR = injectedKey(CommonDataKeys.EDITOR);
  public static final DataKey<Caret> CARET = injectedKey(CommonDataKeys.CARET);
  public static final DataKey<VirtualFile> VIRTUAL_FILE = injectedKey(CommonDataKeys.VIRTUAL_FILE);
  public static final DataKey<PsiFile> PSI_FILE = injectedKey(CommonDataKeys.PSI_FILE);
  public static final DataKey<PsiElement> PSI_ELEMENT = injectedKey(CommonDataKeys.PSI_ELEMENT);
  public static final DataKey<Language> LANGUAGE = injectedKey(CommonDataKeys.LANGUAGE);

  @ApiStatus.Internal
  public static @Nullable String injectedId(@NotNull String dataId) {
    return ourInjectableIds.get(dataId);
  }

  @ApiStatus.Internal
  public static @Nullable String uninjectedId(@NotNull String dataId) {
    return isInjected(dataId) ? normalId(dataId) : null;
  }

  private static @NotNull String normalId(@NotNull String dataId) {
    return dataId.substring(ourInjectedPrefix.length());
  }

  private static boolean isInjected(@NotNull String dataId) {
    return dataId.startsWith(ourInjectedPrefix);
  }

  @ApiStatus.Internal
  public static <T> @NotNull DataKey<T> injectedKey(@NotNull DataKey<T> key) {
    String injectedId = ourInjectedPrefix + key.getName();
    ourInjectableIds.put(key.getName(), injectedId);
    return DataKey.create(injectedId);
  }

  @ApiStatus.Internal
  public static @Nullable Object getInjectedData(@NotNull String dataId, @NotNull DataProvider dataProvider) {
    return getInjectedData(dataId, dataProvider, dataProvider);
  }

  @ApiStatus.Internal
  public static @Nullable Object getInjectedData(
    @NotNull String dataId,
    @NotNull DataProvider normalDataProvider,
    @NotNull DataProvider injectedDataProvider
  ) {
    @Nullable String injectedId;
    @NotNull String normalId;
    if (isInjected(dataId)) {
      normalId = normalId(dataId);
      injectedId = dataId;
    } else {
      normalId = dataId;
      injectedId = injectedId(dataId);
    }
    Object injected = injectedId == null ? null : injectedDataProvider.getData(injectedId);
    return injected == null ? normalDataProvider.getData(normalId) : injected;
  }
}