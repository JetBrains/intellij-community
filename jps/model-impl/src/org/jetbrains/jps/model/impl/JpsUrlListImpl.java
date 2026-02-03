// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.ArrayList;
import java.util.List;

final class JpsUrlListImpl extends JpsElementBase<JpsUrlListImpl> implements JpsUrlList {
  private final List<String> myUrls = new ArrayList<>();

  JpsUrlListImpl() {
  }

  JpsUrlListImpl(JpsUrlListImpl list) {
    myUrls.addAll(list.myUrls);
  }

  @Override
  public @NotNull JpsUrlListImpl createCopy() {
    return new JpsUrlListImpl(this);
  }

  @Override
  public @NotNull List<String> getUrls() {
    return myUrls;
  }

  @Override
  public void addUrl(@NotNull String url) {
    myUrls.add(url);
  }

  @Override
  public void removeUrl(@NotNull String url) {
    myUrls.remove(url);
  }
}
