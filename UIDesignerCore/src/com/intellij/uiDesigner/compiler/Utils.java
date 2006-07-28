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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.*;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 *
 * NOTE: the class must be compilable with JDK 1.3, so any methods and filds introduced in 1.4 or later must not be used
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
  public static String validateJComponentClass(final ClassLoader loader, final String className, final boolean validateConstructor){
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
    catch(ExceptionInInitializerError exc) {
      return "Cannot initialize class " + className + ": " + exc.getMessage();
    }

    if (validateConstructor) {
      try {
        final Constructor constructor = aClass.getConstructor(new Class[0]);
        if ((constructor.getModifiers() & Modifier.PUBLIC) == 0) {
          return "Class \"" + className + "\" does not have default public constructor";
        }
      }
      catch (final Exception exc) {
        return "Class \"" + className + "\" does not have default constructor";
      }
    }

    // Check that JComponent is accessible via the loader

    if(!JComponent.class.isAssignableFrom(aClass)){
      return "Class \"" + className + "\" is not an instance of javax.swing.JComponent";
    }

    return null;
  }

  public static void validateNestedFormLoop(final String formName, final NestedFormLoader nestedFormLoader)
    throws CodeGenerationException, RecursiveFormNestingException {
    HashSet usedFormNames = new HashSet();
    validateNestedFormLoop(usedFormNames, formName, nestedFormLoader);
  }

  private static void validateNestedFormLoop(final Set usedFormNames, final String formName, final NestedFormLoader nestedFormLoader)
    throws CodeGenerationException, RecursiveFormNestingException {
    if (usedFormNames.contains(formName)) {
      throw new RecursiveFormNestingException();
    }
    usedFormNames.add(formName);
    final LwRootContainer rootContainer;
    try {
      rootContainer = nestedFormLoader.loadForm(formName);
    }
    catch (Exception e) {
      throw new CodeGenerationException(null, "Error loading nested form: " + e.getMessage());
    }
    final Set thisFormNestedForms = new HashSet();
    final CodeGenerationException[] validateExceptions = new CodeGenerationException[1];
    final RecursiveFormNestingException[] recursiveNestingExceptions = new RecursiveFormNestingException[1];
    rootContainer.accept(new ComponentVisitor() {
      public boolean visit(final IComponent component) {
        if (component instanceof LwNestedForm) {
          LwNestedForm nestedForm = (LwNestedForm) component;
          if (!thisFormNestedForms.contains(nestedForm.getFormFileName())) {
            thisFormNestedForms.add(nestedForm.getFormFileName());
            try {
              validateNestedFormLoop(usedFormNames, nestedForm.getFormFileName(), nestedFormLoader);
            }
            catch(RecursiveFormNestingException e) {
              recursiveNestingExceptions [0] = e;
              return false;
            }
            catch (CodeGenerationException e) {
              validateExceptions [0] = e;
              return false;
            }
          }
        }
        return true;
      }
    });
    if (recursiveNestingExceptions [0] != null) {
      throw recursiveNestingExceptions [0];
    }
    if (validateExceptions [0] != null) {
      throw validateExceptions [0];
    }
  }

  public static String findNotEmptyPanelWithXYLayout(final IComponent component) {
    if (!(component instanceof IContainer)) {
      return null;
    }
    final IContainer container = (IContainer)component;
    if (container.getComponentCount() == 0){
      return null;
    }
    if (container.isXY()){
      return container.getId();
    }
    for (int i=0; i < container.getComponentCount(); i++){
      String id = findNotEmptyPanelWithXYLayout(container.getComponent(i));
      if (id != null) {
        return id;
      }
    }
    return null;
  }

  public static int getHGap(LayoutManager layout) {
    if (layout instanceof BorderLayout) {
      return ((BorderLayout) layout).getHgap();
    }
    if (layout instanceof CardLayout) {
      return ((CardLayout) layout).getHgap();
    }
    return 0;
  }

  public static int getVGap(LayoutManager layout) {
    if (layout instanceof BorderLayout) {
      return ((BorderLayout) layout).getVgap();
    }
    if (layout instanceof CardLayout) {
      return ((CardLayout) layout).getVgap();
    }
    return 0;
  }

  public static int getCustomCreateComponentCount(final IContainer container) {
    final int[] result = new int[1];
    result [0] = 0;
    container.accept(new ComponentVisitor() {
      public boolean visit(IComponent c) {
        if (c.isCustomCreate()) {
          result [0]++;
        }
        return true;
      }
    });
    return result [0];
  }

  public static Class suggestReplacementClass(Class componentClass) {
    while(true) {
      componentClass = componentClass.getSuperclass();
      if (componentClass.equals(JComponent.class)) {
        return JPanel.class;
      }
      if ((componentClass.getModifiers() & (Modifier.ABSTRACT | Modifier.PRIVATE)) != 0) {
        continue;
      }
      try {
        componentClass.getConstructor(new Class[] {});
      }
      catch(NoSuchMethodException ex) {
        continue;
      }
      return componentClass;
    }
  }

  public static int alignFromConstraints(final GridConstraints gc, final boolean horizontal) {
    int anchor = gc.getAnchor();
    int fill = gc.getFill();
    int leftMask = horizontal ? GridConstraints.ANCHOR_WEST : GridConstraints.ANCHOR_NORTH;
    int rightMask = horizontal ? GridConstraints.ANCHOR_EAST : GridConstraints.ANCHOR_SOUTH;
    int fillMask = horizontal ? GridConstraints.FILL_HORIZONTAL : GridConstraints.FILL_VERTICAL;
    if ((fill & fillMask) != 0) return GridConstraints.ALIGN_FILL;
    if ((anchor & rightMask) != 0) return GridConstraints.ALIGN_RIGHT;
    if ((anchor & leftMask) != 0) return GridConstraints.ALIGN_LEFT;
    return GridConstraints.ALIGN_CENTER;
  }
}
