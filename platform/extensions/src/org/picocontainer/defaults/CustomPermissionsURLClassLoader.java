/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 *****************************************************************************/

package org.picocontainer.defaults;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.util.Map;

/**
 * CustomPermissionsURLClassLoader extends URLClassLoader, adding the abilty to programatically add permissions easily.
 * To be effective for permission management, it should be run in conjunction with a policy that restricts
 * some of the classloaders, but not all.
 * It's not ordinarily used by PicoContainer, but is here because PicoContainer is common
 * to most classloader trees.
 *
 * @author Paul Hammant
 */
public class CustomPermissionsURLClassLoader extends URLClassLoader {
  private final Map permissionsMap;

  public CustomPermissionsURLClassLoader(URL[] urls, Map permissionsMap, ClassLoader parent) {
    super(urls, parent);
    this.permissionsMap = permissionsMap;
  }

  @Override
  public Class loadClass(String name) throws ClassNotFoundException {
    try {
      return super.loadClass(name);
    }
    catch (ClassNotFoundException e) {
      throw decorateException(name, e);
    }
  }

  @Override
  protected Class findClass(String name) throws ClassNotFoundException {
    try {
      return super.findClass(name);
    }
    catch (ClassNotFoundException e) {
      throw decorateException(name, e);
    }
  }

  private ClassNotFoundException decorateException(String name, ClassNotFoundException e) {
    if (name.startsWith("class ")) {
      return new ClassNotFoundException("Class '" + name + "' is not a classInstance.getName(). " +
                                        "It's a classInstance.toString(). The clue is that it starts with 'class ', no classname contains a space.");
    }
    ClassLoader classLoader = this;
    StringBuilder sb = new StringBuilder("'").append(name).append("' classloader stack [");
    while (classLoader != null) {
      sb.append(classLoader.toString()).append("\n");
      final ClassLoader cl = classLoader;
      classLoader = (ClassLoader)AccessController.doPrivileged(new PrivilegedAction() {
        @Override
        public Object run() {
          return cl.getParent();
        }
      });
    }
    return new ClassNotFoundException(sb.append("]").toString(), e);
  }

  public String toString() {
    StringBuilder result = new StringBuilder(CustomPermissionsURLClassLoader.class.getName() + " " + System.identityHashCode(this) + ":");
    URL[] urls = getURLs();
    for (URL url : urls) {
      result.append("\n\t").append(url.toString());
    }

    return result.toString();
  }

  @Override
  public PermissionCollection getPermissions(CodeSource codeSource) {
    return (Permissions)permissionsMap.get(codeSource.getLocation());
  }
}

