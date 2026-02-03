// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Rustam Vishnyakov
 */
@ApiStatus.Internal
@State(
  name = "CodeStyleSchemeSettings",
  storages = @Storage("code.style.schemes.xml"),
  additionalExportDirectory = CodeStyleSchemesImpl.CODE_STYLES_DIR_PATH,
  category = SettingsCategory.CODE
)
public final class PersistableCodeStyleSchemes extends CodeStyleSchemesImpl implements PersistentStateComponent<Element> {
  public String CURRENT_SCHEME_NAME = CodeStyleScheme.DEFAULT_SCHEME_NAME;

  public PersistableCodeStyleSchemes() {
    this(SchemeManagerFactory.getInstance());
  }

  @NonInjectable
  @TestOnly
  public PersistableCodeStyleSchemes(@NotNull SchemeManagerFactory schemeManagerFactory) {
    super(schemeManagerFactory);
  }

  @Override
  public @NotNull Element getState() {
    CodeStyleScheme currentScheme = getCurrentScheme();
    CURRENT_SCHEME_NAME = currentScheme == null ? null : currentScheme.getName();
    return XmlSerializer.serialize(this, new SerializationFilter() {
      @Override
      public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
        if ("CURRENT_SCHEME_NAME".equals(accessor.getName())) {
          return !CodeStyleScheme.DEFAULT_SCHEME_NAME.equals(accessor.read(bean));
        }
        else {
          return accessor.getValueClass().equals(String.class);
        }
      }
    });
  }

  @Override
  public void loadState(@NotNull Element state) {
    CURRENT_SCHEME_NAME = CodeStyleScheme.DEFAULT_SCHEME_NAME;
    XmlSerializer.deserializeInto(this, state);
    CodeStyleScheme current = CURRENT_SCHEME_NAME == null ? null : mySchemeManager.findSchemeByName(CURRENT_SCHEME_NAME);
    setCurrentScheme(current == null ? getDefaultScheme() : current);
  }
}
