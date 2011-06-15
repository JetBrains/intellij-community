/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ant;

import com.sun.tools.javac.util.Pair;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 06.06.11
 * Time: 15:02
 * To change this template use File | Settings | File Templates.
 */
public class PseudoClassLoader {
  static final WeakHashMap myCache = new WeakHashMap();

  final Map myDefinedClasses = new HashMap ();

  public class PseudoClass {
    final String myName;
    final String mySuperClass;
    final String[] myInterfaces;
    final boolean isInterface;

    private PseudoClass(final String name, final String superClass, final String[] interfaces, final boolean anInterface) {
      myName = name;
      mySuperClass = superClass;
      myInterfaces = interfaces;
      isInterface = anInterface;
    }

    private PseudoClass getSuperClass() throws IOException, ClassNotFoundException {
      if (mySuperClass == null) {
        return null;
      }

      return loadClass(mySuperClass);
    }

    private PseudoClass[] getInterfaces() throws IOException, ClassNotFoundException {
      if (myInterfaces == null) {
        return new PseudoClass[0];
      }

      final PseudoClass[] result = new PseudoClass[myInterfaces.length];

      for (int i = 0; i < result.length; i++) {
        result[i] = loadClass(myInterfaces[i]);
      }

      return result;
    }

    public boolean equals (final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      return myName.equals(((PseudoClass)o).myName);
    }

    private boolean isSubclassOf(final PseudoClass x) throws IOException, ClassNotFoundException {
      for (PseudoClass c = this; c != null; c = c.getSuperClass()) {
        final PseudoClass superClass = c.getSuperClass();

        if (superClass != null && superClass.equals(x)) {
          return true;
        }
      }

      return false;
    }

    private boolean implementsInterface(final PseudoClass x) throws IOException, ClassNotFoundException {
      for (PseudoClass c = this; c != null; c = c.getSuperClass()) {
        final PseudoClass[] tis = c.getInterfaces();
        for (int i = 0; i < tis.length; ++i) {
          final PseudoClass ti = tis[i];
          if (ti.equals(x) || ti.implementsInterface(x)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isAssignableFrom(final PseudoClass x) throws IOException, ClassNotFoundException {
      if (this.equals(x)) {
        return true;
      }

      if (x.isSubclassOf(this)) {
        return true;
      }

      if (x.implementsInterface(this)) {
        return true;
      }

      if (x.isInterface && myName.equals("java/lang/Object")) {
        return true;
      }

      return false;
    }

    public String getCommonSuperClassName(final PseudoClass x) throws IOException, ClassNotFoundException {
      if (isAssignableFrom(x)) {
        return myName;
      }

      if (x.isAssignableFrom(this)) {
        return x.myName;
      }

      if (isInterface || x.isInterface) {
        return "java/lang/Object";
      }
      else {
        PseudoClass c = this;

        do {
          c = c.getSuperClass();
        }
        while (!c.isAssignableFrom(x));

        return c.myName;
      }
    }
  }

  final URLClassLoader myLoader;

  public PseudoClassLoader(final URL[] classpath) {
    myLoader = new URLClassLoader (classpath, null);
  }

  public ClassLoader getLoader () {
    return myLoader;
  }

  public void defineClass (final String internalName, final byte[] data) {
    myDefinedClasses.put(internalName, createPseudoClass(new ClassReader(data)));
  }

  private static class V extends EmptyVisitor {
    public String superName = null;
    public String[] interfaces = null;
    public String name = null;
    public boolean isInterface = false;

    public void visitAttribute(Attribute attr) {
      super.visitAttribute(attr);
    }

    public void visit(int version, int access, String pName, String signature, String pSuperName, String[] pInterfaces) {
      superName = pSuperName;
      interfaces = pInterfaces;
      name = pName;
      isInterface = (access & Opcodes.ACC_INTERFACE) > 0;
    }
  }

  private PseudoClass createPseudoClass(final ClassReader r) {
    final V visitor = new V();

    r.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    return new PseudoClass(visitor.name, visitor.superName, visitor.interfaces, visitor.isInterface);
  }

  public PseudoClass loadClass(final String internalName) throws IOException, ClassNotFoundException {
    final PseudoClass defined = (PseudoClass) myDefinedClasses.get(internalName);

    if (defined != null) {
      return defined;
    }

    final URL resource = myLoader.findResource(internalName + ".class");

    if (resource == null) {
      throw new ClassNotFoundException(internalName);
    }

    final String fileName = resource.getFile();
    final boolean isFile = fileName.length() > 0;
    final File file = new File(fileName);

    if (isFile) {
      final Pair cached = (Pair)myCache.get(internalName);

      if (cached != null && ((Long)cached.snd).longValue() == file.lastModified()) {
        return (PseudoClass)cached.fst;
      }
    }

    final InputStream content = (InputStream)resource.getContent();
    final ClassReader reader = new ClassReader(content);
    final PseudoClass result = createPseudoClass(reader);

    if (isFile) {
      myCache.put(internalName, new Pair(result, new Long(file.lastModified())));
    }

    return result;
  }
}
