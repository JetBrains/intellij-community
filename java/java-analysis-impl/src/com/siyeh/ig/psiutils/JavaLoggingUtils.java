// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class JavaLoggingUtils {

  public static final String JAVA_LOGGING = "java.util.logging.Logger";
  public static final String JAVA_LOGGING_FACTORY = JAVA_LOGGING;
  public static final String SLF4J = "org.slf4j.Logger";
  public static final String SLF4J_FACTORY = "org.slf4j.LoggerFactory";
  public static final String COMMONS_LOGGING = "org.apache.commons.logging.Log";
  public static final String COMMONS_LOGGING_FACTORY = "org.apache.commons.logging.LogFactory";
  public static final String LOG4J = "org.apache.log4j.Logger";
  public static final String LOG4J_FACTORY = LOG4J;
  public static final String LOG4J2 = "org.apache.logging.log4j.Logger";
  public static final String LOG4J2_FACTORY = "org.apache.logging.log4j.LogManager";

  public static final @Unmodifiable List<String> DEFAULT_LOGGERS = List.of(JAVA_LOGGING, SLF4J, COMMONS_LOGGING, LOG4J, LOG4J2);

  private JavaLoggingUtils() {}
}
