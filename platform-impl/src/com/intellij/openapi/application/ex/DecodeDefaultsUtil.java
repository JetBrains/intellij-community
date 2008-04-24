package com.intellij.openapi.application.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DecodeDefaultsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.ex.DecodeDefaultsUtil");
  private static Map<String, URL> myResourceCache = Collections.synchronizedMap(new HashMap<String, URL>());

  @NonNls private static final String XML_EXTENSION = ".xml";

  public static URL getDefaults(Object requestor, final String componentResourcePath) {
    if (myResourceCache.containsKey(componentResourcePath)) {
      return myResourceCache.get(componentResourcePath);
    }

    URL url = getDefaultsImpl(requestor, componentResourcePath);
    myResourceCache.put(componentResourcePath, url);
    return url;
  }

  private static URL getDefaultsImpl(final Object requestor, final String componentResourcePath) {
    boolean isPathAbsoulte = StringUtil.startsWithChar(componentResourcePath, '/');
    if (isPathAbsoulte) {
      return requestor.getClass().getResource(componentResourcePath + XML_EXTENSION);
    }
    else {
      return getResourceByRelativePath(requestor, componentResourcePath, XML_EXTENSION);
    }
  }


  @Nullable
  public static InputStream getDefaultsInputStream(Object requestor, final String componentResourcePath) {
    try {
      final URL defaults = getDefaults(requestor, componentResourcePath);
      return defaults != null ? URLUtil.openStream(defaults) : null;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  private static URL getResourceByRelativePath(Object requestor, final String componentResourcePath, String resourceExtension) {
    String appName = ApplicationManagerEx.getApplicationEx().getName();
    URL result = requestor.getClass().getResource("/" + appName + "/" + componentResourcePath + resourceExtension);
    if (result == null) {
      result = requestor.getClass().getResource("/" + componentResourcePath + resourceExtension);
    }
    return result;
  }
}
