/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

/**
 * @author Eugene Zhuravlev
 *         Date: 3/28/11
 */
public class DefaultTemplate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.DefaultTemplate");
  
  private final String myName;
  private final String myExtension;
  private final URL myTemplateURL;
  @Nullable 
  private final URL myDescriptionURL;
  private final String myText;
  private final String myDescriptionText;

  public DefaultTemplate(@NotNull String name, @NotNull String extension, @NotNull URL templateURL, @Nullable URL descriptionURL) {
    myName = name;
    myExtension = extension;
    myTemplateURL = templateURL;
    myDescriptionURL = descriptionURL;
    myText = loadText(templateURL);
    myDescriptionText = descriptionURL != null? loadText(descriptionURL) : "";
  }

  private static String loadText(URL url) {
    String text = "";
    try {
      text = StringUtil.convertLineSeparators(UrlUtil.loadText(url));
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return text;
  }

  public String getName() {
    return myName;
  }

  public String getQualifiedName() {
    return FileTemplateBase.getQualifiedName(getName(), getExtension());
  }
  
  public String getExtension() {
    return myExtension;
  }

  public URL getTemplateURL() {
    return myTemplateURL;
  }

  @Nullable
  public URL getDescriptionURL() {
    return myDescriptionURL;
  }
  
  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getDescriptionText() {
    return myDescriptionText;
  }
}
