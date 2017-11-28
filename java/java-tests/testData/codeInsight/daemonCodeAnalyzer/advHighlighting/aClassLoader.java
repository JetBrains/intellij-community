/* ClassLoader.java -- responsible for loading classes into the VM
   Copyright (C) 1998, 1999, 2001, 2002, 2003, 2004, 2005 Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */


package java.lang;

import gnu.classpath.SystemProperties;
import gnu.classpath.VMStackWalker;
import gnu.java.util.DoubleEnumeration;
import gnu.java.util.EmptyEnumeration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * The ClassLoader is a way of customizing the way Java gets its classes
 * and loads them into memory.  The verifier and other standard Java things
 * still run, but the ClassLoader is allowed great flexibility in determining
 * where to get the classfiles and when to load and resolve them. For that
 * matter, a custom ClassLoader can perform on-the-fly code generation or
 * modification!
 *
 * <p>Every classloader has a parent classloader that is consulted before
 * the 'child' classloader when classes or resources should be loaded.
 * This is done to make sure that classes can be loaded from an hierarchy of
 * multiple classloaders and classloaders do not accidentially redefine
 * already loaded classes by classloaders higher in the hierarchy.
 *
 * <p>The grandparent of all classloaders is the bootstrap classloader, which
 * loads all the standard system classes as implemented by GNU Classpath. The
 * other special classloader is the system classloader (also called
 * application classloader) that loads all classes from the CLASSPATH
 * (<code>java.class.path</code> system property). The system classloader
 * is responsible for finding the application classes from the classpath,
 * and delegates all requests for the standard library classes to its parent
 * the bootstrap classloader. Most programs will load all their classes
 * through the system classloaders.
 *
 * <p>The bootstrap classloader in GNU Classpath is implemented as a couple of
 * static (native) methods on the package private class
 * <code>java.lang.VMClassLoader</code>, the system classloader is an
 * anonymous inner class of ClassLoader and a subclass of
 * <code>java.net.URLClassLoader</code>.
 *
 * <p>Users of a <code>ClassLoader</code> will normally just use the methods
 * <ul>
 *  <li> <code>loadClass()</code> to load a class.</li>
 *  <li> <code>getResource()</code> or <code>getResourceAsStream()</code>
 *       to access a resource.</li>
 *  <li> <code>getResources()</code> to get an Enumeration of URLs to all
 *       the resources provided by the classloader and its parents with the
 *       same name.</li>
 * </ul>
 *
 * <p>Subclasses should implement the methods
 * <ul>
 *  <li> <code>findClass()</code> which is called by <code>loadClass()</code>
 *       when the parent classloader cannot provide a named class.</li>
 *  <li> <code>findResource()</code> which is called by
 *       <code>getResource()</code> when the parent classloader cannot provide
 *       a named resource.</li>
 *  <li> <code>findResources()</code> which is called by
 *       <code>getResource()</code> to combine all the resources with the
 *       same name from the classloader and its parents.</li>
 *  <li> <code>findLibrary()</code> which is called by
 *       <code>Runtime.loadLibrary()</code> when a class defined by the
 *       classloader wants to load a native library.</li>
 * </ul>
 *
 * @author John Keiser
 * @author Mark Wielaard
 * @author Eric Blake (ebb9@email.byu.edu)
 * @see Class
 * @since 1.0
 * @status still missing 1.4 functionality
 */
public abstract class ClassLoader
{
  /**
   * All classes loaded by this classloader. VM's may choose to implement
   * this cache natively; but it is here available for use if necessary. It
   * is not private in order to allow native code (and trusted subclasses)
   * access to this field.
   */
  final HashMap loadedClasses = new HashMap();

  /**
   * All packages defined by this classloader. It is not private in order to
   * allow native code (and trusted subclasses) access to this field.
   */
  final HashMap definedPackages = new HashMap();

  /**
   * The classloader that is consulted before this classloader.
   * If null then the parent is the bootstrap classloader.
   */
  private final ClassLoader parent;

  /**
   * This is true if this classloader was successfully initialized.
   * This flag is needed to avoid a class loader attack: even if the
   * security manager rejects an attempt to create a class loader, the
   * malicious class could have a finalize method which proceeds to
   * define classes.
   */
  private final boolean initialized;

  static class StaticData
  {
    /**
     * The System Class Loader (a.k.a. Application Class Loader). The one
     * returned by ClassLoader.getSystemClassLoader.
     */
    static final ClassLoader systemClassLoader =
                              VMClassLoader.getSystemClassLoader();
    static
    {
      // Find out if we have to install a default security manager. Note that
      // this is done here because we potentially need the system class loader
      // to load the security manager and note also that we don't need the
      // security manager until the system class loader is created.
      // If the runtime chooses to use a class loader that doesn't have the
      // system class loader as its parent, it is responsible for setting
      // up a security manager before doing so.
      String secman = SystemProperties.getProperty("java.security.manager");
      if (secman != null && SecurityManager.current == null)
        {
          if (secman.equals("") || secman.equals("default"))
	    {
	      SecurityManager.current = new SecurityManager();
	    }
	  else
	    {
	      try
	        {
	  	  Class cl = Class.forName(secman, false, StaticData.systemClassLoader);
		  SecurityManager.current = (SecurityManager)cl.newInstance();
	        }
	      catch (Exception x)
	        {
		  throw (InternalError)
		      new InternalError("Unable to create SecurityManager")
		  	  .initCause(x);
	        }
	    }
        }
    }

    /**
     * The default protection domain, used when defining a class with a null
     * parameter for the domain.
     */
    static final ProtectionDomain defaultProtectionDomain;
    static
    {
        CodeSource cs = new CodeSource(null, null);
        PermissionCollection perm = Policy.getPolicy().getPermissions(cs);
        defaultProtectionDomain = new ProtectionDomain(cs, perm);
    }
    /**
     * The command-line state of the package assertion status overrides. This
     * map is never modified, so it does not need to be synchronized.
     */
    // Package visible for use by Class.
    static final Map systemPackageAssertionStatus
      = VMClassLoader.packageAssertionStatus();
    /**
     * The command-line state of the class assertion status overrides. This
     * map is never modified, so it does not need to be synchronized.
     */
    // Package visible for use by Class.
    static final Map systemClassAssertionStatus
      = VMClassLoader.classAssertionStatus();
  }

  /**
   * The desired assertion status of classes loaded by this loader, if not
   * overridden by package or class instructions.
   */
  // Package visible for use by Class.
  boolean defaultAssertionStatus = VMClassLoader.defaultAssertionStatus();

  /**
   * The map of package assertion status overrides, or null if no package
   * overrides have been specified yet. The values of the map should be
   * Boolean.TRUE or Boolean.FALSE, and the unnamed package is represented
   * by the null key. This map must be synchronized on this instance.
   */
  // Package visible for use by Class.
  Map packageAssertionStatus;

  /**
   * The map of class assertion status overrides, or null if no class
   * overrides have been specified yet. The values of the map should be
   * Boolean.TRUE or Boolean.FALSE. This map must be synchronized on this
   * instance.
   */
  // Package visible for use by Class.
  Map classAssertionStatus;

  /**
   * VM private data.
   */
  transient Object vmdata;

  /**
   * Create a new ClassLoader with as parent the system classloader. There
   * may be a security check for <code>checkCreateClassLoader</code>.
   *
   * @throws SecurityException if the security check fails
   */
  protected ClassLoader() throws SecurityException
  {
    this(StaticData.systemClassLoader);
  }

  /**
   * Create a new ClassLoader with the specified parent. The parent will
   * be consulted when a class or resource is requested through
   * <code>loadClass()</code> or <code>getResource()</code>. Only when the
   * parent classloader cannot provide the requested class or resource the
   * <code>findClass()</code> or <code>findResource()</code> method
   * of this classloader will be called. There may be a security check for
   * <code>checkCreateClassLoader</code>.
   *
   * @param parent the classloader's parent, or null for the bootstrap
   *        classloader
   * @throws SecurityException if the security check fails
   * @since 1.2
   */
  protected ClassLoader(ClassLoader parent)
  {
    // May we create a new classloader?
    SecurityManager sm = SecurityManager.current;
    if (sm != null)
      sm.checkCreateClassLoader();
    this.parent = parent;
    this.initialized = true;
  }

  /**
   * Load a class using this ClassLoader or its parent, without resolving
   * it. Calls <code>loadClass(name, false)</code>.
   *
   * <p>Subclasses should not override this method but should override
   * <code>findClass()</code> which is called by this method.</p>
   *
   * @param name the name of the class relative to this ClassLoader
   * @return the loaded class
   * @throws ClassNotFoundException if the class cannot be found
   */
  public Class loadClass(String name) throws ClassNotFoundException
  {
    return loadClass(name, false);
  }

  /**
   * Load a class using this ClassLoader or its parent, possibly resolving
   * it as well using <code>resolveClass()</code>. It first tries to find
   * out if the class has already been loaded through this classloader by
   * calling <code>findLoadedClass()</code>. Then it calls
   * <code>loadClass()</code> on the parent classloader (or when there is
   * no parent it uses the VM bootclassloader). If the class is still
   * not loaded it tries to create a new class by calling
   * <code>findClass()</code>. Finally when <code>resolve</code> is
   * <code>true</code> it also calls <code>resolveClass()</code> on the
   * newly loaded class.
   *
   * <p>Subclasses should not override this method but should override
   * <code>findClass()</code> which is called by this method.</p>
   *
   * @param name the fully qualified name of the class to load
   * @param resolve whether or not to resolve the class
   * @return the loaded class
   * @throws ClassNotFoundException if the class cannot be found
   */
  protected synchronized Class loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
    // Have we already loaded this class?
    Class c = findLoadedClass(name);
    if (c == null)
      {
	// Can the class be loaded by a parent?
	try
	  {
	    if (parent == null)
	      {
		c = VMClassLoader.loadClass(name, resolve);
		if (c != null)
		  return c;
	      }
	    else
	      {
		return parent.loadClass(name, resolve);
	      }
	  }
	catch (ClassNotFoundException e)
	  {
	  }
	// Still not found, we have to do it ourself.
	c = findClass(name);
      }
    if (resolve)
      resolveClass(c);
    return c;
  }

  /**
   * Called for every class name that is needed but has not yet been
   * defined by this classloader or one of its parents. It is called by
   * <code>loadClass()</code> after both <code>findLoadedClass()</code> and
   * <code>parent.loadClass()</code> couldn't provide the requested class.
   *
   * <p>The default implementation throws a
   * <code>ClassNotFoundException</code>. Subclasses should override this
   * method. An implementation of this method in a subclass should get the
   * class bytes of the class (if it can find them), if the package of the
   * requested class doesn't exist it should define the package and finally
   * it should call define the actual class. It does not have to resolve the
   * class. It should look something like the following:<br>
   *
   * <pre>
   * // Get the bytes that describe the requested class
   * byte[] classBytes = classLoaderSpecificWayToFindClassBytes(name);
   * // Get the package name
   * int lastDot = name.lastIndexOf('.');
   * if (lastDot != -1)
   *   {
   *     String packageName = name.substring(0, lastDot);
   *     // Look if the package already exists
   *     if (getPackage(packageName) == null)
   *       {
   *         // define the package
   *         definePackage(packageName, ...);
   *       }
   *   }
   * // Define and return the class
   *  return defineClass(name, classBytes, 0, classBytes.length);
   * </pre>
   *
   * <p><code>loadClass()</code> makes sure that the <code>Class</code>
   * returned by <code>findClass()</code> will later be returned by
   * <code>findLoadedClass()</code> when the same class name is requested.
   *
   * @param name class name to find (including the package name)
   * @return the requested Class
   * @throws ClassNotFoundException when the class can not be found
   * @since 1.2
   */
  protected Class findClass(String name) throws ClassNotFoundException
  {
    throw new ClassNotFoundException(name);
  }

  /**
   * Helper to define a class using a string of bytes. This version is not
   * secure.
   *
   * @param data the data representing the classfile, in classfile format
   * @param offset the offset into the data where the classfile starts
   * @param len the length of the classfile data in the array
   * @return the class that was defined
   * @throws ClassFormatError if data is not in proper classfile format
   * @throws IndexOutOfBoundsException if offset or len is negative, or
   *         offset + len exceeds data
   * @deprecated use {@link #defineClass(String, byte[], int, int)} instead
   */
  protected final Class defineClass(byte[] data, int offset, int len)
    throws ClassFormatError
  {
    return defineClass(null, data, offset, len);
  }

  /**
   * Helper to define a class using a string of bytes without a
   * ProtectionDomain. Subclasses should call this method from their
   * <code>findClass()</code> implementation. The name should use '.'
   * separators, and discard the trailing ".class".  The default protection
   * domain has the permissions of
   * <code>Policy.getPolicy().getPermissions(new CodeSource(null, null))</code>.
   *
   * @param name the name to give the class, or null if unknown
   * @param data the data representing the classfile, in classfile format
   * @param offset the offset into the data where the classfile starts
   * @param len the length of the classfile data in the array
   * @return the class that was defined
   * @throws ClassFormatError if data is not in proper classfile format
   * @throws IndexOutOfBoundsException if offset or len is negative, or
   *         offset + len exceeds data
   * @throws SecurityException if name starts with "java."
   * @since 1.1
   */
  protected final Class defineClass(String name, byte[] data, int offset,
                                    int len) throws ClassFormatError
  {
    return defineClass(name, data, offset, len, null);
  }

  /**
   * Helper to define a class using a string of bytes. Subclasses should call
   * this method from their <code>findClass()</code> implementation. If the
   * domain is null, the default of
   * <code>Policy.getPolicy().getPermissions(new CodeSource(null, null))</code>
   * is used. Once a class has been defined in a package, all further classes
   * in that package must have the same set of certificates or a
   * SecurityException is thrown.
   *
   * @param name the name to give the class.  null if unknown
   * @param data the data representing the classfile, in classfile format
   * @param offset the offset into the data where the classfile starts
   * @param len the length of the classfile data in the array
   * @param domain the ProtectionDomain to give to the class, null for the
   *        default protection domain
   * @return the class that was defined
   * @throws ClassFormatError if data is not in proper classfile format
   * @throws IndexOutOfBoundsException if offset or len is negative, or
   *         offset + len exceeds data
   * @throws SecurityException if name starts with "java.", or if certificates
   *         do not match up
   * @since 1.2
   */
  protected final synchronized Class defineClass(String name, byte[] data,
						 int offset, int len,
						 ProtectionDomain domain)
    throws ClassFormatError
  {
    if (domain == null)
      domain = StaticData.defaultProtectionDomain;
    if (! initialized)
      throw new SecurityException("attempt to define class from uninitialized class loader");
    
    Class retval = VMClassLoader.defineClass(this, name, data,
					     offset, len, domain);
    loadedClasses.put(retval.getName(), retval);
    return retval;
  }

  /**
   * Links the class, if that has not already been done. Linking basically
   * resolves all references to other classes made by this class.
   *
   * @param c the class to resolve
   * @throws NullPointerException if c is null
   * @throws LinkageError if linking fails
   */
  protected final void resolveClass(Class c)
  {
    VMClassLoader.resolveClass(c);
  }

  /**
   * Helper to find a Class using the system classloader, possibly loading it.
   * A subclass usually does not need to call this, if it correctly
   * overrides <code>findClass(String)</code>.
   *
   * @param name the name of the class to find
   * @return the found class
   * @throws ClassNotFoundException if the class cannot be found
   */
  protected final Class findSystemClass(String name)
    throws ClassNotFoundException
  {
    return Class.forName(name, false, StaticData.systemClassLoader);
  }

  /**
   * Returns the parent of this classloader. If the parent of this
   * classloader is the bootstrap classloader then this method returns
   * <code>null</code>. A security check may be performed on
   * <code>RuntimePermission("getClassLoader")</code>.
   *
   * @return the parent <code>ClassLoader</code>
   * @throws SecurityException if the security check fails
   * @since 1.2
   */
  public final ClassLoader getParent()
  {
    // Check if we may return the parent classloader.
    SecurityManager sm = SecurityManager.current;
    if (sm != null)
      {
	ClassLoader cl = VMStackWalker.getCallingClassLoader();
	if (cl != null && ! cl.isAncestorOf(this))
          sm.checkPermission(new RuntimePermission("getClassLoader"));
      }
    return parent;
  }

  /**
   * Helper to set the signers of a class. This should be called after
   * defining the class.
   *
   * @param c the Class to set signers of
   * @param signers the signers to set
   * @since 1.1
   */
  protected final void setSigners(Class c, Object[] signers)
  {
    c.setSigners(signers);
  }

  /**
   * Helper to find an already-loaded class in this ClassLoader.
   *
   * @param name the name of the class to find
   * @return the found Class, or null if it is not found
   * @since 1.1
   */
  protected final synchronized Class findLoadedClass(String name)
  {
    // NOTE: If the VM is keeping its own cache, it may make sense to have
    // this method be native.
    return (Class) loadedClasses.get(name);
  }

  /**
   * Get the URL to a resource using this classloader or one of its parents.
   * First tries to get the resource by calling <code>getResource()</code>
   * on the parent classloader. If the parent classloader returns null then
   * it tries finding the resource by calling <code>findResource()</code> on
   * this classloader. The resource name should be separated by '/' for path
   * elements.
   *
   * <p>Subclasses should not override this method but should override
   * <code>findResource()</code> which is called by this method.
   *
   * @param name the name of the resource relative to this classloader
   * @return the URL to the resource or null when not found
   */
  public URL getResource(String name)
  {
    URL result;

    if (parent == null)
      result = VMClassLoader.getResource(name);
    else
      result = parent.getResource(name);

    if (result == null)
      result = findResource(name);
    return result;
  }

  /**
   * Returns an Enumeration of all resources with a given name that can
   * be found by this classloader and its parents. Certain classloaders
   * (such as the URLClassLoader when given multiple jar files) can have
   * multiple resources with the same name that come from multiple locations.
   * It can also occur that a parent classloader offers a resource with a
   * certain name and the child classloader also offers a resource with that
   * same name. <code>getResource()</code> only offers the first resource (of the
   * parent) with a given name. This method lists all resources with the
   * same name. The name should use '/' as path separators.
   *
   * <p>The Enumeration is created by first calling <code>getResources()</code>
   * on the parent classloader and then calling <code>findResources()</code>
   * on this classloader.</p>
   *
   * @param name the resource name
   * @return an enumaration of all resources found
   * @throws IOException if I/O errors occur in the process
   * @since 1.2
   */
  public final Enumeration getResources(String name) throws IOException
  {
    Enumeration parentResources;
    if (parent == null)
      parentResources = VMClassLoader.getResources(name);
    else
      parentResources = parent.getResources(name);
    return new DoubleEnumeration(parentResources, findResources(name));
  }

  /**
   * Called whenever all locations of a named resource are needed.
   * It is called by <code>getResources()</code> after it has called
   * <code>parent.getResources()</code>. The results are combined by
   * the <code>getResources()</code> method.
   *
   * <p>The default implementation always returns an empty Enumeration.
   * Subclasses should override it when they can provide an Enumeration of
   * URLs (possibly just one element) to the named resource.
   * The first URL of the Enumeration should be the same as the one
   * returned by <code>findResource</code>.
   *
   * @param name the name of the resource to be found
   * @return a possibly empty Enumeration of URLs to the named resource
   * @throws IOException if I/O errors occur in the process
   * @since 1.2
   */
  protected Enumeration findResources(String name) throws IOException
  {
    return EmptyEnumeration.getInstance();
  }

  /**
   * Called whenever a resource is needed that could not be provided by
   * one of the parents of this classloader. It is called by
   * <code>getResource()</code> after <code>parent.getResource()</code>
   * couldn't provide the requested resource.
   *
   * <p>The default implementation always returns null. Subclasses should
   * override this method when they can provide a way to return a URL
   * to a named resource.
   *
   * @param name the name of the resource to be found
   * @return a URL to the named resource or null when not found
   * @since 1.2
   */
  protected URL findResource(String name)
  {
    return null;
  }

  /**
   * Get the URL to a resource using the system classloader.
   *
   * @param name the name of the resource relative to the system classloader
   * @return the URL to the resource
   * @since 1.1
   */
  public static final URL getSystemResource(String name)
  {
    return StaticData.systemClassLoader.getResource(name);
  }

  /**
   * Get an Enumeration of URLs to resources with a given name using the
   * the system classloader. The enumeration firsts lists the resources with
   * the given name that can be found by the bootstrap classloader followed
   * by the resources with the given name that can be found on the classpath.
   *
   * @param name the name of the resource relative to the system classloader
   * @return an Enumeration of URLs to the resources
   * @throws IOException if I/O errors occur in the process
   * @since 1.2
   */
  public static Enumeration getSystemResources(String name) throws IOException
  {
    return StaticData.systemClassLoader.getResources(name);
  }

  /**
   * Get a resource as stream using this classloader or one of its parents.
   * First calls <code>getResource()</code> and if that returns a URL to
   * the resource then it calls and returns the InputStream given by
   * <code>URL.openStream()</code>.
   *
   * <p>Subclasses should not override this method but should override
   * <code>findResource()</code> which is called by this method.
   *
   * @param name the name of the resource relative to this classloader
   * @return an InputStream to the resource, or null
   * @since 1.1
   */
  public InputStream getResourceAsStream(String name)
  {
    try
      {
        URL url = getResource(name);
        if (url == null)
          return null;
        return url.openStream();
      }
    catch (IOException e)
      {
        return null;
      }
  }

  /**
   * Get a resource using the system classloader.
   *
   * @param name the name of the resource relative to the system classloader
   * @return an input stream for the resource, or null
   * @since 1.1
   */
  public static final InputStream getSystemResourceAsStream(String name)
  {
    try
      {
        URL url = getSystemResource(name);
        if (url == null)
          return null;
        return url.openStream();
      }
    catch (IOException e)
      {
        return null;
      }
  }

  /**
   * Returns the system classloader. The system classloader (also called
   * the application classloader) is the classloader that is used to
   * load the application classes on the classpath (given by the system
   * property <code>java.class.path</code>. This is set as the context
   * class loader for a thread. The system property
   * <code>java.system.class.loader</code>, if defined, is taken to be the
   * name of the class to use as the system class loader, which must have
   * a public constructor which takes a ClassLoader as a parent. The parent
   * class loader passed in the constructor is the default system class
   * loader.
   *
   * <p>Note that this is different from the bootstrap classloader that
   * actually loads all the real "system" classes.
   *
   * <p>A security check will be performed for
   * <code>RuntimePermission("getClassLoader")</code> if the calling class
   * is not a parent of the system class loader.
   *
   * @return the system class loader
   * @throws SecurityException if the security check fails
   * @throws IllegalStateException if this is called recursively
   * @throws Error if <code>java.system.class.loader</code> fails to load
   * @since 1.2
   */
  public static ClassLoader getSystemClassLoader()
  {
    // Check if we may return the system classloader
    SecurityManager sm = SecurityManager.current;
    if (sm != null)
      {
	ClassLoader cl = VMStackWalker.getCallingClassLoader();
	if (cl != null && cl != StaticData.systemClassLoader)
	  sm.checkPermission(new RuntimePermission("getClassLoader"));
      }

    return StaticData.systemClassLoader;
  }

  /**
   * Defines a new package and creates a Package object. The package should
   * be defined before any class in the package is defined with
   * <code>defineClass()</code>. The package should not yet be defined
   * before in this classloader or in one of its parents (which means that
   * <code>getPackage()</code> should return <code>null</code>). All
   * parameters except the <code>name</code> of the package may be
   * <code>null</code>.
   *
   * <p>Subclasses should call this method from their <code>findClass()</code>
   * implementation before calling <code>defineClass()</code> on a Class
   * in a not yet defined Package (which can be checked by calling
   * <code>getPackage()</code>).
   *
   * @param name the name of the Package
   * @param specTitle the name of the specification
   * @param specVendor the name of the specification designer
   * @param specVersion the version of this specification
   * @param implTitle the name of the implementation
   * @param implVendor the vendor that wrote this implementation
   * @param implVersion the version of this implementation
   * @param sealed if sealed the origin of the package classes
   * @return the Package object for the specified package
   * @throws IllegalArgumentException if the package name is null or it
   *         was already defined by this classloader or one of its parents
   * @see Package
   * @since 1.2
   */
  protected Package definePackage(String name, String specTitle,
                                  String specVendor, String specVersion,
                                  String implTitle, String implVendor,
                                  String implVersion, URL sealed)
  {
    if (getPackage(name) != null)
      throw new IllegalArgumentException("Package " + name
                                         + " already defined");
    Package p = new Package(name, specTitle, specVendor, specVersion,
                            implTitle, implVendor, implVersion, sealed);
    synchronized (definedPackages)
      {
        definedPackages.put(name, p);
      }
    return p;
  }

  /**
   * Returns the Package object for the requested package name. It returns
   * null when the package is not defined by this classloader or one of its
   * parents.
   *
   * @param name the package name to find
   * @return the package, if defined
   * @since 1.2
   */
  protected Package getPackage(String name)
  {
    Package p;
    if (parent == null)
      p = VMClassLoader.getPackage(name);
    else
      p = parent.getPackage(name);

    if (p == null)
      {
	synchronized (definedPackages)
	  {
	    p = (Package) definedPackages.get(name);
	  }
      }
    return p;
  }

  /**
   * Returns all Package objects defined by this classloader and its parents.
   *
   * @return an array of all defined packages
   * @since 1.2
   */
  protected Package[] getPackages()
  {
    // Get all our packages.
    Package[] packages;
    synchronized(definedPackages)
      {
        packages = new Package[definedPackages.size()];
        definedPackages.values().toArray(packages);
      }

    // If we have a parent get all packages defined by our parents.
    Package[] parentPackages;
    if (parent == null)
      parentPackages = VMClassLoader.getPackages();
    else
      parentPackages = parent.getPackages();

    Package[] allPackages = new Package[parentPackages.length
					+ packages.length];
    System.arraycopy(parentPackages, 0, allPackages, 0,
                     parentPackages.length);
    System.arraycopy(packages, 0, allPackages, parentPackages.length,
                     packages.length);
    return allPackages;
  }

  /**
   * Called by <code>Runtime.loadLibrary()</code> to get an absolute path
   * to a (system specific) library that was requested by a class loaded
   * by this classloader. The default implementation returns
   * <code>null</code>. It should be implemented by subclasses when they
   * have a way to find the absolute path to a library. If this method
   * returns null the library is searched for in the default locations
   * (the directories listed in the <code>java.library.path</code> system
   * property).
   *
   * @param name the (system specific) name of the requested library
   * @return the full pathname to the requested library, or null
   * @see Runtime#loadLibrary()
   * @since 1.2
   */
  protected String findLibrary(String name)
  {
    return null;
  }

  /**
   * Set the default assertion status for classes loaded by this classloader,
   * used unless overridden by a package or class request.
   *
   * @param enabled true to set the default to enabled
   * @see #setClassAssertionStatus(String, boolean)
   * @see #setPackageAssertionStatus(String, boolean)
   * @see #clearAssertionStatus()
   * @since 1.4
   */
  public void setDefaultAssertionStatus(boolean enabled)
  {
    defaultAssertionStatus = enabled;
  }
  
  /**
   * Set the default assertion status for packages, used unless overridden
   * by a class request. This default also covers subpackages, unless they
   * are also specified. The unnamed package should use null for the name.
   *
   * @param name the package (and subpackages) to affect
   * @param enabled true to set the default to enabled
   * @see #setDefaultAssertionStatus(String, boolean)
   * @see #setClassAssertionStatus(String, boolean)
   * @see #clearAssertionStatus()
   * @since 1.4
   */
  public synchronized void setPackageAssertionStatus(String name,
                                                     boolean enabled)
  {
    if (packageAssertionStatus == null)
      packageAssertionStatus
        = new HashMap(StaticData.systemPackageAssertionStatus);
    packageAssertionStatus.put(name, Boolean.valueOf(enabled));
  }
  
  /**
   * Set the default assertion status for a class. This only affects the
   * status of top-level classes, any other string is harmless.
   *
   * @param name the class to affect
   * @param enabled true to set the default to enabled
   * @throws NullPointerException if name is null
   * @see #setDefaultAssertionStatus(String, boolean)
   * @see #setPackageAssertionStatus(String, boolean)
   * @see #clearAssertionStatus()
   * @since 1.4
   */
  public synchronized void setClassAssertionStatus(String name,
                                                   boolean enabled)
  {
    if (classAssertionStatus == null)
      classAssertionStatus = 
        new HashMap(StaticData.systemClassAssertionStatus);
    // The toString() hack catches null, as required.
    classAssertionStatus.put(name.toString(), Boolean.valueOf(enabled));
  }
  
  /**
   * Resets the default assertion status of this classloader, its packages
   * and classes, all to false. This allows overriding defaults inherited
   * from the command line.
   *
   * @see #setDefaultAssertionStatus(boolean)
   * @see #setClassAssertionStatus(String, boolean)
   * @see #setPackageAssertionStatus(String, boolean)
   * @since 1.4
   */
  public synchronized void clearAssertionStatus()
  {
    defaultAssertionStatus = false;
    packageAssertionStatus = new HashMap();
    classAssertionStatus = new HashMap();
  }

  /**
   * Return true if this loader is either the specified class loader
   * or an ancestor thereof.
   * @param loader the class loader to check
   */
  final boolean isAncestorOf(ClassLoader loader)
  {
    while (loader != null)
      {
	if (this == loader)
	  return true;
	loader = loader.parent;
      }
    return false;
  }

  private static URL[] getExtClassLoaderUrls()
  {
    String classpath = SystemProperties.getProperty("java.ext.dirs", "");
    StringTokenizer tok = new StringTokenizer(classpath, File.pathSeparator);
    ArrayList list = new ArrayList();
    while (tok.hasMoreTokens())
      {
	try
	  {
	    File f = new File(tok.nextToken());
	    File[] files = f.listFiles();
	    if (files != null)
	      for (int i = 0; i < files.length; i++)
		list.add(files[i].toURL());
	  }
	catch(Exception x)
	  {
	  }
      }
    URL[] urls = new URL[list.size()];
    list.toArray(urls);
    return urls;
  }

  private static void addFileURL(ArrayList list, String file)
  {
    try
      {
	list.add(new File(file).toURL());
      }
    catch(java.net.MalformedURLException x)
      {
      }
  }

  private static URL[] getSystemClassLoaderUrls()
  {
    String classpath = SystemProperties.getProperty("java.class.path", ".");
    StringTokenizer tok = new StringTokenizer(classpath, File.pathSeparator, true);
    ArrayList list = new ArrayList();
    while (tok.hasMoreTokens())
      {
	String s = tok.nextToken();
	if (s.equals(File.pathSeparator))
	    addFileURL(list, ".");
	else
	  {
	    addFileURL(list, s);
	    if (tok.hasMoreTokens())
	      {
		// Skip the separator.
		tok.nextToken();
		// If the classpath ended with a separator,
		// append the current directory.
		if (!tok.hasMoreTokens())
		    addFileURL(list, ".");
	      }
	  }
      }
    URL[] urls = new URL[list.size()];
    list.toArray(urls);
    return urls;
  }

  static ClassLoader defaultGetSystemClassLoader()
  {
    return createAuxiliarySystemClassLoader(
        createSystemClassLoader(getSystemClassLoaderUrls(),
            createExtClassLoader(getExtClassLoaderUrls(), null)));
  }

  static ClassLoader createExtClassLoader(URL[] urls, ClassLoader parent)
  {
    if (urls.length > 0)
      return new URLClassLoader(urls, parent);
    else
      return parent;
  }

  static ClassLoader createSystemClassLoader(URL[] urls, ClassLoader parent)
  {
    return
	new URLClassLoader(urls, parent)
	{
	    protected synchronized Class loadClass(String name,
		boolean resolve)
		throws ClassNotFoundException
	    {
		SecurityManager sm = SecurityManager.current;
		if (sm != null)
		{
		    int lastDot = name.lastIndexOf('.');
		    if (lastDot != -1)
			sm.checkPackageAccess(name.substring(0, lastDot));
		}
		return super.loadClass(name, resolve);
	    }
	};
  }

  static ClassLoader createAuxiliarySystemClassLoader(ClassLoader parent)
  {
    String loader = SystemProperties.getProperty("java.system.class.loader", null);
    if (loader == null)
      {
	return parent;
      }
    try
      {
	Constructor c = Class.forName(loader, false, parent)
	    .getConstructor(new Class[] { ClassLoader.class });
	return (ClassLoader)c.newInstance(new Object[] { parent });
      }
    catch (Exception e)
      {
	System.err.println("Requested system classloader " + loader + " failed.");
	throw (Error)
	    new Error("Requested system classloader " + loader + " failed.")
		.initCause(e);
      }
  }
}
