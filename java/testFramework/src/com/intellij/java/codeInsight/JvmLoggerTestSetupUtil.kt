// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

object JvmLoggerTestSetupUtil {
  fun setupSlf4j(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
      package org.slf4j;
      
      interface Logger {}
    """.trimIndent())
    fixture.addClass("""
      package org.slf4j;
      
      interface LoggerFactory{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
  }

  fun setupLog4j2(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
      package org.apache.logging.log4j;
      
      interface Logger {}
    """.trimIndent())
    fixture.addClass("""
      package org.apache.logging.log4j;
      
      interface LogManager{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
  }

  fun setupLog4j(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
      package org.apache.log4j;
      
      interface Logger {
      static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
  }

  fun setupApacheCommons(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
    package org.apache.commons.logging;
    
    interface Log {
    }
  """.trimIndent())
    fixture.addClass("""
    package org.apache.commons.logging;
    
    interface LogFactory {
      static Log getLog(Class<?> clazz) {}
    }
  """.trimIndent())
  }
}