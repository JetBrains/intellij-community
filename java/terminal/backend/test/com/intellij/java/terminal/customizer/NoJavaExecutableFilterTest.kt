// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.customizer

import com.intellij.java.terminal.backend.NoJavaExecutableFilter
import org.junit.jupiter.api.Test

/** Verifies the filtering of [NoJavaExecutableFilter] to avoid false positives/negative */
internal class NoJavaExecutableFilterTest {
  private val myFilter: NoJavaExecutableFilter = NoJavaExecutableFilter()

  @Test
  fun `test apt suggestions`() {
    doTest("""
      ❯ java
      Command 'java' not found, but can be installed with:
      sudo apt install openjdk-17-jre-headless  # version 17.0.18+8-1~24.04.1, or
      sudo apt install openjdk-21-jre-headless  # version 21.0.10+7-1~24.04
      sudo apt install default-jre              # version 2:1.17-75
      sudo apt install openjdk-19-jre-headless  # version 19.0.2+7-4
      sudo apt install openjdk-20-jre-headless  # version 20.0.2+9-1
      sudo apt install openjdk-22-jre-headless  # version 22~22ea-1
      sudo apt install openjdk-11-jre-headless  # version 11.0.30+7-1ubuntu1~24.04
      sudo apt install openjdk-25-jre-headless  # version 25.0.2+10-1~24.04
      sudo apt install openjdk-8-jre-headless   # version 8u482-ga~us1-0ubuntu1~24.04
    """.trimIndent(), listOf(1))
  }

  @Test
  fun `test apt suggestions but in German`() {
    doTest("""
      Der Befehl 'java' wurde nicht gefunden, kann aber installiert werden mit:
      sudo apt install openjdk-17-jre-headless  # version 17.0.18+8-1~24.04.1, or
      sudo apt install openjdk-21-jre-headless  # version 21.0.10+7-1~24.04
      sudo apt install default-jre              # version 2:1.17-75
      sudo apt install openjdk-19-jre-headless  # version 19.0.2+7-4
      sudo apt install openjdk-20-jre-headless  # version 20.0.2+9-1
      sudo apt install openjdk-22-jre-headless  # version 22~22ea-1
      sudo apt install openjdk-11-jre-headless  # version 11.0.30+7-1ubuntu1~24.04
      sudo apt install openjdk-25-jre-headless  # version 25.0.2+10-1~24.04
      sudo apt install openjdk-8-jre-headless   # version 8u482-ga~us1-0ubuntu1~24.04
    """.trimIndent(), expectedResultLines = listOf(0))
  }
  
  @Test
  fun `test apt suggestions but in Spanish`() {
    doTest("""
      ❯ java
      No se ha encontrado la orden «java», pero se puede instalar con:
      sudo apt install openjdk-17-jre-headless  # version 17.0.18+8-1~24.04.1, or
      sudo apt install openjdk-21-jre-headless  # version 21.0.10+7-1~24.04
      sudo apt install default-jre              # version 2:1.17-75
      sudo apt install openjdk-19-jre-headless  # version 19.0.2+7-4
      sudo apt install openjdk-20-jre-headless  # version 20.0.2+9-1
      sudo apt install openjdk-22-jre-headless  # version 22~22ea-1
      sudo apt install openjdk-11-jre-headless  # version 11.0.30+7-1ubuntu1~24.04
      sudo apt install openjdk-25-jre-headless  # version 25.0.2+10-1~24.04
      sudo apt install openjdk-8-jre-headless   # version 8u482-ga~us1-0ubuntu1~24.04
    """.trimIndent(), listOf(1))
  }
  
  @Test
  fun `test zsh`() {
    doTest("""
      ➜  / java
      zsh: command not found: java
    """.trimIndent(), listOf(1))
  }
  
  @Test
  fun `test bash`() {
    doTest("""
      root@b15d0517e88e:/# java
      bash: java: command not found
    """.trimIndent(), listOf(1))
  }
  
  @Test
  fun `test fish`() {
    doTest("""
      root@9082feacf9a4 /# java
      fish: Unknown command: java
    """.trimIndent(), listOf(1))
  }
  
  @Test
  fun `test powershell`() {
    doTest("""
      PowerShell 7.6.0
      PS C:\Users\Admin\Desktop> java
      java: The term 'foo' is not recognized as a name of a cmdlet, function, script file, or executable program.
      Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
    """.trimIndent(), listOf(2))
  }
  
  @Test
  fun `test powershell but in chinese`() {
    doTest("""
      PS D:\github\beansoft\CodeNotesRider> java
      java : 无法将“java”项识别为 cmdlet、函数、脚本文件或可运行程序的名称。请检查名称的拼写，如果包括路径，请确保路径正确，然后再试一次。
      所在位置 行:1 字符: 1
      + java
      + ~~~
          + CategoryInfo          : ObjectNotFound: (java:String) [], CommandNotFoundException
          + FullyQualifiedErrorId : CommandNotFoundException
    """.trimIndent(), listOf(1))
  }

  /** 
   * Run the filter through the lines
   * @param expectedResultLines On which line a result is expected
   */
  private fun doTest(input: String, expectedResultLines: List<Int>) {
    val expectedResultLineIterator = expectedResultLines.iterator()
    var expectedResultLine = expectedResultLineIterator.nextOrNull()

    val lines = input.lines()
    var totalLength = 0
    for ((i, line) in lines.withIndex()) {
      totalLength += line.length + 1 // '\n'
      val result = myFilter.applyFilter("$line\n", totalLength)

      if (result == null) {
        if (expectedResultLine == i) {
          throw AssertionError("Result missing from line: \"$line\"")
        } 
      } else {
         if (expectedResultLine == null || expectedResultLine != i) {
           throw AssertionError("Unexpected result on line: \"$line\", expected: $expectedResultLine")   
         }
        expectedResultLine = expectedResultLineIterator.nextOrNull()
      }
    }
  }

  private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null
}
