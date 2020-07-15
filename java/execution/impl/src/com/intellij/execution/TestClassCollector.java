// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathsList;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TestClassCollector {

  private static final Logger LOG = Logger.getInstance(TestClassCollector.class);

  public static String[] collectClassFQNames(String packageName,
                                             @Nullable Path rootPath,
                                             JavaTestConfigurationBase configuration,
                                             Function<? super ClassLoader, ? extends Predicate<Class<?>>> predicateProducer) {
    Module module = configuration.getConfigurationModule().getModule();
    ClassLoader classLoader = createUsersClassLoader(configuration);
    Set<String> classes = new HashSet<>();
    try {
      String packagePath = packageName.replace('.', '/');
      Enumeration<URL> resources = classLoader.getResources(packagePath);

      Predicate<Class<?>> classPredicate = predicateProducer.apply(classLoader);
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();

        //don't search for tests in jars
        if ("jar".equals(url.getProtocol())) continue;

        Path baseDir = Paths.get(url.toURI());

        //collect tests under single module test output only
        if (rootPath != null && !baseDir.startsWith(rootPath)) continue;

        String pathSeparator = baseDir.getFileSystem().getSeparator();
        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            ProgressManager.checkCanceled();
            FileVisitResult result = super.visitFile(file, attrs);
            File f = file.toFile();
            String fName = f.getName();
            if (fName.endsWith(".class")) {
              try {
                Path relativePath = baseDir.relativize(file.getParent());
                String subpackageName = StringUtil.getQualifiedName(relativePath.toString().replace(pathSeparator, "."),
                                                                    FileUtilRt.getNameWithoutExtension(fName));
                String fqName = StringUtil.getQualifiedName(packageName, subpackageName);
                Class<?> aClass = Class.forName(fqName, false, classLoader);
                //is potential junit 4
                int modifiers = aClass.getModifiers();
                if (Modifier.isAbstract(modifiers) ||
                    !Modifier.isPublic(modifiers) ||
                   aClass.isMemberClass() && !Modifier.isStatic(modifiers)) {
                  return result;
                }
                if (classPredicate.test(aClass)) {
                  classes.add(fqName);
                }
              }
              catch (Throwable e) {
                LOG.info("error processing: " + fName + " of " + baseDir.toString(), e);
              }
            }
            return result;
          }
        });
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    return ArrayUtilRt.toStringArray(classes);
  }

  public static ClassLoader createUsersClassLoader(JavaTestConfigurationBase configuration) {
    Module module = configuration.getConfigurationModule().getModule();
    List<URL> urls = new ArrayList<>();

    PathsList pathsList = ReadAction
      .compute(() -> (module == null || configuration.getTestSearchScope() == TestSearchScope.WHOLE_PROJECT ? OrderEnumerator
        .orderEntries(configuration.getProject()) : OrderEnumerator.orderEntries(module))
      .runtimeOnly().recursively().getPathsList()); //include jdk to avoid NoClassDefFoundError for classes inside tools.jar
    for (VirtualFile file : pathsList.getVirtualFiles()) {
      try {
        urls.add(VfsUtilCore.virtualToIoFile(file).toURI().toURL());
      }
      catch (MalformedURLException ignored) {
        LOG.info(ignored);
      }
    }

    return UrlClassLoader.build().allowLock().useCache().urls(urls).get();
  }

  public static VirtualFile @Nullable [] getRootPath(Module module, final boolean chooseSingleModule) {
    if (chooseSingleModule) {
      return OrderEnumerator.orderEntries(module)
        .withoutSdk()
        .withoutLibraries()
        .withoutDepModules()
        .classes()
        .getRoots();
    }
    return null;
  }
}
