package com.intellij.codeInsight.intention.impl.config;

import com.intellij.util.ResourceUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.net.URL;
import java.io.IOException;
import java.io.File;

/**
 * @author yole
 */
public class ResourceTextDescriptor implements TextDescriptor {
  private final URL myUrl;

  public ResourceTextDescriptor(final URL url) {
    myUrl = url;
  }

  public String getText() throws IOException {
    return ResourceUtil.loadText(myUrl);
  }

  public String getFileName() {
    return StringUtil.trimEnd(new File(myUrl.getFile()).getName(), IntentionActionMetaData.EXAMPLE_USAGE_URL_SUFFIX);
  }
}
