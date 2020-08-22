// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.compiler;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
  * MUST BE COMPILED WITH JDK 1.1 IN ORDER TO SUPPORT JAVAC LAUNCHING FOR ALL JDKs
  * @author Eugene Zhuravlev
  */
public final class JavacResourcesReader {
  public static final String MSG_PATTERNS_START = "__patterns_start";
  public static final String MSG_PATTERNS_END = "__patterns_end";
  public static final String MSG_PARSING_STARTED = "MSG_PARSING_STARTED";
  public static final String MSG_PARSING_COMPLETED = "MSG_PARSING_COMPLETED";
  public static final String MSG_LOADING = "MSG_LOADING";
  public static final String MSG_CHECKING = "MSG_CHECKING";
  public static final String MSG_WROTE = "MSG_WROTE";
  public static final String MSG_WARNING = "MSG_WARNING";
  public static final String MSG_NOTE = "MSG_NOTE";
  public static final String MSG_STATISTICS = "MSG_STATISTICS";
  public static final String MSG_IGNORED = "MSG_IGNORED";

  private static final String[] BUNDLE_NAMES = new String[] {
    "com.sun.tools.javac.resources.compiler",    // v1.5
    "com.sun.tools.javac.v8.resources.compiler", // v1.3-1.4
    "sun.tools.javac.resources.javac"            // v1.1-1.2
  };

  private static final BundleKey[] MSG_NAME_KEY_PAIRS = new BundleKey[] {
    new BundleKey(MSG_PARSING_STARTED, "compiler.misc.verbose.parsing.started"),
    new BundleKey(MSG_PARSING_COMPLETED, "compiler.misc.verbose.parsing.done"),
    new BundleKey(MSG_PARSING_COMPLETED, "benv.parsed_in"), // jdk 1.1-1.2
    new BundleKey(MSG_LOADING, "compiler.misc.verbose.loading"),
    new BundleKey(MSG_LOADING, "benv.loaded_in"), // jdk 1.1-1.2
    new BundleKey(MSG_CHECKING, "compiler.misc.verbose.checking.attribution"),
    new BundleKey(MSG_WROTE,"compiler.misc.verbose.wrote.file"),
    new BundleKey(MSG_WROTE,"main.wrote"), // jdk 1.1-1.2
    new BundleKey(MSG_WARNING,"compiler.warn.warning"),
    new BundleKey(MSG_NOTE,new String[] {"compiler.note.note", "compiler.note.deprecated.filename"}),  // jdk 1.5
    new BundleKey(MSG_NOTE,new String[] {"compiler.note.note", "compiler.note.deprecated.plural"}),  // jdk 1.5
    new BundleKey(MSG_NOTE,new String[] {"compiler.note.note", "compiler.note.deprecated.recompile"}),  // jdk 1.5
    new BundleKey(MSG_NOTE,new String[] {"compiler.note.note", "compiler.note.unchecked.filename"}),  // jdk 1.5
    new BundleKey(MSG_NOTE,new String[] {"compiler.note.note", "compiler.note.unchecked.plural"}),  // jdk 1.5
    new BundleKey(MSG_NOTE,new String[] {"compiler.note.note", "compiler.note.unchecked.recompile"}),  // jdk 1.5
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.error"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.error.plural"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.warn"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.warn.plural"),
    new BundleKey(MSG_STATISTICS,"main.errors"), //jdk 1.1 - 1.2
    new BundleKey(MSG_STATISTICS,"main.warnings"), //jdk 1.1 - 1.2
    new BundleKey(MSG_STATISTICS,"main.1error"), //jdk 1.1 - 1.2
    new BundleKey(MSG_STATISTICS,"main.1warning"), //jdk 1.1 - 1.2
    new IgnoredWarningBundleKey("compiler.warn.dir.path.element.not.found"), //jdk 1.5
    new IgnoredWarningBundleKey("compiler.warn.path.element.not.found"), //jdk 1.5
  };

  public static final String CATEGORY_VALUE_DIVIDER = "=";

  public static void main(String[] args) {
    dumpPatterns();
  }

  // for debug purposes
  /*
  public static void printPatterns() {
    final ResourceBundle messagesBundle = getMessagesBundle();
    if (messagesBundle == null) {
      System.out.println("No bundles found");
      return;
    }
    final Enumeration keys = messagesBundle.getKeys();
    while (keys.hasMoreElements()) {
      final Object key = keys.nextElement();
      System.out.println(key + "->" + messagesBundle.getObject((String)key));
    }
  }
  */

  public static boolean dumpPatterns() {
    final ResourceBundle messagesBundle = getMessagesBundle();
    if (messagesBundle == null) {
      return false;
    }
    System.err.println(MSG_PATTERNS_START);
    for (BundleKey bundleKey : MSG_NAME_KEY_PAIRS) {
      try {
        System.err.println(bundleKey.category + CATEGORY_VALUE_DIVIDER + bundleKey.getCategoryValue(messagesBundle));
      }
      catch (MissingResourceException ignored) {
      }
    }
    System.err.println(MSG_PATTERNS_END);
    return true;
  }

  private static ResourceBundle getMessagesBundle() {
    for (String name : BUNDLE_NAMES) {
      try {
        return ResourceBundle.getBundle(name);
      }
      catch (MissingResourceException ignored) {
      }
    }
    return null;
  }

  private static class BundleKey {
    public final String category;
    public final String[] keys;

    BundleKey(final String category, final String key) {
      this(category, new String[] {key});
    }

    BundleKey(final String category, final String[] composite) {
      this.category = category;
      this.keys = composite;
    }

    public String getCategoryValue(ResourceBundle messagesBundle) {
      if (keys.length == 1) {
        return messagesBundle.getString(keys[0]);
      }
      final StringBuilder buf = new StringBuilder();
      for (String key : keys) {
        buf.append(messagesBundle.getString(key));
      }
      return buf.toString();
    }
  }

  private static class IgnoredWarningBundleKey extends BundleKey {
    IgnoredWarningBundleKey(final String messageKey) {
      super(MSG_IGNORED, new String[]{"compiler.warn.warning", messageKey});
    }

    public String getCategoryValue(ResourceBundle messagesBundle) {
      return messagesBundle.getString(keys[0]) + MessageFormat.format(messagesBundle.getString(keys[1]), "");
    }
  }
}
