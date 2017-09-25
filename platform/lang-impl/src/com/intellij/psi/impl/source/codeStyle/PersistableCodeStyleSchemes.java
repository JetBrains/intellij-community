/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
@State(
  name = "CodeStyleSchemeSettings",
  storages = {
    @Storage("code.style.schemes.xml"),
    @Storage(value = "other.xml", deprecated = true)
  },
  additionalExportFile = CodeStyleSchemesImpl.CODE_STYLES_DIR_PATH
)
public class PersistableCodeStyleSchemes extends CodeStyleSchemesImpl implements PersistentStateComponent<Element> {
  public String CURRENT_SCHEME_NAME = CodeStyleSchemeImpl.DEFAULT_SCHEME_NAME;

  public PersistableCodeStyleSchemes(@NotNull SchemeManagerFactory schemeManagerFactory) {
    super(schemeManagerFactory);
  }

  @Nullable
  @Override
  public Element getState() {
    CodeStyleScheme currentScheme = getCurrentScheme();
    CURRENT_SCHEME_NAME = currentScheme == null ? null : currentScheme.getName();
    return XmlSerializer.serialize(this, new SerializationFilter() {
      @Override
      public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
        if ("CURRENT_SCHEME_NAME".equals(accessor.getName())) {
          return !CodeStyleSchemeImpl.DEFAULT_SCHEME_NAME.equals(accessor.read(bean));
        }
        else {
          return accessor.getValueClass().equals(String.class);
        }
      }
    });
  }

  @Override
  public void loadState(Element state) {
    CURRENT_SCHEME_NAME = CodeStyleSchemeImpl.DEFAULT_SCHEME_NAME;
    XmlSerializer.deserializeInto(this, state);
    CodeStyleScheme current = CURRENT_SCHEME_NAME == null ? null : mySchemeManager.findSchemeByName(CURRENT_SCHEME_NAME);
    setCurrentScheme(current == null ? getDefaultScheme() : current);
  }
}
