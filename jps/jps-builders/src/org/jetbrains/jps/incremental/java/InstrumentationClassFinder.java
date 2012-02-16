package org.jetbrains.jps.incremental.java;

import com.intellij.util.lang.ClassPath;
import org.jetbrains.jps.javac.OutputFileObject;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;
import sun.misc.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 2/16/12
 */
class InstrumentationClassFinder {
  private static final PseudoClass[] EMPTY_PSEUDOCLASS_ARRAY = new PseudoClass[0];
  private static final String CLASS_RESOURCE_EXTENSION = ".class";
  private final Map<String, PseudoClass> myLoaded = new HashMap<String, PseudoClass>(); // className -> class object
  private final ClassPath myPlatformClasspath;
  private final ClassPath myClasspath;
  private final OutputFilesSink myCompiled;

  public InstrumentationClassFinder(final URL[] platformClasspath, final URL[] classpath, OutputFilesSink compiled) {
    myCompiled = compiled;
    myPlatformClasspath = new ClassPath(platformClasspath, true, false);
    myClasspath = new ClassPath(classpath, true, false);
  }

  public PseudoClass loadClass(final String internalName) throws IOException, ClassNotFoundException{
    final PseudoClass aClass = myLoaded.get(internalName);
    if (aClass != null) {
      return aClass;
    }
    
    InputStream is = null;
    // first look into platformCp
    final String resourceName = internalName + CLASS_RESOURCE_EXTENSION;
    Resource resource = myPlatformClasspath.getResource(resourceName, false);
    if (resource != null) {
      is = resource.getInputStream();
    }
    // second look into memory and classspath
    if (is == null) {
      final OutputFileObject.Content content = myCompiled.lookupClassBytes(internalName.replace("/", "."));
      if (content != null) {
        is = new ByteArrayInputStream(content.getBuffer(), content.getOffset(), content.getLength());
      }
    }

    if (is == null) {
      resource = myClasspath.getResource(resourceName, false);
      if (resource != null) {
        is = resource.getInputStream();
      }
    }
    
    if (is == null) {
      throw new ClassNotFoundException("Class not found: " + internalName);
    }

    try {
      final PseudoClass result = loadPseudoClass(is);
      myLoaded.put(internalName, result);
      return result;
    }
    finally {
      is.close();
    }
  }

  private PseudoClass loadPseudoClass(InputStream is) throws IOException {
    final ClassReader reader = new ClassReader(is);
    final V visitor = new V();

    reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    return new PseudoClass(visitor.myName, visitor.mySuperclassName, visitor.myInterfaces, visitor.myIsInterface);
  }
  
  public final class PseudoClass {
    private final String myName;
    private final String mySuperClass;
    private final String[] myInterfaces;
    private final boolean isInterface;

    private PseudoClass(final String name, final String superClass, final String[] interfaces, final boolean anInterface) {
      myName = name;
      mySuperClass = superClass;
      myInterfaces = interfaces;
      isInterface = anInterface;
    }

    public PseudoClass getSuperClass() throws IOException, ClassNotFoundException {
      final String superClass = mySuperClass;
      return superClass != null? loadClass(superClass) : null;
    }

    private PseudoClass[] getInterfaces() throws IOException, ClassNotFoundException {
      if (myInterfaces == null) {
        return EMPTY_PSEUDOCLASS_ARRAY;
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

      return getName().equals(((PseudoClass)o).getName());
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
        for (final PseudoClass ti : tis) {
          if (ti.equals(x) || ti.implementsInterface(x)) {
            return true;
          }
        }
      }
      return false;
    }

    public boolean isAssignableFrom(final PseudoClass x) throws IOException, ClassNotFoundException {
      if (this.equals(x)) {
        return true;
      }
      if (x.isSubclassOf(this)) {
        return true;
      }
      if (x.implementsInterface(this)) {
        return true;
      }
      if (x.isInterface() && getName().equals("java/lang/Object")) {
        return true;
      }
      return false;
    }

    public boolean isInterface() {
      return isInterface;
    }

    public String getName() {
      return myName;
    }
  }

  private static class V extends EmptyVisitor {
    public String mySuperclassName = null;
    public String[] myInterfaces = null;
    public String myName = null;
    public boolean myIsInterface = false;

    public void visitAttribute(Attribute attr) {
      super.visitAttribute(attr);
    }

    public void visit(int version, int access, String pName, String signature, String pSuperName, String[] pInterfaces) {
      mySuperclassName = pSuperName;
      myInterfaces = pInterfaces;
      myName = pName;
      myIsInterface = (access & Opcodes.ACC_INTERFACE) > 0;
    }
  }
}
