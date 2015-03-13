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

package com.intellij.diagnostic.logging;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.ImageLoader;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * User: anna
 * Date: 22-Mar-2006
 */
public class LogFilter implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.diagnostic.logging.LogFilter");

  public String myName;
  public String myIconPath;
  private Icon myIcon;


  public LogFilter(final String name, final Icon icon) {
    myName = name;
    myIcon = icon;
  }

  public LogFilter(final String name, final String iconPath) {
    myName = name;
    myIconPath = iconPath;
  }

  public LogFilter(String name) {
    myName = name;
  }

  //read external
  public LogFilter() {
  }

  @Override
  public String toString() {
    return myName;
  }

  public void setIcon(final Icon icon) {
    myIcon = icon;
  }

  public boolean isAcceptable(String line){
    return true;
  }

  public String getName(){
    return myName;
  }


  public Icon getIcon() {
    if (myIcon != null) {
      return myIcon;
    }
    if (myIconPath != null && new File(FileUtil.toSystemDependentName(myIconPath)).exists()) {
      Image image = null;
      try {
        image = ImageLoader.loadFromStream(VfsUtilCore.convertToURL(VfsUtil.pathToUrl(myIconPath)).openStream());
      }
      catch (IOException e) {
        LOG.debug(e);
      }

      if (image != null){
        return IconLoader.getIcon(image);
      }
    }
    //return IconLoader.getIcon("/ant/filter.png");
    return null;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
