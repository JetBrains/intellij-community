package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;

public class SnappyInitializer {
  public static final boolean NO_SNAPPY = SystemProperties.getBooleanProperty("idea.no.snappy", false);

  public static void initializeSnappy(Logger log, File ideaTempDir) {
    if (!NO_SNAPPY) {
      if (System.getProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR) == null) {
        System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, ideaTempDir.getPath());
      }
      try {
        final long t = System.currentTimeMillis();
        loadSnappyForJRockit();
        log.info("Snappy library loaded (" + Snappy.getNativeLibraryVersion() + ") in " + (System.currentTimeMillis() - t) + " ms");
      }
      catch (Throwable t) {
        log.error("Unable to load Snappy library" + " (OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION + ")", t);
      }
    }
  }

  // todo[r.sh] drop after migration on Java 7
  private static void loadSnappyForJRockit() throws Exception {
    String vmName = System.getProperty("java.vm.name");
    if (vmName == null || !vmName.toLowerCase().contains("jrockit")) {
      return;
    }

    byte[] bytes;
    InputStream in = SnappyInitializer.class.getResourceAsStream("/org/xerial/snappy/SnappyNativeLoader.bytecode");
    try {
      bytes = FileUtil.loadBytes(in);
    }
    finally {
      in.close();
    }

    ClassLoader classLoader = SnappyInitializer.class.getClassLoader();

    Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
    defineClass.setAccessible(true);
    Class<?> loaderClass = (Class<?>)defineClass.invoke(classLoader, "org.xerial.snappy.SnappyNativeLoader", bytes, 0, bytes.length);
    loaderClass = classLoader.loadClass(loaderClass.getName());

    String[] classes = {"org.xerial.snappy.SnappyNativeAPI", "org.xerial.snappy.SnappyNative", "org.xerial.snappy.SnappyErrorCode"};
    for (String aClass : classes) {
      classLoader.loadClass(aClass);
    }

    Method loadNativeLibrary = SnappyLoader.class.getDeclaredMethod("loadNativeLibrary", Class.class);
    loadNativeLibrary.setAccessible(true);
    loadNativeLibrary.invoke(null, loaderClass);
  }
}
