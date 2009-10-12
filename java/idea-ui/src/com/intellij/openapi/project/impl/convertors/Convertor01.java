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
package com.intellij.openapi.project.impl.convertors;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;

import java.util.List;
import java.util.Iterator;

@SuppressWarnings({"HardCodedStringLiteral"})
public class Convertor01 {
  private static final String VIRTUAL_FILE_MANAGER_CLASS = "com.intellij.vfs.VirtualFileManager";
  private static final String JAR_FILE_SYSTEM_CLASS = "com.intellij.vfs.jar.JarFileSystem";
  private static final String PROJECT_ROOT_CONTAINER_CLASS = "com.intellij.project.ProjectRootContainer";

  private static final String SOURCE_PATH_ENTRY_ATTRIBUTE = "sourcePathEntry";
  private static final String CLASS_PATH_ENTRY_ATTRIBUTE = "classPathEntry";
  private static final String OUTPUT_PATH_ENTRY_ATTRIBUTE = "outputPathEntry";
  private static final String JAVADOC_PATH_ENTRY_ATTRIBUTE = "javadocPathEntry";

  public static void execute(Element root) {
    Element rootContComponent = Util.findComponent(root, PROJECT_ROOT_CONTAINER_CLASS);
    if (rootContComponent != null) {
      for (Iterator iterator = rootContComponent.getChildren("root").iterator(); iterator.hasNext();) {
        Element element = (Element)iterator.next();

        String url = element.getAttributeValue("file");
        if (url != null) {
          boolean isJar = url.indexOf("!/") >= 0;
          if (isJar) {
            url = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, url);
          }
          else {
            url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, url);
          }
          element.setAttribute("file", url);
        }
        Element propertyElement = new Element("property");
        element.addContent(propertyElement);
        propertyElement.setAttribute("name", "type");
        propertyElement.setAttribute("value", "projectFiles");
      }

    }
    else {
      rootContComponent = new Element("component");
      root.addContent(rootContComponent);
      rootContComponent.setAttribute("class", PROJECT_ROOT_CONTAINER_CLASS);
    }

    Element vfManComponent = Util.findComponent(root, VIRTUAL_FILE_MANAGER_CLASS);
    if (vfManComponent != null) {
      for (Iterator iterator = vfManComponent.getChildren("fileSystem").iterator(); iterator.hasNext();) {
        Element node = (Element)iterator.next();

        String fileSystemClass = node.getAttributeValue("class");
        boolean isJar = JAR_FILE_SYSTEM_CLASS.equals(fileSystemClass);
        String path = null;
        String rootType = null;

        List children = node.getChildren();
        for (Iterator i = children.iterator(); i.hasNext();) {
          Element node1 = (Element)i.next();

          if ("root".equals(node1.getName())) {
            path = node1.getAttributeValue("path");
          }
          else if ("attribute".equals(node1.getName())) {
            String name = node1.getAttributeValue("name");
            if (SOURCE_PATH_ENTRY_ATTRIBUTE.equals(name)) {
              rootType = "sourcePathEntry";
            }
            else if (CLASS_PATH_ENTRY_ATTRIBUTE.equals(name)) {
              rootType = "classPathEntry";
            }
            else if (OUTPUT_PATH_ENTRY_ATTRIBUTE.equals(name)) {
              rootType = "outputPath";
            }
            else if (JAVADOC_PATH_ENTRY_ATTRIBUTE.equals(name)) {
              rootType = "javadocPathEntry";
            }
            /*
            else if (EXTERNAL_ATTRIBUTE.equals(name)){
              isExternal = true;
            }
            */
          }
        }

        String url;
        if (isJar) {
          path += JarFileSystem.JAR_SEPARATOR;
          url = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path);
        }
        else {
          url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
        }

        Element element = new Element("root");
        rootContComponent.addContent(element);
        element.setAttribute("file", url);

        Element propertyElement = new Element("property");
        element.addContent(propertyElement);
        propertyElement.setAttribute("name", "type");
        if (rootType != null) {
          propertyElement.setAttribute("value", rootType);
        }

        /*
        if (isExternal){
          propertyElement = document.createElement("property");
          element.appendChild(propertyElement);
          propertyElement.setAttribute("name", ProjectRoot.PROP_EXTERNAL);
        }
        */
      }

      root.removeContent(vfManComponent);
    }
  }
}