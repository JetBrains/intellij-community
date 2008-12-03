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

  public DependencyCacheNavigator(Cache cache) {
    myCache = cache;
  }

  public void walkSuperClasses(int classQName, ClassInfoProcessor processor) throws CacheCorruptedException {
    if (classQName == Cache.UNKNOWN) {
      return;
    }
    int superQName = myCache.getSuperQualifiedName(classQName);

    if (classQName == superQName) {
      LOG.assertTrue(false, "Superclass qualified name is the same as class' name: " + classQName);
      return;
    }

    if (superQName != Cache.UNKNOWN) {
      if (processor.process(superQName)) {
        walkSuperClasses(superQName, processor);
      }
    }
    for (int superInterfaceQName : myCache.getSuperInterfaces(classQName)) {
      if (processor.process(superInterfaceQName)) {
        walkSuperClasses(superInterfaceQName, processor);
      }
    }
  }

  public void walkSuperInterfaces(int classQName, ClassInfoProcessor processor) throws CacheCorruptedException {
    if (classQName == Cache.UNKNOWN) {
      return;
    }

    for (int superInterfaceQName : myCache.getSuperInterfaces(classQName)) {
      if (processor.process(superInterfaceQName)) {
        walkSuperInterfaces(superInterfaceQName, processor);
      }
    }
  }

  public void walkSubClasses(int fromClassQName, ClassInfoProcessor processor) throws CacheCorruptedException {
    for (int subQName : myCache.getSubclasses(fromClassQName)) {
      if (fromClassQName == subQName) {
        LOG.assertTrue(false, "Subclass qualified name is the same as class' name: " + fromClassQName);
        return;
      }
      if (subQName != Cache.UNKNOWN) {
        if (!processor.process(subQName)) {
          break;
        }
      }
    }
  }

}
