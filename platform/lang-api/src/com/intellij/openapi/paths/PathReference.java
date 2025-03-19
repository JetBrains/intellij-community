// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.paths;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.NullableConstantFunction;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class PathReference {
  public static final NullableFunction<PathReference, Icon> NULL_ICON = new NullableConstantFunction<>(null);

  private final String myPath;
  private final NullableLazyValue<Icon> myIcon;

  public PathReference(@NotNull String path, final @NotNull Function<? super PathReference, ? extends Icon> icon) {
    myPath = path;
    myIcon = new NullableLazyValue<>() {
      @Override
      protected Icon compute() {
        return icon.fun(PathReference.this);
      }
    };
  }

  public @NotNull String getPath() {
    return myPath;
  }

  public @NotNull String getTrimmedPath() {
    return trimPath(myPath);
  }

  public @Nullable Icon getIcon() {
    return myIcon.getValue();
  }

  public @Nullable PsiElement resolve() {
    return null;
  }

  public static String trimPath(final String url) {
    for (int i = 0; i < url.length(); i++) {
      switch (url.charAt(i)) {
        case '?', '#' -> {
          return url.substring(0, i);
        }
      }
    }
    return url;
  }

  public static class ResolveFunction implements NullableFunction<PathReference, Icon> {
    public static final ResolveFunction NULL_RESOLVE_FUNCTION = new ResolveFunction(null);
    private final Icon myDefaultIcon;

    public ResolveFunction(final @Nullable Icon defaultValue) {
      myDefaultIcon = defaultValue;
    }

    @Override
    public Icon fun(final PathReference pathReference) {
      final PsiElement element = pathReference.resolve();
      return element == null ? myDefaultIcon : element.getIcon(Iconable.ICON_FLAG_READ_STATUS);
    }
  }
}
