/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Used to group ordered collections of {@link StdArrangementTokens} along with their {@link StdArrangementTokenUiRole roles}.
 * 
 * @author Denis Zhdanov
 * @since 3/6/13 8:01 PM
 */
public class CompositeArrangementSettingsToken {

  private static final Function<ArrangementSettingsToken, CompositeArrangementSettingsToken> WRAPPER =
    new Function<ArrangementSettingsToken, CompositeArrangementSettingsToken>() {
      @Override
      public CompositeArrangementSettingsToken fun(ArrangementSettingsToken token) {
        return new CompositeArrangementSettingsToken(token, deduceRole(token), Collections.<CompositeArrangementSettingsToken>emptyList());
      }
    };

  @NotNull private final List<CompositeArrangementSettingsToken> myChildren = ContainerUtilRt.newArrayList();

  @NotNull private final ArrangementSettingsToken  myToken;
  @NotNull private final StdArrangementTokenUiRole myRole;

  /**
   * Creates new <code>CompositeArrangementSettingsToken</code> object with no nested tokens.
   * <p/>
   * <b>Note:</b> given token is expected to be one of {@link StdArrangementTokens standard tokens} because
   * {@link StdArrangementTokenUiRole its role} is deduced.
   * 
   * @param token  token to wrap
   */
  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token) {
    this(token, deduceRole(token), Collections.<CompositeArrangementSettingsToken>emptyList());
  }

  /**
   * Creates new <code>CompositeArrangementSettingsToken</code> object with the given token and all given children
   * assuming that every child {@link CompositeArrangementSettingsToken} will have no children.
   * <p/>
   * <b>Note:</b> given tokens are expected to be from {@link StdArrangementTokens standard tokens} because
   * {@link StdArrangementTokenUiRole their roles} are deduced.
   * 
   * @param token     token to wrap
   * @param children  children to wrap
   */
  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token,
                                           @NotNull ArrangementSettingsToken... children)
  {
    this(token, deduceRole(token), ContainerUtilRt.map2List(children, WRAPPER));
  }

  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token,
                                           @NotNull Collection<ArrangementSettingsToken> children)
  {
    this(token, deduceRole(token), ContainerUtilRt.map2List(children, WRAPPER));
  }

  public CompositeArrangementSettingsToken(@NotNull ArrangementSettingsToken token,
                                           @NotNull StdArrangementTokenUiRole role,
                                           @NotNull List<CompositeArrangementSettingsToken> children)
  {
    myToken = token;
    myRole = role;
    myChildren.addAll(children);
  }

  @NotNull
  private static StdArrangementTokenUiRole deduceRole(@NotNull ArrangementSettingsToken token) {
    final StdArrangementTokenUiRole role = token instanceof StdArrangementSettingsToken ?
                                     ((StdArrangementSettingsToken)token).getTokenType().getUiRole() : null;
    if (role == null) {
      throw new IllegalArgumentException("Can't deduce UI role for token " + token);
    }
    return role;
  }

  @NotNull
  public List<CompositeArrangementSettingsToken> getChildren() {
    return myChildren;
  }

  @NotNull
  public ArrangementSettingsToken getToken() {
    return myToken;
  }

  @NotNull
  public StdArrangementTokenUiRole getRole() {
    return myRole;
  }

  @Override
  public String toString() {
    return myToken.toString() + "-" + myRole;
  }
}
