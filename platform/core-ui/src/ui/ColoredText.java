// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.ColoredText.Builder;
import com.intellij.ui.ColoredText.Fragment;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.jetbrains.annotations.ApiStatus.NonExtendable;

@NonExtendable
public interface ColoredText {

  @NotNull List<? extends @NotNull Fragment> fragments();

  @NonExtendable
  interface Fragment {

    @Nls @NotNull String fragmentText();

    @NotNull SimpleTextAttributes fragmentAttributes();
  }

  @Contract(pure = true)
  static @NotNull ColoredText empty() {
    return ColoredTextImpl.EMPTY;
  }

  @Contract(value = "_ -> new", pure = true)
  static @NotNull ColoredText singleFragment(@Nls @NotNull String fragmentText) {
    return singleFragment(fragmentText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Contract(value = "_, _ -> new", pure = true)
  static @NotNull ColoredText singleFragment(@Nls @NotNull String fragmentText, @NotNull SimpleTextAttributes attributes) {
    return new ColoredTextImpl(new ColoredTextFragmentImpl(fragmentText, attributes));
  }

  @Contract(value = "-> new", pure = true)
  static @NotNull Builder builder() {
    return new ColoredTextBuilderImpl();
  }

  @NonExtendable
  interface Builder {

    @Contract("_ -> this")
    default @NotNull Builder append(@Nls @NotNull String fragmentText) {
      return append(fragmentText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    @Contract("_, _ -> this")
    @NotNull Builder append(@Nls @NotNull String fragmentText, @NotNull SimpleTextAttributes attributes);

    @Contract(value = "-> new", pure = true)
    @NotNull ColoredText build();
  }
}

final class ColoredTextFragmentImpl implements Fragment {

  private final @Nls @NotNull String myFragmentText;
  private final @NotNull SimpleTextAttributes myAttributes;

  @Contract(pure = true)
  ColoredTextFragmentImpl(@Nls @NotNull String fragmentText, @NotNull SimpleTextAttributes attributes) {
    myFragmentText = fragmentText;
    myAttributes = attributes;
  }

  @Override
  public @Nls @NotNull String fragmentText() {
    return myFragmentText;
  }

  @Override
  public @NotNull SimpleTextAttributes fragmentAttributes() {
    return myAttributes;
  }

  @Override
  public String toString() {
    return myFragmentText;
  }
}

final class ColoredTextImpl implements ColoredText {

  static final ColoredText EMPTY = new ColoredTextImpl(Collections.emptyList());

  private final @NotNull List<? extends @NotNull Fragment> myFragments;

  @Contract(pure = true)
  ColoredTextImpl(@NotNull List<? extends @NotNull Fragment> fragments) {
    myFragments = List.copyOf(fragments);
  }

  @Contract(pure = true)
  ColoredTextImpl(@NotNull Fragment fragment) {
    myFragments = Collections.singletonList(fragment);
  }

  @Override
  public @NotNull List<? extends @NotNull Fragment> fragments() {
    return myFragments;
  }

  @Override
  public String toString() {
    return myFragments.stream().map(Fragment::fragmentText).collect(Collectors.joining());
  }
}

final class ColoredTextBuilderImpl implements Builder {

  private final @NotNull List<@NotNull Fragment> myFragments = new ArrayList<>(2);

  private final @Nls @NotNull StringBuilder myLastFragmentTextBuilder = new StringBuilder();
  private @Nullable SimpleTextAttributes myLastFragmentAttributes = null;

  @Override
  public @NotNull Builder append(@Nls @NotNull String fragmentText, @NotNull SimpleTextAttributes attributes) {
    if (!fragmentText.isEmpty()) {
      if (myLastFragmentAttributes == null && !myFragments.isEmpty()) {
        int size = myFragments.size();
        Fragment lastFragment = myFragments.get(size - 1);
        if (attributes.equals(lastFragment.fragmentAttributes())) {
          myLastFragmentTextBuilder.append(lastFragment.fragmentText());
          myFragments.remove(size - 1);
        }
      }
      if (!attributes.equals(myLastFragmentAttributes)) {
        flushLastFragment();
      }
      myLastFragmentAttributes = attributes;
      myLastFragmentTextBuilder.append(fragmentText);
    }
    return this;
  }

  private void flushLastFragment() {
    if (myLastFragmentAttributes != null) {
      myFragments.add(new ColoredTextFragmentImpl(myLastFragmentTextBuilder.toString(), myLastFragmentAttributes));
      myLastFragmentTextBuilder.setLength(0);
      myLastFragmentAttributes = null;
    }
  }

  @Override
  public @NotNull ColoredText build() {
    flushLastFragment();
    int size = myFragments.size();
    if (size == 0) {
      return ColoredTextImpl.EMPTY;
    }
    else if (size == 1) {
      return new ColoredTextImpl(myFragments.get(0));
    }
    else {
      return new ColoredTextImpl(myFragments);
    }
  }
}
