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

package com.intellij.lang.cacheBuilder;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class CacheBuilderEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance(CacheBuilderEP.class);

  public static final ExtensionPointName<CacheBuilderEP> EP_NAME = new ExtensionPointName<>("com.intellij.cacheBuilder");

  @Attribute("fileType")
  public String fileType;
  @Attribute("wordsScannerClass")
  public String wordsScannerClass;

  private Class<WordsScanner> myCachedClass;

  String getFileType() {
    return fileType;
  }

  WordsScanner getWordsScanner() {
    try {
      Class<WordsScanner> aClass = myCachedClass;
      if (aClass == null) {
        myCachedClass = aClass = findExtensionClass(wordsScannerClass);
      }
      return aClass.newInstance();
    }
    catch (Exception e) {
      LOG.error(new PluginException(e, getPluginId()));
      return null;
    }
  }
}