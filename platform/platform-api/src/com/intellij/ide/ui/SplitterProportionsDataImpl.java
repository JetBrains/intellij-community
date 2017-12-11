/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.SmartList;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

@Tag("splitter-proportions")
public class SplitterProportionsDataImpl implements SplitterProportionsData {
  private static final String DATA_VERSION = "1";
  @NonNls private static final String ATTRIBUTE_PROPORTIONS = "proportions";
  @NonNls private static final String ATTRIBUTE_VERSION = "version";

  private List<Float> proportions = new SmartList<>();

  @Override
  public void saveSplitterProportions(Component root) {
    proportions.clear();
    doSaveSplitterProportions(root);
  }

  private void doSaveSplitterProportions(Component root) {
    if (root instanceof Splitter) {
      Float prop = ((Splitter)root).getProportion();
      proportions.add(prop);
    }
    if (root instanceof Container) {
      for (Component child : ((Container)root).getComponents()) {
        doSaveSplitterProportions(child);
      }
    }
  }

  @Override
  public void restoreSplitterProportions(Component root) {
    restoreSplitterProportions(root, 0);
  }

  private int restoreSplitterProportions(Component root, int index) {
    if (root instanceof Splitter) {
      if (proportions.size() <= index) return index;
      ((Splitter)root).setProportion(proportions.get(index++).floatValue());
    }
    if (root instanceof Container) {
      Component[] children = ((Container)root).getComponents();
      for (Component child : children) {
        index = restoreSplitterProportions(child, index);
      }
    }
    return index;
  }

  @Override
  public void externalizeToDimensionService(String key) {
    for (int i = 0; i < proportions.size(); i++) {
      PropertiesComponent.getInstance().setValue(key + "." + i, (int)(proportions.get(i).floatValue() * 1000), -1);
    }
  }
  @Override
  public void externalizeFromDimensionService(String key) {
    proportions.clear();
    for (int i = 0; ;i++) {
      int value = PropertiesComponent.getInstance().getInt(key + "." + i, -1);
      if (value == -1) {
        break;
      }

      proportions.add(new Float(value * 0.001));
    }
  }

  @Override
  public void readExternal(Element element) {
    proportions.clear();
    String prop = element.getAttributeValue(ATTRIBUTE_PROPORTIONS);
    String version = element.getAttributeValue(ATTRIBUTE_VERSION);
    if (prop != null && Comparing.equal(version, DATA_VERSION)) {
      StringTokenizer tokenizer = new StringTokenizer(prop, ",");
      while (tokenizer.hasMoreTokens()) {
        String p = tokenizer.nextToken();
        proportions.add(Float.valueOf(p));
      }
    }
  }

  @Override
  public void writeExternal(Element element) {
    StringBuilder result = new StringBuilder();
    String sep = "";
    for (Float proportion : proportions) {
      result.append(sep);
      result.append(proportion);
      sep = ",";
    }
    element.setAttribute(ATTRIBUTE_PROPORTIONS, result.toString());
    element.setAttribute(ATTRIBUTE_VERSION, DATA_VERSION);
  }

  public static final class SplitterProportionsConverter extends Converter<SplitterProportionsDataImpl> {
    @NotNull
    @Override
    public SplitterProportionsDataImpl fromString(@NotNull String value) {
      SplitterProportionsDataImpl data = new SplitterProportionsDataImpl();
      StringTokenizer tokenizer = new StringTokenizer(value, ",");
      while (tokenizer.hasMoreTokens()) {
        data.proportions.add(Float.valueOf(tokenizer.nextToken()));
      }
      return data;
    }

    @NotNull
    @Override
    public String toString(@NotNull SplitterProportionsDataImpl data) {
      StringBuilder result = new StringBuilder();
      String sep = "";
      for (Float proportion : data.proportions) {
        result.append(sep);
        result.append(proportion);
        sep = ",";
      }
      return result.toString();
    }
  }

  public List<Float> getProportions() {
    return proportions;
  }

  public void setProportions(final List<Float> proportions) {
    this.proportions = proportions;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SplitterProportionsDataImpl && ((SplitterProportionsDataImpl)obj).getProportions().equals(proportions);
  }
}