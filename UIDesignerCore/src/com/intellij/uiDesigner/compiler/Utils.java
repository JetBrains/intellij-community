/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.PropertiesProvider;
import org.jdom.input.SAXBuilder;
import org.jdom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 *
 * NOTE: the class must be compilable with JDK 1.3, so any methods and filds introduced in 1.4 or later must not be used
 *
 */
public final class Utils {
  public static final String FORM_NAMESPACE = "http://www.intellij.com/uidesigner/form/";
  private static final SAXParser SAX_PARSER = createParser();
  private static final SAXBuilder SAX_BUILDER = new SAXBuilder();

  private Utils() {
  }

  private static SAXParser createParser() {
    try {
      return SAXParserFactory.newInstance().newSAXParser();
    }
    catch (Exception e) {
      return null;
    }
  }

  /**
   * @param provider if null, no classes loaded and no properties read
   */
  public static LwRootContainer getRootContainer(final String formFileContent, final PropertiesProvider provider) throws Exception{
    if (formFileContent.indexOf(FORM_NAMESPACE) == -1) {
      throw new AlienFormFileException();
    }

    final Document document = SAX_BUILDER.build(new StringReader(formFileContent), "UTF-8");

    final LwRootContainer root = new LwRootContainer();
    root.read(document.getRootElement(), provider);

    return root;
  }

  public static LwRootContainer getRootContainer(final InputStream stream, final PropertiesProvider provider) throws Exception {
    final Document document = SAX_BUILDER.build(stream, "UTF-8");

    final LwRootContainer root = new LwRootContainer();
    root.read(document.getRootElement(), provider);

    return root;
  }

  public synchronized static String getBoundClassName(final String formFileContent) throws Exception {
    if (formFileContent.indexOf(FORM_NAMESPACE) == -1) {
      throw new AlienFormFileException();
    }

    final String[] className = new String[] {null};
    try {
      SAX_PARSER.parse(new InputSource(new StringReader(formFileContent)), new DefaultHandler() {
        public void startElement(String uri,
                                 String localName,
                                 String qName,
                                 Attributes attributes)
          throws SAXException {
          if ("form".equals(qName)) {
            className[0] = attributes.getValue("", "bind-to-class");
            throw new SAXException("stop parsing");
          }
        }
      });
    }
    catch (Exception e) {
      // Do nothing.
    }

    return className[0];
  }

  /**
   * Validates that specified class represents {@link javax.swing.JComponent} with
   * empty constructor.
   *
   * @return descriptive human readable error message or <code>null</code> if
   * no errors were detected.
   */
  public static String validateJComponentClass(final ClassLoader loader, final String className){
    if(loader == null){
      throw new IllegalArgumentException("loader cannot be null");
    }
    if(className == null){
      throw new IllegalArgumentException("className cannot be null");
    }

    // These classes are not visible for passed class loader!
    if(
      "com.intellij.uiDesigner.HSpacer".equals(className) ||
      "com.intellij.uiDesigner.VSpacer".equals(className)
    ){
      return null;
    }

    final Class aClass;
    try {
      aClass = Class.forName(className, true, loader);
    }
    catch (final ClassNotFoundException exc) {
      return "Class \"" + className + "\"not found";
    }
    catch (NoClassDefFoundError exc) {
      return "Cannot load class " + className + ": " + exc.getMessage();
    }

    try {
      final Constructor constructor = aClass.getConstructor(new Class[0]);
      if ((constructor.getModifiers() & Modifier.PUBLIC) == 0) {
        return "Class \"" + className + "\" does not have default public constructor";
      }
    }
    catch (final Exception exc) {
      return "Class \"" + className + "\" does not have default constructor";
    }

    // Check that JComponent is accessible via the loader

    if(!JComponent.class.isAssignableFrom(aClass)){
      return "Class \"" + className + "\" is not an instance of javax.swing.JComponent";
    }

    return null;
  }

}
