// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class CustomCodeStyleSettings implements Cloneable {
  private CodeStyleSettings myContainer;
  private final String myTagName;

  private final static Logger LOG  = Logger.getInstance(CustomCodeStyleSettings.class);

  protected CustomCodeStyleSettings(@NonNls @NotNull String tagName, @NotNull CodeStyleSettings container) {
    myTagName = tagName;
    myContainer = container;
  }

  public final CodeStyleSettings getContainer() {
    return myContainer;
  }

  @NonNls @NotNull
  public final String getTagName() {
    return myTagName;
  }

  /**
   * in case settings save additional top-level tags, list the list of them to prevent serializer to treat such tag as unknown settings.
   */
  @NotNull
  public List<String> getKnownTagNames() {
    return Collections.singletonList(getTagName());
  }

  public void readExternal(Element parentElement) throws InvalidDataException {
    Element child = parentElement.getChild(myTagName);
    if (child != null) {
      DefaultJDOMExternalizer.readExternal(this, child);
      LOG.info("Loaded " + getClass().getName());
    }
  }

  public void writeExternal(Element parentElement, @NotNull final CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    final Element childElement = new Element(myTagName);
    DefaultJDOMExternalizer.write(this, childElement, new DifferenceFilter<>(this, parentSettings));
    if (!childElement.getContent().isEmpty()) {
      parentElement.addContent(childElement);
    }
  }

  CustomCodeStyleSettings copyWith(@NotNull CodeStyleSettings container) {
    CustomCodeStyleSettings cloned = (CustomCodeStyleSettings)clone();
    cloned.myContainer = container;
    return cloned;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * For compatibility with old code style settings stored in CodeStyleSettings.
   */
  protected void importLegacySettings(@NotNull CodeStyleSettings rootSettings) {
  }

  /**
   * Fired before loading.
   */
  protected void beforeLoading() {}


  /**
   * Fired when settings just loaded.
   *
   * <p>
   *   When the common version (the {@link CodeStyleSettings#myVersion} is not changed, this method is called just after loading.
   *   When the common version is changed, this method called after {@link CustomCodeStyleSettings#importLegacySettings}.
   * </p>
   */
  protected void afterLoaded() {}


  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CustomCodeStyleSettings)) return false;
    if (!ReflectionUtil.comparePublicNonFinalFields(this, obj)) return false;
    return true;
  }
}
