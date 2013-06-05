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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XsdNamespaceBuilder extends NanoXmlUtil.IXMLBuilderAdapter implements Comparable<XsdNamespaceBuilder> {

  public static String computeNamespace(final InputStream is) {
    return computeNamespace(new InputStreamReader(is)).getNamespace();
  }

  public static XsdNamespaceBuilder computeNamespace(final Reader reader) {
    try {
      final XsdNamespaceBuilder builder = new XsdNamespaceBuilder();
      NanoXmlUtil.parse(reader, builder);
      return builder;
    }
    finally {
      try {
        if (reader != null) {
          reader.close();
        }
      }
      catch (IOException e) {
        // can never happen
      }
    }
  }

  private String myCurrentTag;

  private int myCurrentDepth;
  private String myNamespace;

  private String myVersion;
  private List<String> myTags = new ArrayList<String>();
  private final List<String> myAttributes = new ArrayList<String>();
  @Override
  public void startElement(@NonNls final String name, @NonNls final String nsPrefix, @NonNls final String nsURI, final String systemID, final int lineNr)
      throws Exception {

    if (myCurrentDepth < 2 && "http://www.w3.org/2001/XMLSchema".equals(nsURI)) {
      myCurrentTag = name;
    }
    myCurrentDepth++;
  }

  @Override
  public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
    myCurrentDepth--;
    myCurrentTag = null;
  }

  @Override
  public void addAttribute(@NonNls final String key, final String nsPrefix, final String nsURI, final String value, final String type)
      throws Exception {
    if ("schema".equals(myCurrentTag)) {
      if ("targetNamespace".equals(key)) {
        myNamespace = value;
      }
      else if ("version".equals(key)) {
        myVersion = value;
      }
    }
    else if ("element".equals(myCurrentTag) && "name".equals(key)) {
      myTags.add(value);
    }
  }

  @Override
  public int compareTo(XsdNamespaceBuilder o) {
    return Comparing.compare(myNamespace, o.myNamespace);
  }

  public int getRating(@Nullable String tagName, @Nullable String version) {
    int rate = 0;
    if (tagName != null && myTags.contains(tagName)) {
      rate |= 0x02;
    }
    if (Comparing.equal(version, myVersion)) {
      rate |= 0x01;
    }
    return rate;
  }

  private XsdNamespaceBuilder() {
  }

  public XsdNamespaceBuilder(String namespace, String version, List<String> tags) {
    myNamespace = namespace;
    myVersion = version;
    myTags = tags;
  }

  public String getNamespace() {
    return myNamespace;
  }

  public String getVersion() {
    return myVersion;
  }

  public List<String> getTags() {
    return myTags;
  }

  public List<String> getAttributes() {
    return myAttributes;
  }
}
