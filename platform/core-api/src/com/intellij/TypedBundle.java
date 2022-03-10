// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * Allows to create a bundle instance parameterized with {@link PropertyKey} strings,
 * this allows not to implement the bundle methods manually.
 *
 * <h3>Example</h3>
 * Bundle declaration:
 * <pre>
 * public final class MyPluginConstants {
 *
 *   private MyPluginConstants() { }
 *
 *   private static final String BUNDLE_FQN = "messages.MyPluginBundle";
 *   public static final TypedBundle<@PropertyKey(resourceBundle = BUNDLE_FQN) String> BUNDLE = TypedBundle.createBundle(BUNDLE_FQN);
 * }
 * </pre>
 * Bundle usage:
 * <pre>
 * // automatically recognized due to substitution of 'K' -> '@PropertyKey(resourceBundle = BUNDLE_FQN) String'
 * MyPluginConstants.BUNDLE.message("message.key", a1, a2)
 * </pre>
 *
 * @param <K> bundle key string, the argument is expected to be annotated with {@link PropertyKey}
 */
@SuppressWarnings("TypeParameterExtendsFinalClass")
@NonExtendable
public interface TypedBundle<K extends String> {

  @NotNull @Nls String message(@NotNull K key, @Nullable Object @NotNull ... params);

  @NotNull Supplier<@Nls String> lazyMessage(@NotNull K key, @Nullable Object @NotNull ... params);

  static <K extends String> @NotNull TypedBundle<K> createBundle(@NotNull String bundleFqn) {
    return new TypedBundleImpl<>(bundleFqn);
  }
}
