/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;



public final class IntentionActionMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData");
  @NotNull private final IntentionAction myAction;
  private final ClassLoader myIntentionLoader;
  private final String myDescriptionDirectoryName;
  @NotNull public final String[] myCategory;

  private TextDescriptor[] myExampleUsagesBefore = null;
  private TextDescriptor[] myExampleUsagesAfter = null;
  private TextDescriptor myDescription = null;
  private URL myDirURL = null;

  @NonNls private static final String BEFORE_TEMPLATE_PREFIX = "before";
  @NonNls private static final String AFTER_TEMPLATE_PREFIX = "after";
  @NonNls static final String EXAMPLE_USAGE_URL_SUFFIX = ".template";
  @NonNls private static final String DESCRIPTION_FILE_NAME = "description.html";
  @NonNls private static final String INTENTION_DESCRIPTION_FOLDER = "intentionDescriptions";

  public IntentionActionMetaData(@NotNull IntentionAction action,
                                 @Nullable ClassLoader loader,
                                 @NotNull String[] category,
                                 @NotNull String descriptionDirectoryName) {
    myAction = action;
    myIntentionLoader = loader;
    myCategory = category;
    myDescriptionDirectoryName = descriptionDirectoryName;
  }

  public IntentionActionMetaData(@NotNull final IntentionAction action,
                                 @NotNull final String[] category,
                                 final TextDescriptor description,
                                 final TextDescriptor[] exampleUsagesBefore,
                                 final TextDescriptor[] exampleUsagesAfter) {
    myAction = action;
    myCategory = category;
    myExampleUsagesBefore = exampleUsagesBefore;
    myExampleUsagesAfter = exampleUsagesAfter;
    myDescription = description;
    myIntentionLoader = null;
    myDescriptionDirectoryName = null;
  }

  public String toString() {
    return getFamily();
  }

  @NotNull
  public TextDescriptor[] getExampleUsagesBefore() {
    if(myExampleUsagesBefore == null){
      try {
        myExampleUsagesBefore = retrieveURLs(getDirURL(), BEFORE_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myExampleUsagesBefore;
  }

  @NotNull
  public TextDescriptor[] getExampleUsagesAfter() {
      if(myExampleUsagesAfter == null){
      try {
        myExampleUsagesAfter = retrieveURLs(getDirURL(), AFTER_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myExampleUsagesAfter;
  }

  @NotNull
  public TextDescriptor getDescription() {
    if(myDescription == null){
      try {
        final URL dirURL = getDirURL();
        URL descriptionURL = new URL(dirURL.toExternalForm() + "/" + DESCRIPTION_FILE_NAME);
        myDescription = new ResourceTextDescriptor(descriptionURL);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myDescription;
  }

  private static TextDescriptor[] retrieveURLs(@NotNull URL descriptionDirectory, @NotNull String prefix, @NotNull String suffix) throws MalformedURLException {
    List<TextDescriptor> urls = new ArrayList<TextDescriptor>();
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
    return urls.isEmpty() ? null : urls.toArray(new TextDescriptor[urls.size()]);
  }

  @Nullable
  private static URL getIntentionDescriptionDirURL(ClassLoader aClassLoader, String intentionFolderName) {
    final URL pageURL = aClassLoader.getResource(INTENTION_DESCRIPTION_FOLDER + "/" + intentionFolderName+"/"+ DESCRIPTION_FILE_NAME);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Path:"+"intentionDescriptions/" + intentionFolderName);
      LOG.debug("URL:"+pageURL);
    }
    if (pageURL != null) {
      try {
        final String url = pageURL.toExternalForm();
        return new URL(url.substring(0, url.lastIndexOf('/')));
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  private URL getDirURL() {
    if (myDirURL == null) {
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, myDescriptionDirectoryName);
    }
    if (myDirURL == null) { //plugin compatibility
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, getFamily());
    }
    LOG.assertTrue(myDirURL != null, "Intention Description Dir URL is null: " +
                                     getFamily() +"; "+myDescriptionDirectoryName + ", " + myIntentionLoader);
    return myDirURL;
  }

  @Nullable public PluginId getPluginId() {
    if (myIntentionLoader instanceof PluginClassLoader) {
      return ((PluginClassLoader)myIntentionLoader).getPluginId();
    }
    return null;
  }

  @NotNull
  public String getFamily() {
    return myAction.getFamilyName();
  }

  @NotNull
  public IntentionAction getAction() {
    return myAction;
  }
}
