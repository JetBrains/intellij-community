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
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public abstract class BeforeAfterActionMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.BeforeAfterActionMetaData");

  protected static final TextDescriptor[] EMPTY_EXAMPLE = new TextDescriptor[0];
  protected static final TextDescriptor EMPTY_DESCRIPTION = new PlainTextDescriptor("", "");

  @NonNls protected static final String DESCRIPTION_FILE_NAME = "description.html";
  @NonNls static final String EXAMPLE_USAGE_URL_SUFFIX = ".template";
  @NonNls private static final String BEFORE_TEMPLATE_PREFIX = "before";
  @NonNls private static final String AFTER_TEMPLATE_PREFIX = "after";
  protected final ClassLoader myLoader;
  protected final String myDescriptionDirectoryName;
  protected TextDescriptor[] myExampleUsagesBefore = null;
  protected TextDescriptor[] myExampleUsagesAfter = null;
  protected TextDescriptor myDescription = null;


  public BeforeAfterActionMetaData(ClassLoader loader, String descriptionDirectoryName) {
    myLoader = loader;
    myDescriptionDirectoryName = descriptionDirectoryName;
  }

  public BeforeAfterActionMetaData(final TextDescriptor description,
                                   final TextDescriptor[] exampleUsagesBefore,
                                   final TextDescriptor[] exampleUsagesAfter) {
    myLoader = null;
    myDescriptionDirectoryName = null;

    myExampleUsagesBefore = exampleUsagesBefore;
    myExampleUsagesAfter = exampleUsagesAfter;
    myDescription = description;
  }

  @NotNull
  private static TextDescriptor[] retrieveURLs(@NotNull URL descriptionDirectory, @NotNull String prefix, @NotNull String suffix)
    throws MalformedURLException {
    List<TextDescriptor> urls = new ArrayList<>();
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      final String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (String extension : extensions) {
        for (int i = 0; ; i++) {
          URL url = new URL(descriptionDirectory.toExternalForm() + "/" +
                            prefix + "." + extension + (i == 0 ? "" : Integer.toString(i)) +
                            suffix);
          try {
            InputStream inputStream = url.openStream();
            inputStream.close();
            urls.add(new ResourceTextDescriptor(url));
          }
          catch (IOException ioe) {
            break;
          }
        }
      }
    }
    if (urls.isEmpty()) {
      String[] children;
      Exception cause = null;
      try {
        URI uri = descriptionDirectory.toURI();
        children = uri.isOpaque() ? null : ObjectUtils.notNull(new File(uri).list(), ArrayUtil.EMPTY_STRING_ARRAY);
      }
      catch (URISyntaxException e) {
        cause = e;
        children = null;
      }
      catch (IllegalArgumentException e) {
        cause = e;
        children = null;
      }
      LOG.error("URLs not found for available file types and prefix: '" +
                prefix +
                "', suffix: '" +
                suffix +
                "';" +
                " in directory: '" +
                descriptionDirectory +
                "'" +
                (children == null ? "" : "; directory contents: " + Arrays.asList(children)), cause);
      return EMPTY_EXAMPLE;
    }
    return urls.toArray(new TextDescriptor[urls.size()]);
  }

  @NotNull
  public TextDescriptor[] getExampleUsagesBefore() {
    if (myExampleUsagesBefore == null) {
      try {
        myExampleUsagesBefore = retrieveURLs(getDirURL(), BEFORE_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
        return EMPTY_EXAMPLE;
      }
    }
    return myExampleUsagesBefore;
  }

  @NotNull
  public TextDescriptor[] getExampleUsagesAfter() {
    if (myExampleUsagesAfter == null) {
      try {
        myExampleUsagesAfter = retrieveURLs(getDirURL(), AFTER_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
        return EMPTY_EXAMPLE;
      }
    }
    return myExampleUsagesAfter;
  }

  @NotNull
  public TextDescriptor getDescription() {
    if (myDescription == null) {
      try {
        final URL dirURL = getDirURL();
        URL descriptionURL = new URL(dirURL.toExternalForm() + "/" + DESCRIPTION_FILE_NAME);
        myDescription = new ResourceTextDescriptor(descriptionURL);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
        return EMPTY_DESCRIPTION;
      }
    }
    return myDescription;
  }

  @NotNull
  protected abstract URL getDirURL();
}
