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

package com.intellij.xml.index;

import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.util.NullableFunction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Dmitry Avdeev
 */
public class XsdNamespaceBuilder extends NanoXmlUtil.IXMLBuilderAdapter {

  @Nullable
  public static String computeNamespace(final InputStream is) {
    try {
      final XsdNamespaceBuilder builder = new XsdNamespaceBuilder();
      NanoXmlUtil.parse(is, builder);
      return builder.myNamespace;
    }
    finally {
      try {
        if (is != null) {
          is.close();
        }
      }
      catch (IOException e) {
        // can never happen
      }
    }
  }
  
  @Nullable
  public static String computeNamespace(final VirtualFile file) {
    return VfsUtil.processInputStream(file, new NullableFunction<InputStream, String>() {
      public String fun(final InputStream inputStream) {
        return computeNamespace(inputStream);
      }
    });
  }

  private String myNamespace;

  public void startElement(@NonNls final String name, @NonNls final String nsPrefix, @NonNls final String nsURI, final String systemID, final int lineNr)
      throws Exception {

    if ( !nsURI.equals("http://www.w3.org/2001/XMLSchema") || !name.equals("schema")) {
      stop();
    }
  }

  public void addAttribute(@NonNls final String key, final String nsPrefix, final String nsURI, final String value, final String type)
      throws Exception {
    if (key.equals("targetNamespace")) {
      myNamespace = value;
      stop();
    }
  }

  public void endElement(final String name, final String nsPrefix, final String nsURI) throws Exception {
    stop();
  }
}
