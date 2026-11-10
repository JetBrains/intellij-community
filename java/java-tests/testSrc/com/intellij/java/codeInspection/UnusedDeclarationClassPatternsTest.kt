/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.reference.RefClass
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class UnusedDeclarationClassPatternsTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testClassPattern() {
    val unusedDeclarationInspection = UnusedDeclarationInspection(true)
    myFixture.enableInspections(unusedDeclarationInspection)
    val classPattern = EntryPointsManagerBase.ClassPattern()
    classPattern.hierarchically = true
    classPattern.pattern = "java.lang.Runnable"
    val patterns = EntryPointsManagerBase.getInstance(myFixture.project).patterns
    try {
      patterns.add(classPattern)
      myFixture.configureByText("C.java", "public abstract class C implements Runnable {}")
      myFixture.checkHighlighting()
    }
    finally {
      patterns.remove(classPattern)
      myFixture.disableInspections(unusedDeclarationInspection)
    }
  }

  @Test
  fun testMethodPattern() {
    val unusedDeclarationInspection = UnusedDeclarationInspection(true)
    myFixture.enableInspections(unusedDeclarationInspection)
    val classPattern = EntryPointsManagerBase.ClassPattern()
    classPattern.pattern = "C"
    classPattern.method = "*"
    val patterns = EntryPointsManagerBase.getInstance(myFixture.project).patterns
    try {
      patterns.add(classPattern)
      myFixture.configureByText("C.java", "public abstract class C { void foo() {} public static void main(String[] args) {}}")
      myFixture.checkHighlighting()
    }
    finally {
      patterns.remove(classPattern)
      myFixture.disableInspections(unusedDeclarationInspection)
    }
  }

  @Test
  fun testMethodPattern1() {
    val unusedDeclarationInspection = UnusedDeclarationInspection(true)
    myFixture.enableInspections(unusedDeclarationInspection)
    val classPattern = EntryPointsManagerBase.ClassPattern()
    classPattern.pattern = "C"
    classPattern.method = "foo*"
    val patterns = EntryPointsManagerBase.getInstance(myFixture.project).patterns
    try {
      patterns.add(classPattern)
      myFixture.configureByText("C.java", "public abstract class C { void fooBar() {} public static void main() {}}")
      myFixture.checkHighlighting()
    }
    finally {
      patterns.remove(classPattern)
      myFixture.disableInspections(unusedDeclarationInspection)
    }
  }

  @Test
  fun testNoClassPattern() {
    val unusedDeclarationInspection = UnusedDeclarationInspection(true)
    try {
      myFixture.enableInspections(unusedDeclarationInspection)
      myFixture.configureByText("C.java", "public abstract class <warning descr=\"Class 'C' is never used\">C</warning> implements Runnable {}")
      myFixture.checkHighlighting()
    }
    finally {
      myFixture.disableInspections(unusedDeclarationInspection)
    }
  }

  @Test
  fun testAddEntryPoint() {
    val aClass = myFixture.addClass("public class Foo {}")
    val entryPointsManager = EntryPointsManagerBase.getInstance(myFixture.project)
    val context = (InspectionManager.getInstance(myFixture.project) as InspectionManagerEx).createNewGlobalContext()
    context.currentScope =  AnalysisScope(myFixture.project)
    context.initializeTools(ArrayList(), ArrayList(), ArrayList())
    
    try {
      val refClass = context.refManager.getReference(aClass)
      assertNotNull(refClass)
      val patterns = entryPointsManager.patterns
      assertThat(patterns).isNullOrEmpty()

      //add class as entry point
      entryPointsManager.addEntryPoint(refClass!!, true)
      assertEquals(1, patterns.size)
      assertEquals("Foo", patterns.iterator().next().pattern)

      //remove class entry point with constructors - ensure nothing is left in the entries
      entryPointsManager.removeEntryPoint(refClass)
      for (constructor in (refClass as RefClass).constructors) {
        entryPointsManager.removeEntryPoint(constructor)
      }

      assertThat(patterns).isNullOrEmpty()
      assertThat(entryPointsManager.getEntryPoints(context.refManager)).isNullOrEmpty()
    }
    finally {
      context.cleanup()
    }
  }

  @Test
  fun testAddRemoveMethodEntryPoint() {
    val aClass = myFixture.addClass("public class Foo {void foo(){}}")
    val entryPointsManager = EntryPointsManagerBase.getInstance(myFixture.project)
    val context = (InspectionManager.getInstance(myFixture.project) as InspectionManagerEx).createNewGlobalContext()
    context.initializeTools(ArrayList(),ArrayList(),ArrayList())
    try {
      val refMethod = context.refManager.getReference(runReadActionBlocking { aClass.methods[0] })
      assertNotNull(refMethod)
      val refClass = context.refManager.getReference(aClass)
      assertNotNull(refClass)
      val patterns = entryPointsManager.patterns
      assertThat(patterns).isNullOrEmpty()

      entryPointsManager.addEntryPoint(refMethod!!, true)
      assertEquals(1, patterns.size)
      val classPattern = patterns.iterator().next()
      assertEquals("Foo", classPattern.pattern)
      assertEquals("foo", classPattern.method)

      entryPointsManager.removeEntryPoint(refMethod)

      assertThat(patterns).isNullOrEmpty()
      assertThat(entryPointsManager.getEntryPoints(context.refManager)).isNullOrEmpty()
    }
    finally {
      context.cleanup()
    }
  }
}