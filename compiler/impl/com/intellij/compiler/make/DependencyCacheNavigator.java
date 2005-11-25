/*
 * @author: Eugene Zhuravlev
 * Date: May 26, 2003
 * Time: 8:13:56 PM
 */
package com.intellij.compiler.make;

import com.intellij.openapi.diagnostic.Logger;

public class DependencyCacheNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.DependencyCacheNavigator");

  private final Cache myCache;
  private final DependencyCache myDepCache;

  public DependencyCacheNavigator(Cache cache, DependencyCache depCache) {
    myCache = cache;
    myDepCache = depCache;
  }

  public void walkSuperClasses(int classQName, ClassInfoProcessor processor) throws CacheCorruptedException {
    final int classId = myCache.getClassId(classQName);
    if (classId == Cache.UNKNOWN) {
      return;
    }
    int superQName = myCache.getSuperQualifiedName(classId);

    if (classQName == superQName) {
      LOG.assertTrue(false, "Superclass qualified name is the same as class' name: " + classQName);
      return;
    }

    if (superQName != Cache.UNKNOWN) {
      int superInfoId = myCache.getClassId(superQName);
      if (superInfoId != Cache.UNKNOWN) {
        if (processor.process(superQName)) {
          walkSuperClasses(superQName, processor);
        }
      }
    }
    int[] superInterfaces = myCache.getSuperInterfaces(classId);
    for (int superInterfaceQName : superInterfaces) {
      int superInfoId = myCache.getClassId(superInterfaceQName);
      if (superInfoId != Cache.UNKNOWN) {
        if (processor.process(superInterfaceQName)) {
          walkSuperClasses(superInterfaceQName, processor);
        }
      }
    }
  }

  public void walkSubClasses(int fromClassQName, ClassInfoProcessor processor) throws CacheCorruptedException {
    final int[] subclasses = myCache.getSubclasses(myCache.getClassId(fromClassQName));
    for (int subQName : subclasses) {
      if (fromClassQName == subQName) {
        LOG.assertTrue(false, "Subclass qualified name is the same as class' name: " + fromClassQName);
        return;
      }
      int subclassId = myCache.getClassId(subQName);
      if (subclassId != Cache.UNKNOWN) {
        if (!processor.process(subQName)) {
          break;
        }
      }
    }
  }

}
