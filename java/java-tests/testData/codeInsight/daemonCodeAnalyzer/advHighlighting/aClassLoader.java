/*
 * @(#)ClassLoader.java	1.162 02/03/19
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Stack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.jar.Manifest;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.Permissions;
import java.security.CodeSource;
import java.security.Policy;
import sun.misc.URLClassPath;
import sun.misc.Resource;
import sun.misc.CompoundEnumeration;
import sun.misc.ClassFileTransformer;
//import sun.misc.Launcher;
import sun.reflect.Reflection;
import sun.security.action.GetPropertyAction;

/**
 * A class loader is an object that is responsible for loading
 * classes. The class <code>ClassLoader</code> is an abstract class.
 * Given the name of a class, a class loader should attempt to locate
 * or generate data that constitutes a definition for the class. A
 * typical strategy is to transform the name into a file
 * name and then read a "class file" of that name from a file system.
 * <p>
 * Every <code>Class</code> object contains a
 * {@link Class#getClassLoader() reference} to the
 * <code>ClassLoader</code> that defined it.
 * <p>
 * Class objects for array classes are not created by class loaders, but
 * are created automatically as required by the Java runtime. The class
 * loader for an array class, as returned by {@link Class#getClassLoader()}
 * is the same as the class loader for its element type; if the element
 * type is a primitive type, then the array class has no class loader.
 * <p>
 * Applications implement subclasses of <code>ClassLoader</code> in
 * order to extend the manner in which the Java virtual machine
 * dynamically loads classes.
 * <p>
 * Class loaders may typically be used by security managers to
 * indicate security domains.
 * <p>
 * The <code>ClassLoader</code> class uses a delegation model to
 * search for classes and resources. Each instance of
 * <code>ClassLoader</code> has an associated parent class loader.
 * When called upon to find a class or resource, a
 * <code>ClassLoader</code> instance will delegate the search for
 * the class or resource to its parent class loader before
 * attempting to find the class or resource itself.  The virtual
 * machine's built-in class loader, called the bootstrap class loader,
 * does not itself have a parent but may serve as the parent of a
 * <code>ClassLoader</code> instance.
 * <p>
 * Normally, the Java virtual machine loads classes from the local
 * file system in a platform-dependent manner. For example, on UNIX
 * systems, the virtual machine loads classes from the directory
 * defined by the <code>CLASSPATH</code> environment variable.
 * <p>
 * However, some classes may not originate from a file; they may
 * originate from other sources, such as the network, or they could
 * be constructed by an application. The method
 * <code>defineClass</code> converts an array of bytes into an
 * instance of class <code>Class</code>. Instances of this newly
 * defined class can be created using the <code>newInstance</code>
 * method in class <code>Class</code>.
 * <p>
 * The methods and constructors of objects created by a class loader
 * may reference other classes. To determine the class(es) referred
 * to, the Java virtual machine calls the <code>loadClass</code>
 * method of the class loader that originally created the class.
 * <p>
 * For example, an application could create a network class loader
 * to download class files from a server. Sample code might look like:
 * <blockquote><pre>
 *   ClassLoader loader&nbsp;= new NetworkClassLoader(host,&nbsp;port);
 *   Object main&nbsp;= loader.loadClass("Main", true).newInstance();
 *	 &nbsp;.&nbsp;.&nbsp;.
 * </pre></blockquote>
 * <p>
 * The network class loader subclass must define the methods
 * <code>findClass</code> and <code>loadClassData</code>
 * to load a class from the network. Once it
 * has downloaded the bytes that make up the class, it should use the
 * method <code>defineClass</code> to create a class instance. A
 * sample implementation is:
 * <p><hr><blockquote><pre>
 *     class NetworkClassLoader extends ClassLoader {
 *         String host;
 *         int port;
 *
 *         public Class findClass(String name) {
 *             byte[] b = loadClassData(name);
 *             return defineClass(name, b, 0, b.length);
 *         }
 *
 *         private byte[] loadClassData(String name) {
 *             // load the class data from the connection
 *             &nbsp;.&nbsp;.&nbsp;.
 *         }
 *     }
 * </pre></blockquote><hr>
 *
 * @version 1.162, 03/19/02
 * @see     java.lang.Class
 * @see     java.lang.Class#newInstance()
 * @see     java.lang.ClassLoader#defineClass(byte[], int, int)
 * @see     java.lang.ClassLoader#loadClass(java.lang.String, boolean)
 * @see     java.lang.ClassLoader#resolveClass(java.lang.Class)
 * @since   JDK1.0
 */
public abstract class ClassLoader {

    private static native void registerNatives();
    static {
        registerNatives();
    }

    /*
     * If initialization succeed this is set to true and security checks will
     * succeed. Otherwise the object is not initialized and the object is
     * useless.
     */
    private boolean initialized = false;

    /*
     * The parent class loader for delegation.
     */
    private ClassLoader parent;

    /*
     * Hashtable that maps packages to certs
     */
    private Hashtable package2certs = new Hashtable(11);

    /*
     * shared among all packages with unsigned classes
     */
    java.security.cert.Certificate[] nocerts;

    /*
     * The classes loaded by this class loader. The only purpose of this
     * table is to keep the classes from being GC'ed until the loader
     * is GC'ed.
     */
    private Vector classes = new Vector();

    /*
     * The initiating protection domains for all classes
     * loaded by this loader.
     */
    private Set domains = new HashSet();

    /*
     * Called by the VM to record every loaded class with this loader.
     */
    void addClass(Class c) {
        classes.addElement(c);
    }

    /*
     * The packages defined in this class loader. Each package name is
     * mapped to its corresponding Package object.
     */
    private HashMap packages = new HashMap();

    /**
     * Creates a new class loader using the specified parent class loader
     * for delegation.
     * <p>
     * If there is a security manager, its <code>checkCreateClassLoader</code>
     * method is called. This may result in a security exception.
     *
     * @param parent the parent class loader
     *
     * @throws  SecurityException if a security manager exists and its
     * <code>checkCreateClassLoader</code> method doesn't allow creation of a
     * new class loader.
     * @see       java.lang.SecurityException
     * @see       java.lang.SecurityManager#checkCreateClassLoader()
     * @since     1.2
     */
    protected ClassLoader(ClassLoader parent) {
	SecurityManager security = System.getSecurityManager();
	if (security != null) {
	    security.checkCreateClassLoader();
	}
	this.parent = parent;
	initialized = true;
    }

    /**
     * Creates a new class loader using the <code>ClassLoader</code>
     * returned by the method <code>getSystemClassLoader()</code> as the
     * parent class loader.
     * <p>
     * If there is a security manager, its <code>checkCreateClassLoader</code>
     * method is called. This may result in a security exception.
     *
     * @throws  SecurityException
     *    if a security manager exists and its <code>checkCreateClassLoader</code>
     *    method doesn't allow creation of a new class loader.
     *
     * @see       java.lang.SecurityException
     * @see       java.lang.SecurityManager#checkCreateClassLoader()
     */
    protected ClassLoader() {
	SecurityManager security = System.getSecurityManager();
	if (security != null) {
	    security.checkCreateClassLoader();
	}
	this.parent = getSystemClassLoader();
	initialized = true;
    }

    /**
     * Loads the class with the specified name. This method searches for
     * classes in the same manner as the {@link #loadClass(String, boolean)}
     * method. It is called by the Java virtual machine to resolve class
     * references. Calling this method is equivalent to calling
     * <code>loadClass(name, false)</code>.
     *
     * @param     name the name of the class
     * @return    the resulting <code>Class</code> object
     * @exception ClassNotFoundException if the class was not found
     */
    public Class loadClass(String name) throws ClassNotFoundException
    {
	return loadClass(name, false);
    }

    /**
     * Loads the class with the specified name.  The default implementation of
     * this method searches for classes in the following order:<p>
     *
     * <ol>
     * <li> Call {@link #findLoadedClass(String)} to check if the class has
     *      already been loaded. <p>
     * <li> Call the <code>loadClass</code> method on the parent class
     *      loader.  If the parent is <code>null</code> the class loader
     *      built-in to the virtual machine is used, instead. <p>
     * <li> Call the {@link #findClass(String)} method to find the class. <p>
     * </ol>
     *
     * If the class was found using the above steps, and the
     * <code>resolve</code> flag is true, this method will then call the
     * {@link #resolveClass(Class)} method on the resulting class object.
     * <p>
     * From the Java 2 SDK, v1.2, subclasses of ClassLoader are
     * encouraged to override
     * {@link #findClass(String)}, rather than this method.<p>
     *
     * @param     name the name of the class
     * @param     resolve if <code>true</code> then resolve the class
     * @return	  the resulting <code>Class</code> object
     * @exception ClassNotFoundException if the class could not be found
     */
    protected synchronized Class loadClass(String name, boolean resolve)
	throws ClassNotFoundException
    {
	// First, check if the class has already been loaded
	Class c = findLoadedClass(name);
	if (c == null) {
	    try {
		if (parent != null) {
		    c = parent.loadClass(name, false);
		} else {
		    c = findBootstrapClass0(name);
		}
	    } catch (ClassNotFoundException e) {
	        // If still not found, then call findClass in order
	        // to find the class.
	        c = findClass(name);
	    }
	}
	if (resolve) {
	    resolveClass(c);
	}
	return c;
    }

    /*
     * This method is called by the virtual machine to load
     * a class.
     */
    private synchronized Class loadClassInternal(String name)
	throws ClassNotFoundException {

	return loadClass(name);
    }

    private void checkPackageAccess(Class cls, ProtectionDomain pd) {
	final SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    final String name = cls.getName();
            final int i = name.lastIndexOf('.');
	    if (i != -1) {
                AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
		        sm.checkPackageAccess(name.substring(0, i));
		        return null;
                    }
                }, new AccessControlContext(new ProtectionDomain[] {pd}));
	    }
	}
	domains.add(pd);
    }

    /**
     * Finds the specified class. This method should be overridden
     * by class loader implementations that follow the new delegation model
     * for loading classes, and will be called by the <code>loadClass</code>
     * method after checking the parent class loader for the requested class.
     * The default implementation throws <code>ClassNotFoundException</code>.
     *
     * @param  name the name of the class
     * @return the resulting <code>Class</code> object
     * @exception ClassNotFoundException if the class could not be found
     * @since  1.2
     */
    protected Class findClass(String name) throws ClassNotFoundException {
	throw new ClassNotFoundException(name);
    }

    /**
     * Converts an array of bytes into an instance of class
     * <code>Class</code>.  Before the Class can be used it must be
     * resolved. This method is deprecated in favor of the version
     * that takes the class name as its first argument, and is more
     * secure.
     *
     * @param      b   the bytes that make up the class data. The bytes in
     *             positions <code>off</code> through <code>off+len-1</code>
     *             should have the format of a valid class file as defined
     *             by the
     *             <a href="http://java.sun.com/docs/books/vmspec/">Java
     *             Virtual Machine Specification</a>.
     * @param      off  the start offset in <code>b</code> of the class data
     * @param      len the length of the class data
     * @return     the <code>Class</code> object that was created from the
     *             specified class data
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  IndexOutOfBoundsException if either <code>off</code> or
     *             <code>len</code> is negative, or if
     *             <code>off+len</code> is greater than <code>b.length</code>.
     * @see        ClassLoader#loadClass(java.lang.String, boolean)
     * @see        ClassLoader#resolveClass(java.lang.Class)
     * @deprecated Replaced by defineClass(java.lang.String, byte[], int, int)
     */
    protected final Class defineClass(byte[] b, int off, int len)
	throws ClassFormatError
    {
	return defineClass(null, b, off, len, null);
    }

    /**
     * Converts an array of bytes into an instance of class <code>Class</code>.
     * Before the Class can be used it must be resolved.
     * <p>
     * This method assigns a default <code>ProtectionDomain</code> to
     * the newly defined class. The <code>ProtectionDomain</code>
     * contains the set of permissions granted when
     * a call to <code>Policy.getPolicy().getPermissions()</code> is made with
     * a code source of <code>null,null</code>. The default domain is
     * created on the first invocation of <code>defineClass</code>, and
     * re-used on subsequent calls.
     * <p>
     * To assign a specific <code>ProtectionDomain</code> to the class,
     * use the <code>defineClass</code> method that takes a
     * <code>ProtectionDomain</code> as one of its arguments.
     *
     * @param      name the expected name of the class, or <code>null</code>
     *                  if not known, using '.' and not '/' as the separator
     *                  and without a trailing ".class" suffix.
     * @param      b    the bytes that make up the class data. The bytes in
     *             positions <code>off</code> through <code>off+len-1</code>
     *             should have the format of a valid class file as defined
     *             by the
     *             <a href="http://java.sun.com/docs/books/vmspec/">Java
     *             Virtual Machine Specification</a>.
     * @param      off  the start offset in <code>b</code> of the class data
     * @param      len  the length of the class data
     * @return     the <code>Class</code> object that was created from the
     *             specified class data
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  IndexOutOfBoundsException if either <code>off</code> or
     *             <code>len</code> is negative, or if
     *             <code>off+len</code> is greater than <code>b.length</code>.
     * @exception  SecurityException if an attempt is made to add this class
     *             to a package that contains classes that were signed by
     *             a different set of certificates than this class (which
     *             is unsigned), or if the class name begins with "java.".
     *
     * @see        ClassLoader#loadClass(java.lang.String, boolean)
     * @see        ClassLoader#resolveClass(java.lang.Class)
     * @see        java.security.ProtectionDomain
     * @see        java.security.Policy
     * @see        java.security.CodeSource
     * @see        java.security.SecureClassLoader
     * @since      JDK1.1
     */
    protected final Class defineClass(String name, byte[] b, int off, int len)
	throws ClassFormatError
    {
	return defineClass(name, b, off, len, null);
    }

    /**
     * Converts an array of bytes into an instance of class Class,
     * with an optional ProtectionDomain. If the domain is <code>null</code>,
     * then a default domain will be assigned to the class as specified
     * in the documentation for {@link #defineClass(String,byte[],int,int)}.
     * Before the class can be used it must be resolved.
     *
     * <p>The first class defined in a package determines the exact set of
     * certificates that all subsequent classes defined in that package must
     * contain. The set of certificates for a class is obtained from the
     * <code>CodeSource</code> within the <code>ProtectionDomain</code> of
     * the class. Any classes added to that package must contain
     * the same set of certificates or a <code>SecurityException</code>
     * will be thrown. Note that if the <code>name</code> argument is
     * null, this check is not performed. You should always pass in the
     * name of the class you are defining as well as the bytes. This
     * ensures that the class you are defining is indeed the class
     * you think it is.
     *
     * <p>The specified class name cannot begin with "java.", since all
     * classes in the java.* packages can only be defined by the bootstrap
     * class loader. If the name parameter is not <TT>null</TT>, it
     * must be equal to the name of the class specified by the byte
     * array b, otherwise a <TT>ClassFormatError</TT> is raised.
     *
     * @param      name the expected name of the class, or <code>null</code>
     *                  if not known, using '.' and not '/' as the separator
     *                  and without a trailing ".class" suffix.
     * @param      b    the bytes that make up the class data. The bytes in
     *             positions <code>off</code> through <code>off+len-1</code>
     *             should have the format of a valid class file as defined
     *             by the
     *             <a href="http://java.sun.com/docs/books/vmspec/">Java
     *             Virtual Machine Specification</a>.
     * @param      off  the start offset in <code>b</code> of the class data
     * @param      len  the length of the class data
     * @param protectionDomain the ProtectionDomain of the class
     * @return the <code>Class</code> object created from the data,
     *         and optional ProtectionDomain.
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  IndexOutOfBoundsException if either <code>off</code> or
     *             <code>len</code> is negative, or if
     *             <code>off+len</code> is greater than <code>b.length</code>.
     * @exception  SecurityException if an attempt is made to add this class
     *             to a package that contains classes that were signed by
     *             a different set of certificates than this class, or if
     *             the class name begins with "java.".
     */
    protected final Class defineClass(String name, byte[] b, int off, int len,
				      ProtectionDomain protectionDomain)
	throws ClassFormatError
    {
	check();
        if ((name != null) && name.startsWith("java.")) {
            throw new SecurityException("Prohibited package name: " +
                                        name.substring(0, name.lastIndexOf('.')));
        }
	if (protectionDomain == null) {
	    protectionDomain = getDefaultDomain();
	}

	if (name != null)
	    checkCerts(name, protectionDomain.getCodeSource());

	Class c = null;

	try
	{
	    c = defineClass0(name, b, off, len, protectionDomain);
	}
	catch (ClassFormatError cfe)
	{
	    // Class format error - try to transform the bytecode and
	    // define the class again
	    //
	    Object[] transformers = ClassFileTransformer.getTransformers();

	    for (int i=0; transformers != null && i < transformers.length; i++)
	    {
		try
		{
		    // Transform byte code using transformer
		    byte[] tb = ((ClassFileTransformer) transformers[i]).transform(b, off, len);
		    c = defineClass0(name, tb, 0, tb.length, protectionDomain);
		    break;
		}
		catch (ClassFormatError cfe2)
		{
		    // If ClassFormatError occurs, try next transformer
		}
	    }

	    // Rethrow original ClassFormatError if unable to transform
	    // bytecode to well-formed
	    //
	    if (c == null)
		throw cfe;
	}

	if (protectionDomain.getCodeSource() != null) {
	    java.security.cert.Certificate certs[] =
		protectionDomain.getCodeSource().getCertificates();
	    if (certs != null)
		setSigners(c, certs);
	}
	return c;
    }

    private synchronized void checkCerts(String name, CodeSource cs)
    {
	int i = name.lastIndexOf('.');
	String pname = (i == -1) ? "" : name.substring(0,i);
	java.security.cert.Certificate[] pcerts =
	    (java.security.cert.Certificate[]) package2certs.get(pname);
        if (pcerts == null) {
	    // first class in this package gets to define which
	    // certificates must be the same for all other classes
	    // in this package
	    if (cs != null) {
		pcerts = cs.getCertificates();
	    }
	    if (pcerts == null) {
		if (nocerts == null)
		    nocerts = new java.security.cert.Certificate[0];
		pcerts = nocerts;
	    }
	    package2certs.put(pname, pcerts);
	} else {
	    java.security.cert.Certificate[] certs = null;
	    if (cs != null) {
		certs = cs.getCertificates();
	    }

	    if (!compareCerts(pcerts,certs)) {
		throw new SecurityException("class \""+ name+
					    "\"'s signer information does not match signer information of other classes in the same package");
	    }
	}
    }

    /**
     * check to make sure the certs for the new class (certs) are
     * the same as the certs for the first class inserted
     * in the package (pcerts)
     */
    private boolean compareCerts(java.security.cert.Certificate[] pcerts,
				 java.security.cert.Certificate[] certs)
    {
	// certs can be null, indicating no certs.
	if ((certs == null) || (certs.length == 0)) {
	    return pcerts.length == 0;
	}

	// the length must be the same at this point
	if (certs.length != pcerts.length)
	    return false;

	// go through and make sure all the certs in one array
	// are in the other and vice-versa.
	boolean match;
	for (int i=0; i < certs.length; i++) {
	    match = false;
	    for (int j=0; j < pcerts.length; j++) {
		if (certs[i].equals(pcerts[j])) {
		    match = true;
		    break;
		}
	    }
	    if (!match) return false;
	}

	// now do the same for pcerts
	for (int i=0; i < pcerts.length; i++) {
	    match = false;
	    for (int j=0; j < certs.length; j++) {
		if (pcerts[i].equals(certs[j])) {
		    match = true;
		    break;
		}
	    }
	    if (!match) return false;
	}

	return true;
    }

    /**
     * Links the specified class.
     * This (misleadingly named) method may be used by a class loader to
     * link a class. If the class <code>c</code> has already been linked,
     * then this method simply returns. Otherwise, the class is linked
     * as described in the "Execution" chapter of the <i>Java Language
     * Specification</i>.
     *
     * @param c the class to link
     * @exception NullPointerException if <code>c</code> is <code>null</code>.
     * @see   java.lang.ClassLoader#defineClass(java.lang.String,byte[],int,int)
     */
    protected final void resolveClass(Class c) {
	check();
	resolveClass0(c);
    }

    /**
     * Finds a class with the specified name, loading it if necessary.<p>
     *
     * Prior to the Java 2 SDK, this method loads a class from the local file
     * system in a platform-dependent manner, and returns a class object
     * that has no associated class loader.<p>
     *
     * Since the Java 2 SDK v1.2, this method loads the class through the
     * system class loader(see {@link #getSystemClassLoader()}).  Class objects
     * returned might have <code>ClassLoader</code>s associated with them.
     * Subclasses of <code>ClassLoader</code> need not usually call this
     * method, because most class loaders need to override just {@link
     * #findClass(String)}.<p>
     *
     * @param     name the name of the class that is to be found
     * @return the <code>Class</code> object for the specified
     * <code>name</code>
     * @exception ClassNotFoundException if the class could not be found
     * @see       #ClassLoader(ClassLoader)
     * @see       #getParent()
     */
    protected final Class findSystemClass(String name)
	throws ClassNotFoundException
    {
	check();
	ClassLoader system = getSystemClassLoader();
	if (system == null) {
	    return findBootstrapClass(name);
	}
	return system.loadClass(name);
    }

    /**
     * Returns the parent class loader for delegation. Some implementations
     * may use <code>null</code> to represent the bootstrap class
     * loader. This method will return <code>null</code> in such
     * implementations if this class loader's parent is the bootstrap
     * class loader.
     * <p>
     * If a security manager is present, and the caller's class loader is
     * not null and is not an ancestor of this class loader, then
     * this method calls the security manager's <code>checkPermission</code>
     * method with a <code>RuntimePermission("getClassLoader")</code>
     * permission to ensure it's ok to access the parent class loader.
     * If not, a <code>SecurityException</code> will be thrown.
     *
     * @return the parent <code>ClassLoader</code>
     * @throws SecurityException
     *    if a security manager exists and its
     *    <code>checkPermission</code> method doesn't allow
     *    access to this class loader's parent class loader.
     *
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     *
     * @since 1.2
     */
    public final ClassLoader getParent() {
	if (parent == null)
	    return null;
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    ClassLoader ccl = getCallerClassLoader();
	    if (ccl != null && !isAncestor(ccl)) {
		sm.checkPermission(getGetClassLoaderPerm());
	    }
	}
	return parent;
    }

    /**
     * Sets the signers of a class. This should be called after defining a
     * class.
     *
     * @param c the <code>Class</code> object
     * @param signers the signers for the class
     * @since JDK1.1
     */
    protected final void setSigners(Class c, Object[] signers) {
        check();
	c.setSigners(signers);
    }

    private Class findBootstrapClass0(String name)
	throws ClassNotFoundException {
	check();
	return findBootstrapClass(name);
    }

    private native Class defineClass0(String name, byte[] b, int off, int len,
	ProtectionDomain pd);
    private native void resolveClass0(Class c);
    private native Class findBootstrapClass(String name)
	throws ClassNotFoundException;

    /*
     * Check to make sure the class loader has been initialized.
     */
    private void check() {
	if (!initialized) {
	    throw new SecurityException("ClassLoader object not initialized");
	}
    }

    /**
     * Finds the class with the given name if it had been previously loaded
     * through this class loader.
     *
     * @param  name the class name
     * @return the <code>Class</code> object, or <code>null</code> if
     *         the class has not been loaded
     * @since  JDK1.1
     */
    protected native final Class findLoadedClass(String name);

    /**
     * Finds the resource with the given name. A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.<p>
     *
     * The name of a resource is a "/"-separated path name that identifies
     * the resource.<p>
     *
     * This method will first search the parent class loader for the resource;
     * if the parent is <code>null</code> the path of the class loader
     * built-in to the virtual machine is searched.  That failing, this method
     * will call <code>findResource</code> to find the resource.<p>
     *
     * @param  name resource name
     * @return a URL for reading the resource, or <code>null</code> if
     *         the resource could not be found or the caller doesn't have
     *         adequate privileges to get the resource.
     * @since  JDK1.1
     * @see #findResource(String)
     */
    public URL getResource(String name) {
	URL url;
	if (parent != null) {
	    url = parent.getResource(name);
	} else {
	    url = getBootstrapResource(name);
	}
	if (url == null) {
	    url = findResource(name);
	}
	return url;
    }

    /**
     * Finds all the resources with the given name. A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.<p>
     *
     * The name of a resource is a "/"-separated path name that identifies the
     * resource.<p>
     *
     * The search order is described in the documentation for {@link
     * #getResource(String)}.<p>
     *
     * @param  name resource name
     * @return an enumeration of URL to the resource. If no resources could
     *         be found, the enumeration will be empty. Resources that the
     *         doesn't have access to will not be in the enumeration.
     * @throws IOException if I/O errors occur
     * @since  1.2
     * @see    #getResource
     * @see #findResources
     */
    public final Enumeration getResources(String name) throws IOException {
	Enumeration[] tmp = new Enumeration[2];
	if (parent != null) {
	    tmp[0] = parent.getResources(name);
	} else {
	    tmp[0] = getBootstrapResources(name);
	}
	tmp[1] = findResources(name);

	return new CompoundEnumeration(tmp);
    }

    /**
     * Returns an Enumeration of URLs representing all the resources with
     * the given name. Class loader implementations should override this
     * method to specify where to load resources from.
     *
     * @param  name the resource name
     * @return an Enumeration of URLs for the resources
     * @throws IOException if I/O errors occur
     * @since  1.2
     */
    protected Enumeration findResources(String name) throws IOException {
	return new CompoundEnumeration(new Enumeration[0]);
    }

    /**
     * Finds the resource with the given name. Class loader
     * implementations should override this method to specify where to
     * find resources.
     *
     * @param  name the resource name
     * @return a URL for reading the resource, or <code>null</code>
     *         if the resource could not be found
     * @since  1.2
     */
    protected URL findResource(String name) {
	return null;
    }

    /**
     * Find a resource of the specified name from the search path used to load
     * classes.<p>
     *
     * In JDK1.1, the search path used is that of the virtual machine's
     * built-in class loader.<p>
     *
     * Since the Java 2 SDK v1.2, this method locates the resource through the system class
     * loader (see {@link #getSystemClassLoader()}).
     *
     * @param  name the resource name
     * @return a URL for reading the resource, or <code>null</code> if
     *         the resource could not be found
     * @since JDK1.1
     */
    public static URL getSystemResource(String name) {
	ClassLoader system = getSystemClassLoader();
	if (system == null) {
	    return getBootstrapResource(name);
	}
	return system.getResource(name);
    }

    /**
     * Find resources from the VM's built-in classloader.
     */
    private static URL getBootstrapResource(String name) {
	URLClassPath ucp = getBootstrapClassPath();
	Resource res = ucp.getResource(name);
	return res != null ? res.getURL() : null;
    }

    /**
     * Finds all resources of the specified name from the search path used to
     * load classes. The resources thus found are returned as an
     * <code>Enumeration</code> of <code>URL</code> objects. <p>
     *
     * The search order is described in the documentation for {@link
     * #getSystemResource(String)}. <p>
     *
     * @param  name the resource name
     * @return an enumeration of resource URLs
     * @throws IOException if I/O errors occur
     * @since 1.2
     */
    public static Enumeration getSystemResources(String name)
	throws IOException
    {
	ClassLoader system = getSystemClassLoader();
	if (system == null) {
	    return getBootstrapResources(name);
	}
	return system.getResources(name);
    }

    /**
     * Find resources from the VM's built-in classloader.
     */
    private static Enumeration getBootstrapResources(String name)
	throws IOException
    {
	final Enumeration e = getBootstrapClassPath().getResources(name);
	return new Enumeration () {
	    public Object nextElement() {
		return ((Resource)e.nextElement()).getURL();
	    }
	    public boolean hasMoreElements() {
		return e.hasMoreElements();
	    }
	};
    }

    /*
     * Returns the URLClassPath that is used for finding system resources.
     */
    static URLClassPath getBootstrapClassPath() {
	if (bootstrapClassPath == null) {
	    bootstrapClassPath = sun.misc.Launcher.getBootstrapClassPath();
	}
	return bootstrapClassPath;
    }

    private static URLClassPath bootstrapClassPath;

    /**
     * Returns an input stream for reading the specified resource.
     *
     * The search order is described in the documentation for {@link
     * #getResource(String)}.<p>
     *
     * @param  name the resource name
     * @return an input stream for reading the resource, or <code>null</code>
     *         if the resource could not be found
     * @since  JDK1.1
     */
    public InputStream getResourceAsStream(String name) {
	URL url = getResource(name);
	try {
	    return url != null ? url.openStream() : null;
	} catch (IOException e) {
	    return null;
	}
    }

    /**
     * Open for reading, a resource of the specified name from the search path
     * used to load classes.<p>
     *
     * The search order is described in the documentation for {@link
     * #getSystemResource(String)}. <p>
     *
     * @param  name the resource name
     * @return an input stream for reading the resource, or <code>null</code>
     * 	       if the resource could not be found
     * @since JDK1.1
     */
    public static InputStream getSystemResourceAsStream(String name) {
	URL url = getSystemResource(name);
	try {
	    return url != null ? url.openStream() : null;
	} catch (IOException e) {
	    return null;
	}
    }

    /**
     * Returns the system class loader for delegation. This is the default
     * delegation parent for new <code>ClassLoader</code> instances, and
     * is typically the class loader used to start the application.
     * <p>
     * This method is first invoked early in the runtime's startup
     * sequence, at which point it creates the system class loader
     * and sets it as the context class loader of the invoking
     * <tt>Thread</tt>.
     * <p>
     * The default system class loader is an implementation-dependent
     * instance of this class.
     * <p>
     * If the system property <tt>java.system.class.loader</tt> is
     * defined when this method is first invoked then the value of that
     * property is taken to be the name of a class that will be returned as
     * the system class loader. The class is loaded using the default system
     * class loader and must define a public constructor that takes a single
     * parameter of type <tt>ClassLoader</tt> which is used
     * as the delegation parent. An instance is then created using this
     * constructor with the default system class loader as the parameter.
     * The resulting class loader is defined to be the system class loader.
     * <p>
     * If a security manager is present, and the caller's class loader is
     * not null and the caller's class loader is not the same as or an ancestor of
     * the system class loader, then
     * this method calls the security manager's <code>checkPermission</code>
     * method with a <code>RuntimePermission("getClassLoader")</code>
     * permission to ensure it's ok to access the system class loader.
     * If not, a <code>SecurityException</code> will be thrown.
     *
     * @return the system <code>ClassLoader</code> for delegation, or
     *         <code>null</code> if none
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow
     *        access to the system class loader.
     * @throws IllegalStateException
     *        if invoked recursively during the construction
     *        of the class loader specified by the
     *        <code>java.system.class.loader</code> property.
     * @throws Error
     *        if the system property <tt>java.system.class.loader</tt>
     *        is defined but the named class could not be loaded, the
     *        provider class does not define the required constructor, or an
     *        exception is thrown by that constructor when it is invoked. The
     *        underlying cause of the error can be retrieved via the
     *        {@link Throwable#getCause()} method.
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     * @revised 1.4
     */
    public static ClassLoader getSystemClassLoader() {
	initSystemClassLoader();
	if (scl == null) {
	    return null;
	}
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    ClassLoader ccl = getCallerClassLoader();
	    if (ccl != null && ccl != scl && !scl.isAncestor(ccl)) {
		sm.checkPermission(getGetClassLoaderPerm());
	    }
	}
	return scl;
    }

    private static synchronized void initSystemClassLoader() {
	if (!sclSet) {
	    if (scl != null)
		throw new IllegalStateException("recursive call");
            sun.misc.Launcher l = sun.misc.Launcher.getLauncher();
	    if (l != null) {
		Throwable oops = null;
		scl = l.getClassLoader();
	        try {
		    PrivilegedExceptionAction a;
		    a = new SystemClassLoaderAction(scl);
                    scl = (ClassLoader) AccessController.doPrivileged(a);
	        } catch (PrivilegedActionException pae) {
		    oops = pae.getCause();
	            if (oops instanceof InvocationTargetException) {
		        oops = oops.getCause();
		    }
	        }
		if (oops != null) {
		    if (oops instanceof Error) {
			throw (Error) oops;
		    } else {
		        // wrap the exception
		        throw new Error(oops);
		    }
		}
	    }
	    sclSet = true;
	}
    }

    // Returns true if the specified class loader can be found
    // in this class loader's delegation chain.
    boolean isAncestor(ClassLoader cl) {
	ClassLoader acl = this;
	do {
	    acl = acl.parent;
	    if (cl == acl) {
		return true;
	    }
	} while (acl != null);
	return false;
    }

    // Returns the caller's class loader, or null if none.
    // NOTE this must always be called when there is exactly one
    // intervening frame from the core libraries on the stack between
    // this method's invocation and the desired caller.
    static ClassLoader getCallerClassLoader() {
        // NOTE use of more generic Reflection.getCallerClass()
        Class caller = Reflection.getCallerClass(3);
        // This can be null if the VM is requesting it
        if (caller == null) {
            return null;
        }
        // Circumvent security check since this is package-private
        return caller.getClassLoader0();
    }

    // The class loader for the system
    private static ClassLoader scl;

    // Set to true once the system class loader has been set
    private static boolean sclSet;

    // Permission to access system or parent class loader
    private static RuntimePermission getClassLoaderPerm = null;

    static RuntimePermission getGetClassLoaderPerm()
    {
	if (getClassLoaderPerm == null)
	    getClassLoaderPerm = new RuntimePermission("getClassLoader");
	return getClassLoaderPerm;
    }

    /**
     * Defines a package by name in this ClassLoader. This allows class
     * loaders to define the packages for their classes. Packages must be
     * created before the class is defined, and package names must be
     * unique within a class loader and cannot be redefined or changed
     * once created.
     *
     * @param name        the package name
     * @param specTitle   the specification title
     * @param specVersion the specification version
     * @param specVendor  the specification vendor
     * @param implTitle   the implementation title
     * @param implVersion the implementation version
     * @param implVendor  the implementation vendor
     * @param sealBase    If not null, then this package is sealed with
     *                    respect to the given code source URL. Otherwise,
     *			  the package is not sealed.
     * @return the newly defined <code>Package</code> object
     * @exception IllegalArgumentException if package name duplicates an
     *            existing package either in this class loader or one of
     *            its ancestors
     * @since 1.2
     */
    protected Package definePackage(String name, String specTitle,
				    String specVersion, String specVendor,
				    String implTitle, String implVersion,
				    String implVendor, URL sealBase)
	throws IllegalArgumentException
    {
	synchronized (packages) {
	    Package pkg = getPackage(name);
	    if (pkg != null) {
		throw new IllegalArgumentException(name);
	    }
	    pkg = new Package(name, specTitle, specVersion, specVendor,
			      implTitle, implVersion, implVendor,
			      sealBase);
	    packages.put(name, pkg);
	    return pkg;
	}
    }

    /**
     * Returns a Package that has been defined by this class loader or any
     * of its ancestors.
     *
     * @param  name the package name
     * @return the Package corresponding to the given name, or null if not
     *         found
     * @since  1.2
     */
    protected Package getPackage(String name) {
	synchronized (packages) {
	    Package pkg = (Package)packages.get(name);
	    if (pkg == null) {
		if (parent != null) {
		    pkg = parent.getPackage(name);
		} else {
		    pkg = Package.getSystemPackage(name);
		}
		if (pkg != null) {
		    packages.put(name, pkg);
		}
	    }
	    return pkg;
	}
    }

    /**
     * Returns all of the Packages defined by this class loader and its
     * ancestors.
     *
     * @return the array of <code>Package</code> objects defined by this
     * <code>ClassLoader</code>
     * @since 1.2
     */
    protected Package[] getPackages() {
	Map map;
	synchronized (packages) {
	    map = (Map)packages.clone();
	}
	Package[] pkgs;
	if (parent != null) {
	    pkgs = parent.getPackages();
	} else {
	    pkgs = Package.getSystemPackages();
	}
	if (pkgs != null) {
	    for (int i = 0; i < pkgs.length; i++) {
                String pkgName = pkgs[i].getName();
                if (map.get(pkgName) == null) {
                    map.put(pkgName, pkgs[i]);
                }
	    }
	}
	return (Package[])map.values().toArray(new Package[map.size()]);
    }

    /**
     * Returns the absolute path name of a native library. The VM
     * invokes this method to locate the native libraries that belong
     * to classes loaded with this class loader. If this method returns
     * <code>null</code>, the VM searches the library along the path
     * specified as the <code>java.library.path</code> property.
     *
     * @param      libname   the library name
     * @return     the absolute path of the native library
     * @see        java.lang.System#loadLibrary(java.lang.String)
     * @see        java.lang.System#mapLibraryName(java.lang.String)
     * @since      1.2
     */
    protected String findLibrary(String libname) {
        return null;
    }

    /**
     * The inner class NativeLibrary denotes a loaded native library
     * instance. Every classloader contains a vector of loaded native
     * libraries in the private field <code>nativeLibraries</code>.
     * The native libraries loaded into the system are entered into
     * the <code>systemNativeLibraries</code> vector.
     *
     * Every native library reuqires a particular version of JNI. This
     * is denoted by the private jniVersion field. This field is set
     * by the VM when it loads the library, and used by the VM to pass
     * the correct version of JNI to the native methods.
     *
     * @version 1.162, 03/19/02
     * @see     java.lang.ClassLoader
     * @since   1.2
     */
    static class NativeLibrary {
        /* opaque handle to native library, used in native code. */
        long handle;
        /* the version of JNI environment the native library requires. */
        private int jniVersion;
        /* the class from which the library is loaded, also indicates
	   the loader this native library belongs. */
        private Class fromClass;
        /* the canonicalized name of the native library. */
        String name;

        native void load(String name);
        native long find(String name);
        native void unload();

        public NativeLibrary(Class fromClass, String name) {
            this.name = name;
	    this.fromClass = fromClass;
	}

        protected void finalize() {
	    synchronized (loadedLibraryNames) {
	        if (fromClass.getClassLoader() != null && handle != 0) {
		    /* remove the native library name */
		    int size = loadedLibraryNames.size();
		    for (int i = 0; i < size; i++) {
		        if (name.equals(loadedLibraryNames.elementAt(i))) {
			    loadedLibraryNames.removeElementAt(i);
			    break;
			}
		    }
		    /* unload the library. */
		    ClassLoader.nativeLibraryContext.push(this);
		    try {
			unload();
		    } finally {
		        ClassLoader.nativeLibraryContext.pop();
		    }
		}
	    }
	}
        /* Called in the VM to determine the context class in
	   JNI_Load/JNI_Unload */
        static Class getFromClass() {
            return ((NativeLibrary)
		    (ClassLoader.nativeLibraryContext.peek())).fromClass;
	}
    }

    /* the "default" domain. Set as the default ProtectionDomain
     * on newly created classses.
     */
    private ProtectionDomain defaultDomain = null;

    /*
     * returns (and initializes) the default domain.
     */

    private synchronized ProtectionDomain getDefaultDomain() {
	if (defaultDomain == null) {
	    CodeSource cs = new CodeSource(null, null);
	    defaultDomain = new ProtectionDomain(cs, null, this, null);
	}
	return defaultDomain;
    }

    /* All native library names we've loaded. */
    private static Vector loadedLibraryNames = new Vector();
    /* Native libraries belonging to system classes. */
    private static Vector systemNativeLibraries = new Vector();
    /* Native libraries associated with the class loader. */
    private Vector nativeLibraries = new Vector();

    /* native libraries being loaded/unloaded. */
    private static Stack nativeLibraryContext = new Stack();

    /* The paths searched for libraries */
    static private String usr_paths[];
    static private String sys_paths[];

    private static String[] initializePath(String propname) {
        String ldpath = System.getProperty(propname, "");
	String ps = File.pathSeparator;
	int ldlen = ldpath.length();
	int i, j, n;
	// Count the separators in the path
	i = ldpath.indexOf(ps);
	n = 0;
	while (i >= 0) {
	    n++;
	    i = ldpath.indexOf(ps, i+1);
	}

	// allocate the array of paths - n :'s = n + 1 path elements
	String[] paths = new String[n + 1];

	// Fill the array with paths from the ldpath
	n = i = 0;
	j = ldpath.indexOf(ps);
	while (j >= 0) {
	    if (j - i > 0) {
	        paths[n++] = ldpath.substring(i, j);
	    } else if (j - i == 0) {
	        paths[n++] = ".";
	    }
	    i = j + 1;
	    j = ldpath.indexOf(ps, i);
	}
	paths[n] = ldpath.substring(i, ldlen);
	return paths;
    }


    /* Called in the java.lang.Runtime class to implement load
     * and loadLibrary.
     */
    static void loadLibrary(Class fromClass, String name,
			    boolean isAbsolute) {
        ClassLoader loader =
	    (fromClass == null) ? null : fromClass.getClassLoader();
        if (sys_paths == null) {
	    usr_paths = initializePath("java.library.path");
	    sys_paths = initializePath("sun.boot.library.path");
        }
        if (isAbsolute) {
	    if (loadLibrary0(fromClass, new File(name))) {
	        return;
	    }
	    throw new UnsatisfiedLinkError("Can't load library: " + name);
	}
	if (loader != null) {
	    String libfilename = loader.findLibrary(name);
	    if (libfilename != null) {
	        File libfile = new File(libfilename);
	        if (!libfile.isAbsolute()) {
		    throw new UnsatisfiedLinkError(
    "ClassLoader.findLibrary failed to return an absolute path: " + libfilename);
		}
		if (loadLibrary0(fromClass, libfile)) {
		    return;
		}
		throw new UnsatisfiedLinkError ("Can't load " + libfilename);
	    }
	}
	for (int i = 0 ; i < sys_paths.length ; i++) {
	    File libfile = new File(sys_paths[i], System.mapLibraryName(name));
	    if (loadLibrary0(fromClass, libfile)) {
	        return;
	    }
	}
	if (loader != null) {
	    for (int i = 0 ; i < usr_paths.length ; i++) {
	        File libfile = new File(usr_paths[i],
					System.mapLibraryName(name));
		if (loadLibrary0(fromClass, libfile)) {
		    return;
		}
	    }
	}
	// Oops, it failed
        throw new UnsatisfiedLinkError("no " + name + " in java.library.path");
    }

    private static boolean loadLibrary0(Class fromClass, final File file) {
	Boolean exists = (Boolean)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    return new Boolean(file.exists());
		}
	    });
	if (!exists.booleanValue()) {
	    return false;
	}
        String name;
	try {
	    name = file.getCanonicalPath();
	} catch (IOException e) {
	    return false;
	}
        ClassLoader loader =
	    (fromClass == null) ? null : fromClass.getClassLoader();
        Vector libs =
	    loader != null ? loader.nativeLibraries : systemNativeLibraries;
	synchronized (libs) {
	    int size = libs.size();
	    for (int i = 0; i < size; i++) {
	        NativeLibrary lib = (NativeLibrary)libs.elementAt(i);
		if (name.equals(lib.name)) {
		    return true;
		}
	    }

	    synchronized (loadedLibraryNames) {
	        if (loadedLibraryNames.contains(name)) {
		    throw new UnsatisfiedLinkError
		        ("Native Library " +
			 name +
			 " already loaded in another classloader");
		}
		/* If the library is being loaded (must be by
		 * the same thread, because Runtime.load and
		 * Runtime.loadLibrary are synchronous). The
		 * reason is can occur is that the JNI_OnLoad
		 * function can cause another loadLibrary call.
		 *
		 * Thus we can use a static stack to hold the list
		 * of libraries we are loading.
		 *
		 * If there is a pending load operation for the
		 * library, we immediately return success; otherwise,
		 * we raise UnsatisfiedLinkError.
		 */
		int n = nativeLibraryContext.size();
		for (int i = 0; i < n; i++) {
		    NativeLibrary lib = (NativeLibrary)
		        nativeLibraryContext.elementAt(i);
		    if (name.equals(lib.name)) {
		        if (loader == lib.fromClass.getClassLoader()) {
			    return true;
			} else {
			    throw new UnsatisfiedLinkError
			        ("Native Library " +
				 name +
				 " is being loaded in another classloader");
			}
		    }
		}
		NativeLibrary lib = new NativeLibrary(fromClass, name);
		nativeLibraryContext.push(lib);
		try {
		    lib.load(name);
		} finally {
		    nativeLibraryContext.pop();
		}
		if (lib.handle != 0) {
		    loadedLibraryNames.addElement(name);
		    libs.addElement(lib);
		    return true;
		}
		return false;
	    }
	}
    }

    /* Called in the VM class linking code. */
    static long findNative(ClassLoader loader, String name) {
        Vector libs =
	    loader != null ? loader.nativeLibraries : systemNativeLibraries;
	synchronized (libs) {
	    int size = libs.size();
	    for (int i = 0; i < size; i++) {
	        NativeLibrary lib = (NativeLibrary)libs.elementAt(i);
		long entry = lib.find(name);
		if (entry != 0)
		    return entry;
	    }
	}
	return 0;
    }

    /*
     * The default toggle for assertion checking.
     */
    private boolean defaultAssertionStatus = false;

    /*
     * Maps String packageName to Boolean package default assertion status
     * Note that the default package is placed under a null map key.
     * If this field is null then we are delegating assertion status queries
     * to the VM, i.e., none of this ClassLoader's assertion status
     * modification methods have been called.
     */
    private Map packageAssertionStatus = null;

    /*
     * Maps String fullyQualifiedClassName to Boolean assertionStatus
     * If this field is null then we are delegating assertion status queries
     * to the VM, i.e., none of this ClassLoader's assertion status
     * modification methods have been called.
     */
    Map classAssertionStatus = null;

    /**
     * Sets the default assertion status for this class loader.  This setting
     * determines whether classes loaded by this class loader and initialized
     * in the future will have assertions enabled or disabled by default.
     * This setting may be overridden on a per-package or per-class basis by
     * invoking {@link #setPackageAssertionStatus(String,boolean)} or {@link
     * #setClassAssertionStatus(String,boolean)}.
     *
     * @param enabled <tt>true</tt> if classes loaded by this class loader
     *        will henceforth have assertions enabled by default,
     *        <tt>false</tt> if they will have assertions disabled by default.
     * @since 1.4
     */
    public synchronized void setDefaultAssertionStatus(boolean enabled) {
        if (classAssertionStatus == null)
            initializeJavaAssertionMaps();

        defaultAssertionStatus = enabled;
    }

    /**
     * Sets the package default assertion status for the named
     * package.  The package default assertion status determines the
     * assertion status for classes initialized in the future that belong
     * to the named package or any of its "subpackages."
     * <p>
     * A subpackage of a package named p is any package whose name
     * begins with "p." .  For example, <tt>javax.swing.text</tt> is
     * a subpackage of <tt>javax.swing</tt>, and both <tt>java.util</tt>
     * and <tt>java.lang.reflect</tt> are subpackages of <tt>java</tt>.
     * <p>
     * In the event that multiple package defaults apply to a given
     * class, the package default pertaining to the most specific package
     * takes precedence over the others.  For example, if
     * <tt>javax.lang</tt> and <tt>javax.lang.reflect</tt> both have
     * package defaults associated with them, the latter package
     * default applies to classes in <tt>javax.lang.reflect</tt>.
     * <p>
     * Package defaults take precedence over the class loader's default
     * assertion status, and may be overridden on a per-class basis by
     * invoking {@link #setClassAssertionStatus(String,boolean)}.
     *
     * @param packageName the name of the package whose package default
     *        assertion status is to be set. A null value
     *        indicates the unnamed package that is "current"
     *        (JLS 7.4.2).
     * @param enabled <tt>true</tt> if classes loaded by this classloader
     *        and belonging to the named package or any of its subpackages
     *        will have assertions enabled by default, <tt>false</tt> if they
     *        will have assertions disabled by default.
     * @since 1.4
     */
    public synchronized void setPackageAssertionStatus(String packageName,
                                                       boolean enabled)
    {
        if (packageAssertionStatus == null)
            initializeJavaAssertionMaps();

        packageAssertionStatus.put(packageName, Boolean.valueOf(enabled));
    }

    /**
     * Sets the desired assertion status for the named top-level class in
     * this class loader and any nested classes contained therein.
     * This setting takes precedence over the  class loader's default
     * assertion status, and over any applicable per-package default.
     * This method has no effect if the named class has already been
     * initialized.  (Once a class is initialized, its assertion status cannot
     * change.)
     * <p>
     * If the named class is not a top-level class, this call will have no
     * effect on the actual assertion status of any class, and its return
     * value is undefined.
     *
     * @param className the fully qualified class name of the top-level class
     *        whose assertion status is to be set.
     * @param enabled <tt>true</tt> if the named class is to have assertions
     *        enabled when (and if) it is initialized, <tt>false</tt> if the
     *        class is to have assertions disabled.
     * @since 1.4
     */
    public synchronized void setClassAssertionStatus(String className,
                                                     boolean enabled)
    {
        if (classAssertionStatus == null)
            initializeJavaAssertionMaps();

        classAssertionStatus.put(className, Boolean.valueOf(enabled));
    }

    /**
     * Sets the default assertion status for this class loader to
     * <tt>false</tt> and discards any package defaults or class assertion
     * status settings associated with the class loader.  This call is
     * provided so that class loaders can be made to ignore any command line
     * or persistent assertion status settings and "start with a clean slate."
     *
     * @since 1.4
     */
    public synchronized void clearAssertionStatus() {
        /*
         * Whether or not "Java assertion maps" are initialized, set
         * them to empty maps, effectively ignoring any present settings.
         */
        classAssertionStatus = new HashMap();
        packageAssertionStatus = new HashMap();

        defaultAssertionStatus = false;
    }

    /**
     * Returns the assertion status that would be assigned to the specified
     * class if it were to be initialized at the time this method is invoked.
     * If the named class has had its assertion status set, the most recent
     * setting will be returned; otherwise, if any package default assertion
     * status pertains to this class, the most recent setting for the most
     * specific pertinent package default assertion status is returned;
     * otherwise, this class loader's default assertion status is returned.
     *
     * @param  className the fully qualified class name of the class whose
     *         desired assertion status is being queried.
     * @return the desired assertion status of the specified class.
     * @see    #setClassAssertionStatus(String,boolean)
     * @see    #setPackageAssertionStatus(String,boolean)
     * @see    #setDefaultAssertionStatus(boolean)
     * @since  1.4
     */
    synchronized boolean desiredAssertionStatus(String className) {
        Boolean result;

        // assert classAssertionStatus   != null;
        // assert packageAssertionStatus != null;

        // Check for a class entry
        result = (Boolean)classAssertionStatus.get(className);
        if (result != null)
            return result.booleanValue();

        // Check for most specific package entry
        int dotIndex = className.lastIndexOf(".");
        if (dotIndex < 0) { // default package
            result = (Boolean)packageAssertionStatus.get(null);
            if (result != null)
                return result.booleanValue();
        }
        while(dotIndex > 0) {
            className = className.substring(0, dotIndex);
            result = (Boolean)packageAssertionStatus.get(className);
            if (result != null)
                return result.booleanValue();
            dotIndex = className.lastIndexOf(".", dotIndex-1);
        }

        // Return the classloader default
        return defaultAssertionStatus;
    }

    // Set up the assertions with information provided by the VM.
    private void initializeJavaAssertionMaps() {
        // assert Thread.holdsLock(this);

        classAssertionStatus = new HashMap();
        packageAssertionStatus = new HashMap();
        AssertionStatusDirectives directives = retrieveDirectives();

        for(int i=0; i<directives.classes.length; i++)
            classAssertionStatus.put(directives.classes[i],
                              Boolean.valueOf(directives.classEnabled[i]));

        for(int i=0; i<directives.packages.length; i++)
            packageAssertionStatus.put(directives.packages[i],
                              Boolean.valueOf(directives.packageEnabled[i]));

        defaultAssertionStatus = directives.deflt;
    }

    // Retrieves the assertion directives from the VM.
    private static native AssertionStatusDirectives retrieveDirectives();

}


class SystemClassLoaderAction implements PrivilegedExceptionAction {
    private ClassLoader parent;

    SystemClassLoaderAction(ClassLoader parent) {
	this.parent = parent;
    }

    public Object run() throws Exception {
	ClassLoader sys;
	Constructor ctor;
	Class c;
	Class cp[] = { ClassLoader.class };
	Object params[] = { parent };

        String cls = System.getProperty("java.system.class.loader");
	if (cls == null) {
	    return parent;
	}

	c = Class.forName(cls, true, parent);
	ctor = c.getDeclaredConstructor(cp);
	sys = (ClassLoader) ctor.newInstance(params);
	Thread.currentThread().setContextClassLoader(sys);
	return sys;
    }
}
