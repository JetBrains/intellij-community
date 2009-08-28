package com.intellij.codeInsight.completion;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class CompletionDataEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<CompletionDataEP> EP_NAME = new ExtensionPointName<CompletionDataEP>("com.intellij.completionData");

  // these must be public for scrambling compatibility
  @Attribute("fileType")
  public String fileType;
  @Attribute("className")
  public String className;


  private final LazyInstance<CompletionData> myHandler = new LazyInstance<CompletionData>() {
    protected Class<CompletionData> getInstanceClass() throws ClassNotFoundException {
      return findClass(className);
    }
  };

  public CompletionData getHandler() {
    return myHandler.getValue();
  }
}