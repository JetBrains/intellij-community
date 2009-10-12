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

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;

import java.util.Iterator;

/**
 * @author mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class Convertor23 {
  private static final String PROJECT_ROOT_CONTAINER_CLASS = "com.intellij.projectRoots.ProjectRootContainer";

  public static void execute(Element root) {
    Element rootContComponent = Util.findComponent(root, PROJECT_ROOT_CONTAINER_CLASS);
    if (rootContComponent != null) {
      rootContComponent.setAttribute("class", ProjectRootManager.class.getName());
    }
    convertCompilerConfiguration(root);
  }

  private static final String COMPILER_CONFIGURATION_CLASS = "com.intellij.openapi.compiler.Compiler";
  private static final String COMPILER_CONFIGURATION_COMPONENT = "CompilerConfiguration";
  private static final String COMPILER_WORKSPACE_CONFIGURATION_COMPONENT = "CompilerWorkspaceConfiguration";

  private static final String ATTR_CLASS = "class";
  private static final String ATTR_OPTION = "option";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_VALUE = "value";
  private static final String ATTR_PATH = "path";
  private static final String ATTR_URL = "url";
  private static final String ATTR_EXCLUDE_FROM_COMPILE = "excludeFromCompile";

  public static void convertCompilerConfiguration(Element root){
    String compileInBackgroundValue = null;
    Element component = Util.findComponent(root, COMPILER_CONFIGURATION_CLASS);
    if(component != null){
      component.setAttribute(ATTR_NAME, COMPILER_CONFIGURATION_COMPONENT);
      component.removeAttribute(ATTR_CLASS);
      for(Iterator children = component.getChildren().iterator(); children.hasNext();){
        Element element = (Element)children.next();
        String elementName = element.getName();
        if(ATTR_OPTION.equals(elementName)){
          String name = element.getAttributeValue(ATTR_NAME);
          if(name != null){
            if(name.equals("COMPILE_IN_BACKGROUND")){
              compileInBackgroundValue = element.getAttributeValue(ATTR_VALUE);
            }
          }
        }
        else if (ATTR_EXCLUDE_FROM_COMPILE.equals(elementName)){
          for(Iterator excludeIterator = element.getChildren().iterator(); excludeIterator.hasNext();){
            Element excludeElement = (Element)excludeIterator.next();
            String path = excludeElement.getAttributeValue(ATTR_PATH);
            if(path != null){
              String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
              excludeElement.removeAttribute(ATTR_PATH);
              excludeElement.setAttribute(ATTR_URL, url);
            }
          }
        }
      }
    }
    if(compileInBackgroundValue != null){
      component = Util.findComponent(root, COMPILER_WORKSPACE_CONFIGURATION_COMPONENT);
      if(component == null){
        component = new Element("component");
        component.setAttribute(ATTR_NAME, COMPILER_WORKSPACE_CONFIGURATION_COMPONENT);
        root.addContent(component);
      }
      boolean added = false;
      for(Iterator children = component.getChildren().iterator(); children.hasNext();){
        Element element = (Element)children.next();
        String elementName = element.getName();
        if(ATTR_OPTION.equals(elementName)){
          String name = element.getAttributeValue(ATTR_NAME);
          if(name != null){
            if(name.equals("COMPILE_IN_BACKGROUND")){
              element.setAttribute(ATTR_VALUE, compileInBackgroundValue);
              added = true;
            }
          }
        }
      }
      if(!added){
        Element compileInBackgroundElement = new Element(ATTR_OPTION);
        compileInBackgroundElement.setAttribute(ATTR_NAME, "COMPILE_IN_BACKGROUND");
        compileInBackgroundElement.setAttribute(ATTR_VALUE, compileInBackgroundValue);
        component.addContent(compileInBackgroundElement);
      }
    }
  }
}