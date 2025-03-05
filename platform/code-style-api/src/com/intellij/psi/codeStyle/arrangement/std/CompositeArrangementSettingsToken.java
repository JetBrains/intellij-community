// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Used to group ordered collections of {@link StdArrangementTokens} along with their {@link StdArrangementTokenUiRole roles}.
 */
public class CompositeArrangementSettingsToken {

  private static final Function<ArrangementSettingsToken, CompositeArrangementSettingsToken> WRAPPER =
    token -> new CompositeArrangementSettingsToken(token, deduceRole(token), Collections.emptyList());

  private final @NotNull List<CompositeArrangementSettingsToken> myChildren = new ArrayList<>();

  private final @NotNull ArrangementSettingsToken  myToken;
  private final @NotNull StdArrangementTokenUiRole myRole;

  /**
   * Creates new {@code CompositeArrangementSettingsToken} object with no nested tokens.
   * <p/>
   * <b>Note:</b> given token is expected to be one of {@link StdArrangementTokens standard tokens} because
   * {@link StdArrangementTokenUiRole its role} is deduced.
   *
   * @param token  token to wrap
   */
  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token) {
    this(token, deduceRole(token), Collections.emptyList());
  }

  /**
   * Creates new {@code CompositeArrangementSettingsToken} object with the given token and all given children
   * assuming that every child {@link CompositeArrangementSettingsToken} will have no children.
   * <p/>
   * <b>Note:</b> given tokens are expected to be from {@link StdArrangementTokens standard tokens} because
   * {@link StdArrangementTokenUiRole their roles} are deduced.
   *
   * @param token     token to wrap
   * @param children  children to wrap
   */
  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token,
                                           ArrangementSettingsToken @NotNull ... children)
  {
    this(token, deduceRole(token), ContainerUtil.map(Arrays.asList(children), WRAPPER));
  }

  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token,
                                           @NotNull Collection<? extends ArrangementSettingsToken> children)
  {
    this(token, deduceRole(token), ContainerUtil.map(children, WRAPPER));
  }

  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token,
                                           @NotNull StdArrangementTokenUiRole role,
                                           @NotNull List<? extends CompositeArrangementSettingsToken> children)
  {
    myToken = token;
    myRole = role;
    myChildren.addAll(children);
  }

  private static @NotNull StdArrangementTokenUiRole deduceRole(@NotNull ArrangementSettingsToken token) {
    final StdArrangementTokenUiRole role = token instanceof StdArrangementSettingsToken ?
                                     ((StdArrangementSettingsToken)token).getTokenType().getUiRole() : null;
    if (role == null) {
      throw new IllegalArgumentException("Can't deduce UI role for token " + token);
    }
    return role;
  }

  public @NotNull List<CompositeArrangementSettingsToken> getChildren() {
    return myChildren;
  }

  public @NotNull ArrangementSettingsToken getToken() {
    return myToken;
  }

  public @NotNull StdArrangementTokenUiRole getRole() {
    return myRole;
  }

  @Override
  public String toString() {
    return myToken.toString() + "-" + myRole;
  }
}
