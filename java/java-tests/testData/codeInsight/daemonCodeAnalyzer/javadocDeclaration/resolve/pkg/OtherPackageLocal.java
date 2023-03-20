package pkg;

/**
 * @see pkg1.PackageLocal                      - package local class in other package should resolve
 * @see java.io.ObjectStreamClass.WeakClassKey - and JDK package local class too
 * @see java.io.ObjectStreamClass.Caches       - and even JDK private classes
 *
 * - but don't go over the top, of course:
 * @see java.io.ObjectStreamClass.<error descr="Cannot resolve symbol 'java.io.ObjectStreamClass.XXXXXX'">XXXXXX</error>
 */
class JavadocMustResolveEvenOtherPackageLocalClasses {
  
}

