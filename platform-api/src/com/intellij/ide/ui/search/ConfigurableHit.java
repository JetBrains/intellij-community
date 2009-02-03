package com.intellij.ide.ui.search;

import com.intellij.openapi.options.Configurable;

import java.util.Set;
import java.util.LinkedHashSet;

public final class ConfigurableHit {

  private final Set<Configurable> myNameHits = new LinkedHashSet<Configurable>();
  private final Set<Configurable> myContentHits = new LinkedHashSet<Configurable>();

  private final Set<Configurable> myNameFullHit = new LinkedHashSet<Configurable>();

  ConfigurableHit() {
  }

  public Set<Configurable> getNameHits() {
    return myNameHits;
  }

  public Set<Configurable> getNameFullHits() {
    return myNameFullHit;
  }

  public Set<Configurable> getContentHits() {
    return myContentHits;
  }

  public Set<Configurable> getAll() {
    final LinkedHashSet<Configurable> all = new LinkedHashSet<Configurable>(myNameHits.size() + myContentHits.size());
    all.addAll(myNameHits);
    all.addAll(myContentHits);
    return all;
  }
}
