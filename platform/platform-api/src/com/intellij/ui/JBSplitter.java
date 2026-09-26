// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Major difference between the parent class and {@code JBSplitter} is an ability to save proportion.
 *
 * @author Konstantin Bulenkov
 * @see Splitter
 */
public class JBSplitter extends Splitter {
  /**
   * Used as a key to save and load proportion
   */
  private @Nullable String mySplitterProportionKey = null;
  private final float myDefaultProportion;

  public JBSplitter() {
    super();

    myDefaultProportion = 0.5f;
  }

  public JBSplitter(@NotNull @NonNls String proportionKey, float defaultProportion) {
    this(false, proportionKey, defaultProportion);
  }

  public JBSplitter(boolean vertical, @NotNull @NonNls String proportionKey, float defaultProportion) {
    super(vertical, defaultProportion);

    mySplitterProportionKey = proportionKey;
    myDefaultProportion = defaultProportion;
  }

  public JBSplitter(boolean vertical) {
    super(vertical);

    myDefaultProportion = 0.5f;
  }

  public JBSplitter(boolean vertical, float proportion) {
    super(vertical, proportion);

    myDefaultProportion = proportion;
  }

  public JBSplitter(float proportion) {
    super(false, proportion);

    myDefaultProportion = proportion;
  }

  public JBSplitter(boolean vertical, float proportion, float minProp, float maxProp) {
    super(vertical, proportion, minProp, maxProp);

    myDefaultProportion = proportion;
  }

  public JBSplitter(boolean vertical, @NotNull @NonNls String proportionKey, float minProp, float maxProp) {
    super(vertical, PropertiesComponent.getInstance().getFloat(proportionKey, 0.5f), minProp, maxProp);

    mySplitterProportionKey = proportionKey;
    myDefaultProportion = 0.5f;
  }

  /**
   * Splitter proportion unique key.
   *
   * @return non empty unique String or {@code null} if splitter does not require proportion saving
   */
  public final @Nullable String getSplitterProportionKey() {
    return mySplitterProportionKey;
  }

  /**
   * Sets proportion key.
   *
   * @param key non empty unique String or {@code null} if splitter does not require proportion saving
   */
  public final void setSplitterProportionKey(@Nullable String key) {
    mySplitterProportionKey = key;
  }

  /**
   * Sets proportion key and load from settings.
   *
   * @see #setSplitterProportionKey(String)
   */
  public final void setAndLoadSplitterProportionKey(@NotNull String key) {
    setSplitterProportionKey(key);
    loadProportion();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    loadProportion();
  }

  @Override
  public void setProportion(float proportion) {
    super.setProportion(proportion);
    saveProportion();
  }

  protected void loadProportion() {
    if (!StringUtil.isEmpty(mySplitterProportionKey)) {
      setProportion(PropertiesComponent.getInstance().getFloat(mySplitterProportionKey, myProportion));
    }
  }

  protected void saveProportion() {
    if (!StringUtil.isEmpty(mySplitterProportionKey)) {
      PropertiesComponent.getInstance().setValue(mySplitterProportionKey, myProportion, myDefaultProportion);
    }
  }
}
