/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.intention.impl.config.BeforeAfterActionMetaData;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public final class PostfixTemplateMetaData extends BeforeAfterActionMetaData {

  public static final String KEY = "$key";

  public static final PostfixTemplateMetaData EMPTY_METADATA = new PostfixTemplateMetaData();
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.postfix.settings.PostfixTemplateMetaData");
  private static final String DESCRIPTION_FOLDER = "postfixTemplates";

  @NotNull
  static PostfixTemplateMetaData createMetaData(@Nullable PostfixTemplate template) {
    if (template == null) return EMPTY_METADATA;

    return new PostfixTemplateMetaData(template);
  }

  private URL urlDir = null;
  private PostfixTemplate myTemplate;

  public PostfixTemplateMetaData(PostfixTemplate template) {
    super(template.getClass().getClassLoader(), template.getClass().getSimpleName());
    myTemplate = template;
  }

  PostfixTemplateMetaData() {
    super(EMPTY_DESCRIPTION, EMPTY_EXAMPLE, EMPTY_EXAMPLE);
  }

  @NotNull
  @Override
  public TextDescriptor[] getExampleUsagesBefore() {

    return decorateTextDescriptor(super.getExampleUsagesBefore());
  }

  @NotNull
  private TextDescriptor[] decorateTextDescriptor(TextDescriptor[] before) {
    List<TextDescriptor> list = ContainerUtil.newArrayList();
    for (final TextDescriptor descriptor : before) {
      list.add(new TextDescriptor() {
        @Override
        public String getText() throws IOException {
          return StringUtil.replace(descriptor.getText(), KEY, myTemplate.getKey());
        }

        @Override
        public String getFileName() {
          return descriptor.getFileName();
        }
      });
    }
    return list.toArray(new TextDescriptor[list.size()]);
  }

  @NotNull
  @Override
  public TextDescriptor[] getExampleUsagesAfter() {
    return decorateTextDescriptor(super.getExampleUsagesAfter());
  }

  @NotNull
  @Override
  protected URL getDirURL() {
    if (urlDir != null) {
      return urlDir;
    }

    final URL pageURL = myLoader.getResource(DESCRIPTION_FOLDER + "/" + myDescriptionDirectoryName + "/" + DESCRIPTION_FILE_NAME);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Path:" + DESCRIPTION_FOLDER + "/" + myDescriptionDirectoryName);
      LOG.debug("URL:" + pageURL);
    }
    if (pageURL != null) {
      try {
        final String url = pageURL.toExternalForm();
        urlDir = UrlClassLoader.internProtocol(new URL(url.substring(0, url.lastIndexOf('/'))));
        return urlDir;
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return null;
  }
}
