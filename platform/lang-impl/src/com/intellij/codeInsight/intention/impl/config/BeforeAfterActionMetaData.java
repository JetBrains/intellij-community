// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class BeforeAfterActionMetaData implements BeforeAfterMetaData {
  private static final Logger LOG = Logger.getInstance(BeforeAfterActionMetaData.class);

  protected static final TextDescriptor[] EMPTY_EXAMPLE = new TextDescriptor[0];
  protected static final TextDescriptor EMPTY_DESCRIPTION = new PlainTextDescriptor("", "");

  protected static final @NonNls String DESCRIPTION_FILE_NAME = "description.html";
  static final @NonNls String EXAMPLE_USAGE_URL_SUFFIX = ".template";
  private static final @NonNls String BEFORE_TEMPLATE_PREFIX = "before";
  private static final @NonNls String AFTER_TEMPLATE_PREFIX = "after";
  protected final ClassLoader myLoader;
  protected final String myDescriptionDirectoryName;
  private TextDescriptor[] myExampleUsagesBefore;
  private TextDescriptor[] myExampleUsagesAfter;
  protected TextDescriptor myDescription;

  public BeforeAfterActionMetaData(@Nullable ClassLoader loader, @NotNull String descriptionDirectoryName) {
    myLoader = loader;
    myDescriptionDirectoryName = descriptionDirectoryName;
  }

  public BeforeAfterActionMetaData(@NotNull TextDescriptor description,
                                   TextDescriptor @NotNull [] exampleUsagesBefore,
                                   TextDescriptor @NotNull [] exampleUsagesAfter) {
    myLoader = null;
    myDescriptionDirectoryName = null;

    myExampleUsagesBefore = exampleUsagesBefore;
    myExampleUsagesAfter = exampleUsagesAfter;
    myDescription = description;
  }

  public ClassLoader getLoader() {
    return myLoader;
  }

  private TextDescriptor @NotNull [] retrieveURLs(@NotNull String prefix, @NotNull String suffix) {
    Set<TextDescriptor> urls = new LinkedHashSet<>();
    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      List<FileNameMatcher> matchers = FileTypeManager.getInstance().getAssociations(fileType);
      for (FileNameMatcher matcher : matchers) {
        if (matcher instanceof ExactFileNameMatcher exactFileNameMatcher) {
          String fileName = StringUtil.trimStart(exactFileNameMatcher.getFileName(), ".");
          String resourcePath = getResourceLocation(prefix + "." + fileName + suffix);
          URL resource = myLoader.getResource(resourcePath);
          if (resource != null) urls.add(new ResourceTextDescriptor(myLoader, resourcePath));
        }
        else if (matcher instanceof ExtensionFileNameMatcher extensionFileNameMatcher) {
          String extension = extensionFileNameMatcher.getExtension();
          for (int i = 0; ; i++) {
            String resourcePath = getResourceLocation(prefix + "." + extension + (i == 0 ? "" : Integer.toString(i))
                                  + suffix);
            URL resource = myLoader.getResource(resourcePath);
            if (resource == null) break;
            urls.add(new ResourceTextDescriptor(myLoader, resourcePath));
          }
        }
      }
    }
    if (urls.isEmpty()) {
      URL descriptionUrl = myLoader.getResource(getResourceLocation(DESCRIPTION_FILE_NAME));
      String url = descriptionUrl.toExternalForm();
      URL descriptionDirectory = null;
      String[] children;
      Exception cause = null;
      try {
        descriptionDirectory = new URL(url.substring(0, url.lastIndexOf('/')));
        URI uri = descriptionDirectory.toURI();
        children = uri.isOpaque() ? null : ObjectUtils.notNull(new File(uri).list(), ArrayUtilRt.EMPTY_STRING_ARRAY);
      }
      catch (URISyntaxException | IllegalArgumentException | MalformedURLException e) {
        cause = e;
        children = null;
      }
      LOG.error("URLs not found for available file types and prefix: '" + prefix
                + "', suffix: '" + suffix + "';" +
                " in directory: '" + descriptionDirectory + "'" +
                (children == null ? "" : "; directory contents: " + Arrays.asList(children)), cause);
      return EMPTY_EXAMPLE;
    }
    return urls.toArray(new TextDescriptor[0]);
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesBefore() {
    if (myExampleUsagesBefore == null) {
      myExampleUsagesBefore = retrieveURLs(BEFORE_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
    }
    return myExampleUsagesBefore;
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesAfter() {
    if (myExampleUsagesAfter == null) {
      myExampleUsagesAfter = retrieveURLs(AFTER_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
    }
    return myExampleUsagesAfter;
  }

  @Override
  public @NotNull TextDescriptor getDescription() {
    if (myDescription == null) {
      myDescription = new ResourceTextDescriptor(myLoader, getResourceLocation(DESCRIPTION_FILE_NAME));
    }
    return myDescription;
  }

  protected abstract String getResourceLocation(String resourceName);
}
