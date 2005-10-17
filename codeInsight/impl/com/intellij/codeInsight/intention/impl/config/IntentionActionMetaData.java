/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public final class IntentionActionMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData");
  public final String myFamily;
  private final ClassLoader myIntentionLoader;
  private final String myDescriptionDirectoryName;
  public final String[] myCategory;

  private URL[] myExampleUsagesBefore = null;
  private URL[] myExampleUsagesAfter = null;
  private URL myDescription = null;
  private URL myDirURL = null;

  private static final @NonNls String BEFORE_TEMPLATE_PREFIX = "before";
  private static final @NonNls String AFTER_TEMPLATE_PREFIX = "after";
  static final @NonNls String EXAMPLE_USAGE_URL_SUFFIX = ".template";
  private static final @NonNls String DESCRIPTION_FILE_NAME = "description.html";
  private static final @NonNls String INTENTION_DESCRIPTION_FOLDER = "intentionDescriptions";

  public IntentionActionMetaData(String family,
                                 ClassLoader loader,
                                 String[] category, final String descriptionDirectoryName) {
    myFamily = family;
    myIntentionLoader = loader;
    myCategory = category;
    myDescriptionDirectoryName = descriptionDirectoryName;
  }

  public String toString() {
    return myFamily;
  }

  public URL[] getExampleUsagesBefore() {
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

  public URL[] getExampleUsagesAfter() {
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

  public URL getDescription() {
    if(myDescription == null){
      try {
        myDescription = new URL(getDirURL().toExternalForm() + "/" + DESCRIPTION_FILE_NAME);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myDescription;
  }

  private static URL[] retrieveURLs(URL descriptionDirectory, String prefix, String suffix) throws MalformedURLException {
    List<URL> urls = new ArrayList<URL>();
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
            urls.add(url);
          }
          catch (IOException ioe) {
            break;
          }
        }
      }
    }
    return urls.toArray(new URL[urls.size()]);
  }

  public static URL getIntentionDescriptionDirURL(ClassLoader aClassLoader, String intentionFolderName) {
    final URL pageURL = aClassLoader.getResource(INTENTION_DESCRIPTION_FOLDER + "/" + intentionFolderName);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Path:"+"intentionDescriptions/" + intentionFolderName);
      LOG.debug("URL:"+pageURL);
    }
    return pageURL;
  }

  public URL getDirURL() {
    if(myDirURL == null)
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, myDescriptionDirectoryName);
    return myDirURL;
  }
}
