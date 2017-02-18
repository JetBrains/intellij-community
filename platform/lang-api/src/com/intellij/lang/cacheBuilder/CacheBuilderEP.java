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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class CacheBuilderEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.cacheBuilder.CacheBuilderEP");

  public static final ExtensionPointName<CacheBuilderEP> EP_NAME = new ExtensionPointName<>("com.intellij.cacheBuilder");


  @Attribute("fileType")
  public String fileType;
  @Attribute("wordsScannerClass")
  public String wordsScannerClass;
  private WordsScanner myWordsScanner;
  private PluginDescriptor myPluginDescriptor;

  public String getFileType() {
    return fileType;
  }

  public void setFileType(final String fileType) {
    this.fileType = fileType;
  }


  @Override
  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public String getWordsScannerClass() {
    return wordsScannerClass;
  }

  public void setWordsScannerClass(final String wordsScannerClass) {
    this.wordsScannerClass = wordsScannerClass;
  }

  public WordsScanner getWordsScanner() {
    if (myWordsScanner == null) {
      try {
        final Class<?> aClass = Class.forName(wordsScannerClass, true,
                                              myPluginDescriptor == null ? getClass().getClassLoader()  : myPluginDescriptor.getPluginClassLoader());
        myWordsScanner = (WordsScanner) aClass.newInstance();
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myWordsScanner;
  }
}