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
package com.intellij.uiDesigner.core;

import javax.swing.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ResourceBundle;
import java.util.Locale;

/**
 * @author Vladimir Kondratyev
 */
public final class SupportCode {
  private static final Method ourGetCallerClassLoaderMethod;
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[]{};
  static {
    Method method;
    try {
      method = ClassLoader.class.getDeclaredMethod("getCallerClassLoader", new Class[]{});
      method.setAccessible(true);
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
      method = null;
    }
    ourGetCallerClassLoaderMethod = method;
  }
  /**
   * Parses text that might contain mnemonic and returns structure which contains
   * plain text and index of mnemonic char (if any)
   */
  public static TextWithMnemonic parseText(final String textWithMnemonic){
    if(textWithMnemonic == null){
      throw new IllegalArgumentException("textWithMnemonic cannot be null");
    }
    // Parsing is copied from Presentation.setText(String, boolean)
    int index = -1;
    final StringBuffer plainText = new StringBuffer();
    for(int i = 0; i < textWithMnemonic.length(); i++){
      char ch = textWithMnemonic.charAt(i);
      if (ch == '&'){
        i++;
        if (i >= textWithMnemonic.length()){
          break;
        }
        ch = textWithMnemonic.charAt(i);
        if (ch != '&'){
          index = plainText.length();
        }
      }
      plainText.append(ch);
    }

    return new TextWithMnemonic(plainText.toString(), index);
  }

  public static final class TextWithMnemonic{
    /**
     * Plain text
     */
    public final String myText;
    /**
     * Index of mnemonic char. -1 means that text doesn't contain mnemonic char
     */
    public final int myMnemonicIndex;

    private TextWithMnemonic(final String text, final int index){
      if(text == null){
        throw new IllegalArgumentException("text cannot be null");
      }
      if(index != -1 && (index < 0 || index >= text.length() )){
        throw new IllegalArgumentException("wrong index: " + index + "; text = '" + text + "'");
      }
      myText = text;
      myMnemonicIndex = index;
    }

    public char getMnemonicChar(){
      if(myMnemonicIndex == -1){
        throw new IllegalStateException("text doesn't contain mnemonic");
      }
      return Character.toUpperCase(myText.charAt(myMnemonicIndex));
    }
  }

  /**
   * Called in generated code
   * Because this method is sensitive to the caller's class loader, it must be called from generated code only
   */
  public static void setTextFromBundle(final JComponent component, final String bundleName, final String key) {
    if(component == null){
      throw new IllegalArgumentException("component cannot be null");
    }
    if(bundleName == null){
      throw new IllegalArgumentException("bundleName cannot be null");
    }
    if(key == null){
      throw new IllegalArgumentException("key cannot be null");
    }

    ClassLoader loader = null;
    try {
      loader = ourGetCallerClassLoaderMethod != null? (ClassLoader)ourGetCallerClassLoaderMethod.invoke(null, EMPTY_OBJECT_ARRAY) : null;
    }
    catch (IllegalAccessException e) {
      e.printStackTrace(); // should not happen
    }
    catch (InvocationTargetException e) {
      e.printStackTrace();
    }

    final String text = getResourceString(bundleName, key, loader);

    final TextWithMnemonic textWithMnemonic = SupportCode.parseText(text);

    if (component instanceof JLabel) {
      final JLabel label = (JLabel)component;

      label.setText(textWithMnemonic.myText);
      if (textWithMnemonic.myMnemonicIndex != -1) {
        label.setDisplayedMnemonic(textWithMnemonic.getMnemonicChar());
        setDisplayedMnemonicIndex(label, textWithMnemonic.myMnemonicIndex);
      }
    }
    else if (component instanceof AbstractButton) {
      final AbstractButton button = (AbstractButton)component;

      button.setText(textWithMnemonic.myText);
      if (textWithMnemonic.myMnemonicIndex != -1) {
        button.setMnemonic(textWithMnemonic.getMnemonicChar());
        setDisplayedMnemonicIndex(button, textWithMnemonic.myMnemonicIndex);
      }
    }
    else {
      throw new IllegalArgumentException("unexpected class: " + component.getClass());
    }
  }

  /**
   * Called in generated code
   * Because this method is sensitive to the caller's class loader, it must be called from generated code only
   */
  public static void setTextFromBundle(final JComponent component, final String setterName, final String bundle, final String key) {
    if(component == null){
      throw new IllegalArgumentException("component cannot be null");
    }
    if (setterName == null){
      throw new IllegalArgumentException("setterName cannot be null");
    }
    if(bundle == null){
      throw new IllegalArgumentException("bundle cannot be null");
    }
    if(key == null){
      throw new IllegalArgumentException("key cannot be null");
    }

    ClassLoader loader = null;
    try {
      loader = ourGetCallerClassLoaderMethod != null? (ClassLoader)ourGetCallerClassLoaderMethod.invoke(null, EMPTY_OBJECT_ARRAY) : null;
    }
    catch (IllegalAccessException e1) {
      e1.printStackTrace(); // should not happen
    }
    catch (InvocationTargetException e11) {
      e11.printStackTrace();
    }

    final String text = getResourceString(bundle, key, loader);

    final TextWithMnemonic textWithMnemonic = SupportCode.parseText(text);

    try {
      final Method setter = component.getClass().getMethod(setterName, new Class[]{String.class});
      setter.invoke(component, new Object[]{textWithMnemonic.myText});
    }
    catch (Exception e) {
      throw new RuntimeException(e.toString()); // [anton] we cannot use RuntimeException(Exception) constructor for JDK 1.3 compatibility
    }

    if (textWithMnemonic.myMnemonicIndex != -1) {
      if (setterName.equals("setText")) {
        if (component instanceof JLabel) {
          final JLabel label = (JLabel)component;

          label.setDisplayedMnemonic(textWithMnemonic.getMnemonicChar());
          setDisplayedMnemonicIndex(label, textWithMnemonic.myMnemonicIndex);
        }
        else if (component instanceof AbstractButton) {
          final AbstractButton button = (AbstractButton)component;

          button.setMnemonic(textWithMnemonic.getMnemonicChar());
          setDisplayedMnemonicIndex(button, textWithMnemonic.myMnemonicIndex);
        }
      }
    }
  }

  /**
   * For JDK 1.3 compatibility.
   * Used in generated byte code.
   */
  public static void setDisplayedMnemonicIndex(JComponent component, int index) {
    try {
      Method method = component.getClass().getMethod("setDisplayedMnemonicIndex", new Class[]{int.class});
      method.setAccessible(true);
      method.invoke(component, new Object[]{new Integer(index)});
    }
    catch (Exception e) {
      // JDK earlier than 1.4 - do nothing
    }
  }

  /**
   * Called in generated code
   * Because this method is sensitive to the caller's class loader, it must be called from generated code only
   */
  public static String getResourceString(final String bundleName, final String key) {
    ClassLoader loader = null;
    try {
      loader = ourGetCallerClassLoaderMethod != null? (ClassLoader)ourGetCallerClassLoaderMethod.invoke(null, EMPTY_OBJECT_ARRAY) : null;
    }
    catch (IllegalAccessException e) {
      e.printStackTrace(); // should not happen
    }
    catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    return getResourceString(bundleName, key, loader);
  }

  private static String getResourceString(final String bundleName, final String key, final ClassLoader callerClassLoader) {
    final ResourceBundle bundle = callerClassLoader != null?
                                  ResourceBundle.getBundle(bundleName, Locale.getDefault(), callerClassLoader) :
                                  ResourceBundle.getBundle(bundleName, Locale.getDefault());
    return bundle.getString(key);
  }
}
