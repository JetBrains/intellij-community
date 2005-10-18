package com.intellij.execution;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author dyoma
 */
public class ExternalizablePath implements JDOMExternalizable {
  @NonNls private static final String VALUE_ATTRIBUTE = "value";

  private String myUrl;

  public void readExternal(final Element element) throws InvalidDataException {
    final String value = element.getAttributeValue(VALUE_ATTRIBUTE);
    myUrl = value != null ? value : "";
    final String protocol = VirtualFileManager.extractProtocol(myUrl);
    if (protocol == null) myUrl = urlValue(myUrl);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    element.setAttribute(VALUE_ATTRIBUTE, myUrl);
  }

  public String getLocalPath() {
    return localPathValue(myUrl);
  }

  public static String urlValue(String localPath) {
    if (localPath == null) return "";
    localPath = localPath.trim();
    if (localPath.length() == 0) return "";
    return VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, localPath.replace(File.separatorChar, '/'));
  }

  public static String localPathValue(String url) {
    if (url == null) return "";
    url = url.trim();
    if (url.length() == 0) return "";
    return VirtualFileManager.extractPath(url).replace('/', File.separatorChar);
  }
}
