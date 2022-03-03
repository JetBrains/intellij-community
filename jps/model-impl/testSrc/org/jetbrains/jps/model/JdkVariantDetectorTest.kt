// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.jps.model.java.JdkVersionDetector.Variant.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class JdkVariantDetectorTest {
  @Suppress("SpellCheckingInspection") private val RELEASE_ORACLE_OPEN_1_8_0_41 =
    """|JAVA_VERSION="1.8.0_41"
       |OS_NAME="Windows"
       |OS_VERSION="5.1"
       |OS_ARCH="i586"
       |SOURCE=""
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val MANIFEST_ORACLE_OPEN_1_8_0_41 =
    """|Manifest-Version: 1.0
       |Implementation-Vendor: N/A
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_41
       |Specification-Vendor: Oracle Corporation
       |Created-By: 1.7.0_07 (Oracle Corporation)
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_ORACLE_1_8_0_291 =
    """|JAVA_VERSION="1.8.0_291"
       |OS_NAME="Linux"
       |OS_VERSION="2.6"
       |OS_ARCH="amd64"
       |SOURCE=" .:06b604e7edd4 hotspot:7c09263ba3e2 hotspot/src/closed:573e10ede63c ..."
       |BUILD_TYPE="commercial"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val MANIFEST_ORACLE_1_8_0_291 =
    """|Manifest-Version: 1.0
       |Implementation-Vendor: Oracle Corporation
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_291
       |Specification-Vendor: Oracle Corporation
       |Created-By: 1.7.0_07 (Oracle Corporation)
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_ORACLE_16_0_1 =
    """|IMPLEMENTOR="Oracle Corporation"
       |JAVA_VERSION="16.0.1"
       |JAVA_VERSION_DATE="2021-04-20"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Windows"
       |SOURCE=".:git:ba7c640201ba"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_ADOPT_HOTSPOT_1_8_0_282 =
    """|JAVA_VERSION="1.8.0_282"
       |OS_NAME="Darwin"
       |OS_VERSION="11.2"
       |OS_ARCH="x86_64"
       |SOURCE=" .:OpenJDK: bece19ab82:"
       |IMPLEMENTOR="AdoptOpenJDK"
       |BUILD_SOURCE="git:10223734"
       |FULL_VERSION="1.8.0_282-b08"
       |SEMANTIC_VERSION="8.0.282+8"
       |BUILD_INFO="OS: Mac OS X Version: 10.14.6 18G84"
       |JVM_VARIANT="Hotspot"
       |JVM_VERSION="25.282-b08"
       |IMAGE_TYPE="JDK"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_ADOPT_J9_11_0_10 =
    """|IMPLEMENTOR="AdoptOpenJDK"
       |IMPLEMENTOR_VERSION="AdoptOpenJDK"
       |JAVA_VERSION="11.0.10"
       |JAVA_VERSION_DATE="2021-01-19"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE="OpenJDK:0a86953833 OpenJ9:345e1b09e OMR:741e94ea8"
       |BUILD_SOURCE="git:10223734"
       |FULL_VERSION="11.0.10+9"
       |SEMANTIC_VERSION="11.0.10+9"
       |BUILD_INFO="OS: Mac OS X Version: 10.14.6 18G84"
       |JVM_VARIANT="Openj9"
       |JVM_VERSION="openj9-0.24.0"
       |HEAP_SIZE="Standard"
       |IMAGE_TYPE="JDK"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_CORRETTO_1_8_0_292 =
    """|JAVA_VERSION="1.8.0_292"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=""
       |LIBC=""
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val MANIFEST_CORRETTO_1_8_0_292 =
    """|Manifest-Version: 1.0
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_292
       |Specification-Vendor: Oracle Corporation
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
       |Created-By: 1.8.0_222 (Amazon.com Inc.)
       |Implementation-Vendor: Amazon.com Inc.
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_CORRETTO_11_0_8_10_1 =
    """|IMPLEMENTOR="Amazon.com Inc."
       |IMPLEMENTOR_VERSION="Corretto-11.0.8.10.1"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-14"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=""
    """.trimMargin()

  private val RELEASE_LIBERICA_11_0_8 =
    """|IMPLEMENTOR="BellSoft"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-14"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:hg:030bc020dc04+"
    """.trimMargin()

  private val RELEASE_SAP_MACHINE_11_0_8 =
    """|IMPLEMENTOR="SAP SE"
       |IMPLEMENTOR_VERSION="SapMachine"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-15"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:git:21ef36a0f46a+"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_ZULU_11_0_8 =
    """|IMPLEMENTOR="Azul Systems, Inc."
       |IMPLEMENTOR_VERSION="Zulu11.41+23-CA"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-14"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:hg:5b0e54350bbc"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_ZULU_1_8_0_292 =
    """|JAVA_VERSION="1.8.0_292"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=" .:ddbdd8cb2baa hotspot:19eb9031626c ..."
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val MANIFEST_ZULU_1_8_0_292 =
    """|Manifest-Version: 1.0
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_292
       |Specification-Vendor: Oracle Corporation
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
       |Created-By: 1.8.0_282 (Azul Systems, Inc.)
       |Implementation-Vendor: N/A
    """.trimMargin()

  private val RELEASE_JBR_11_0_10 =
    """|MODULES="java.base ..."
       |IMPLEMENTOR="JetBrains s.r.o."
       |SOURCE=".\:git\:58e267d9f35a+ jcef_git\:git\:e09d9c4a783e"
       |OS_ARCH="x86_64"
       |IMPLEMENTOR_VERSION="JBR-11.0.10.9-1304.4-dcevm"
       |OS_NAME="Windows"
       |JAVA_VERSION="11.0.10"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_IBM_1_8_0_291 =
    """|JAVA_VERSION="1.8.0_291"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=""
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val MANIFEST_IBM_1_8_0_291 =
    """|Manifest-Version: 1.0
       |Ant-Version: Apache Ant 1.7.1
       |Created-By: 1.8.0 (IBM Corporation)
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0
       |Specification-Vendor: Oracle Corporation
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
       |Implementation-Vendor: IBM
       |Build-level: 20210507_01
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_IBM_11_0_11 =
    """|IMPLEMENTOR="IBM Corporation"
       |IMPLEMENTOR_VERSION="11.0.11.0-IBM"
       |JAVA_VERSION="11.0.11"
       |JAVA_VERSION_DATE="2021-04-20"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Linux"
       |SOURCE="OpenJDK:9110d0b174 OpenJ9:b4cc246d9 OMR:162e6f729"
       |BUILD_SOURCE="git:efe243d"
       |FULL_VERSION="11.0.11+9"
       |SEMANTIC_VERSION="11.0.11+9"
       |BUILD_INFO="OS: Linux Version: 3.10.0-327.el7.x86_64"
       |JVM_VARIANT="Openj9"
       |JVM_VERSION="openj9-0.26.0"
       |HEAP_SIZE="Standard"
       |IMAGE_TYPE="JDK"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_GRAALVM_1_8_0_292 =
    """|JAVA_VERSION="1.8.0_292"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=" substratevm:dc4d2d6bdda1e7262bbae3291475e02fd498f382 truffle:dc4d2d6bdda1e7262bbae3291475e02fd498f382 ..."
       |GRAALVM_VERSION="21.1.0"
       |COMMIT_INFO={}
       |component_catalog="..."
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_GRAALVM_16_0_1 =
    """|IMPLEMENTOR="GraalVM Community"
       |JAVA_VERSION="16.0.1"
       |JAVA_VERSION_DATE="2021-04-20"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Windows"
       |SOURCE=".:git:8387de06b765+ substratevm:dc4d2d6bdda1e7262bbae3291475e02fd498f382 truffle:dc4d2d6bdda1e7262bbae3291475e02fd498f382 ..."
       |GRAALVM_VERSION="21.1.0"
       |COMMIT_INFO={}
       |component_catalog="..."
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_SEMERU_16_0_2 =
    """|IMPLEMENTOR="International Business Machines Corporation"
       |IMPLEMENTOR_VERSION="16.0.2.0"
       |JAVA_VERSION="16.0.2"
       |JAVA_VERSION_DATE="2021-07-20"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE="OpenJDK:34df42439f3 OpenJ9:1851b0074 OMR:9db1c870d"
       |BUILD_SOURCE="git:03546ff"
       |FULL_VERSION="16.0.2+7"
       |SEMANTIC_VERSION="16.0.2+7"
       |BUILD_INFO="OS: Mac OS X Version: 10.14.6 18G9216"
       |JVM_VARIANT="Openj9"
       |JVM_VERSION="openj9-0.27.0"
       |HEAP_SIZE="Standard"
       |IMAGE_TYPE="JDK"
    """.trimMargin()

  @Suppress("SpellCheckingInspection") private val RELEASE_TEMURIN_17_0_1 =
    """|IMPLEMENTOR="Eclipse Adoptium"
       |IMPLEMENTOR_VERSION="Temurin-17.0.1+12"
       |JAVA_VERSION="17.0.1"
       |JAVA_VERSION_DATE="2021-10-19"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:git:571f1238bb46"
       |BUILD_SOURCE="git:732e6ff"
       |FULL_VERSION="17.0.1+12"
       |SEMANTIC_VERSION="17.0.1+12"
       |BUILD_INFO="OS: Mac OS X Version: 10.14.6 18G84"
       |JVM_VARIANT="Hotspot"
       |JVM_VERSION="17.0.1+12"
       |IMAGE_TYPE="JDK"
    """.trimMargin()

  @Rule @JvmField val tempDir = TempDirectory()

  @Test fun `Oracle OpenJDK 8`() = assertVariant(Unknown, RELEASE_ORACLE_OPEN_1_8_0_41, MANIFEST_ORACLE_OPEN_1_8_0_41)  // no vendor info
  @Test fun `Oracle Commercial 8`() = assertVariant(Oracle, RELEASE_ORACLE_1_8_0_291, MANIFEST_ORACLE_1_8_0_291)
  @Test fun `Oracle 16`() = assertVariant(Oracle, RELEASE_ORACLE_16_0_1)
  @Test fun `AdoptOpenJDK 8 (HotSpot)`() = assertVariant(AdoptOpenJdk_HS, RELEASE_ADOPT_HOTSPOT_1_8_0_282)
  @Test fun `AdoptOpenJDK 11 (OpenJ9)`() = assertVariant(AdoptOpenJdk_J9, RELEASE_ADOPT_J9_11_0_10)
  @Test fun `Corretto 8`() = assertVariant(Corretto, RELEASE_CORRETTO_1_8_0_292, MANIFEST_CORRETTO_1_8_0_292)
  @Test fun `Corretto 11`() = assertVariant(Corretto, RELEASE_CORRETTO_11_0_8_10_1)
  @Test fun `Liberica 11`() = assertVariant(Liberica, RELEASE_LIBERICA_11_0_8)
  @Test fun `SapMachine 11`() = assertVariant(SapMachine, RELEASE_SAP_MACHINE_11_0_8)
  @Test fun `Zulu 8`() = assertVariant(Unknown, RELEASE_ZULU_1_8_0_292, MANIFEST_ZULU_1_8_0_292)  // no vendor info
  @Test fun `Zulu 11`() = assertVariant(Zulu, RELEASE_ZULU_11_0_8)
  @Test fun `JetBrains Runtime 11`() = assertVariant(JBR, RELEASE_JBR_11_0_10)
  @Test fun `IBM JDK 8`() = assertVariant(IBM, RELEASE_IBM_1_8_0_291, MANIFEST_IBM_1_8_0_291)
  @Test fun `IBM JDK 11`() = assertVariant(IBM, RELEASE_IBM_11_0_11)
  @Test fun `GraalVM 8`() = assertVariant(GraalVM, RELEASE_GRAALVM_1_8_0_292)
  @Test fun `GraalVM 16`() = assertVariant(GraalVM, RELEASE_GRAALVM_16_0_1)
  @Test fun `Semeru 16`() = assertVariant(Semeru, RELEASE_SEMERU_16_0_2)
  @Test fun `Temurin 17`() = assertVariant(Temurin, RELEASE_TEMURIN_17_0_1)

  private fun assertVariant(expectedVariant: JdkVersionDetector.Variant, releaseText: String, manifestText: String = "") {
    tempDir.newFile("release", releaseText.toByteArray())

    if (manifestText.isNotEmpty()) {
      val manifest = Manifest()
      manifest.mainAttributes.putAll(manifestText.splitToSequence("\n").map { it.split(": ", limit = 2) }.map { Attributes.Name(it[0]) to it[1] })
      JarOutputStream(tempDir.newFile("jre/lib/rt.jar").outputStream(), manifest).close()
    }

    val versionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(tempDir.rootPath.toString())
    assertEquals(expectedVariant, versionInfo!!.variant)
  }
}
