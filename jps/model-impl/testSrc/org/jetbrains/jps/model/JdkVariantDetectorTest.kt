// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model

import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.jps.model.java.JdkVersionDetector.Variant.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

@Suppress("SpellCheckingInspection")
class JdkVariantDetectorTest {
  private val RELEASE_ORACLE_OPEN_1_8_0_41 =
    """|JAVA_VERSION="1.8.0_41"
       |OS_NAME="Windows"
       |OS_VERSION="5.1"
       |OS_ARCH="i586"
       |SOURCE=""
    """.trimMargin()

  private val MANIFEST_ORACLE_OPEN_1_8_0_41 =
    """|Manifest-Version: 1.0
       |Implementation-Vendor: N/A
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_41
       |Specification-Vendor: Oracle Corporation
       |Created-By: 1.7.0_07 (Oracle Corporation)
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
    """.trimMargin()

  private val RELEASE_ORACLE_1_8_0_291 =
    """|JAVA_VERSION="1.8.0_291"
       |OS_NAME="Linux"
       |OS_VERSION="2.6"
       |OS_ARCH="amd64"
       |SOURCE=" .:06b604e7edd4 hotspot:7c09263ba3e2 hotspot/src/closed:573e10ede63c ..."
       |BUILD_TYPE="commercial"
    """.trimMargin()

  private val MANIFEST_ORACLE_1_8_0_291 =
    """|Manifest-Version: 1.0
       |Implementation-Vendor: Oracle Corporation
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_291
       |Specification-Vendor: Oracle Corporation
       |Created-By: 1.7.0_07 (Oracle Corporation)
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
    """.trimMargin()

  private val RELEASE_ORACLE_16_0_1 =
    """|IMPLEMENTOR="Oracle Corporation"
       |JAVA_VERSION="16.0.1"
       |JAVA_VERSION_DATE="2021-04-20"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="x86_64"
       |OS_NAME="Windows"
       |SOURCE=".:git:ba7c640201ba"
    """.trimMargin()

  private val RELEASE_ADOPT_HOTSPOT_1_8_0_282 =
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

  private val RELEASE_ADOPT_J9_11_0_10 =
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

  private val RELEASE_CORRETTO_1_8_0_292 =
    """|JAVA_VERSION="1.8.0_292"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=""
       |LIBC=""
    """.trimMargin()

  private val MANIFEST_CORRETTO_1_8_0_292 =
    """|Manifest-Version: 1.0
       |Implementation-Title: Java Runtime Environment
       |Implementation-Version: 1.8.0_292
       |Specification-Vendor: Oracle Corporation
       |Specification-Title: Java Platform API Specification
       |Specification-Version: 1.8
       |Created-By: 1.8.0_222 (Amazon.com Inc.)
       |Implementation-Vendor: Amazon.com Inc.
    """.trimMargin()

  private val RELEASE_CORRETTO_11_0_8_10_1 =
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

  private val RELEASE_ZULU_11_0_8 =
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

  private val RELEASE_ZULU_1_8_0_292 =
    """|JAVA_VERSION="1.8.0_292"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=" .:ddbdd8cb2baa hotspot:19eb9031626c ..."
    """.trimMargin()

  private val MANIFEST_ZULU_1_8_0_292 =
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

  private val RELEASE_IBM_1_8_0_291 =
    """|JAVA_VERSION="1.8.0_291"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=""
    """.trimMargin()

  private val MANIFEST_IBM_1_8_0_291 =
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

  private val RELEASE_IBM_11_0_11 =
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

  private val RELEASE_GRAALVM_1_8_0_292 =
    """|JAVA_VERSION="1.8.0_292"
       |OS_NAME="Windows"
       |OS_VERSION="5.2"
       |OS_ARCH="amd64"
       |SOURCE=" substratevm:dc4d2d6bdda1e7262bbae3291475e02fd498f382 truffle:dc4d2d6bdda1e7262bbae3291475e02fd498f382 ..."
       |GRAALVM_VERSION="21.1.0"
       |COMMIT_INFO={}
       |component_catalog="..."
    """.trimMargin()

  private val RELEASE_GRAALVM_21_0_2 =
    """|IMPLEMENTOR="Oracle Corporation"
       |JAVA_RUNTIME_VERSION="21.0.2+13-LTS-jvmci-23.1-b30"
       |JAVA_VERSION="21.0.2"
       |JAVA_VERSION_DATE="2024-01-16"
       |OS_ARCH="aarch64"
       |OS_NAME="Darwin"
       |GRAALVM_VERSION="23.1.2"
      """.trimMargin()

  private val RELEASE_GRAALVM_CE_16_0_1 =
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

  private val RELEASE_SEMERU_16_0_2 =
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

  private val RELEASE_TEMURIN_17_0_1 =
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

  private val RELEASE_HOMEBREW_OPENJDK_17_0_11 =
    """|IMPLEMENTOR="Homebrew"
       |IMPLEMENTOR_VERSION="Homebrew"
       |JAVA_RUNTIME_VERSION="17.0.11+0"
       |JAVA_VERSION="17.0.11"
       |JAVA_VERSION_DATE="2024-04-16"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="aarch64"
       |OS_NAME="Darwin"
       |SOURCE=""
    """.trimMargin()

  private val RELEASE_BISHENG_17_0_11 =
    """|IMPLEMENTOR="BiSheng"
       |IMPLEMENTOR_VERSION="BiSheng"
       |JAVA_RUNTIME_VERSION="17.0.11+11"
       |JAVA_VERSION="17.0.11"
       |JAVA_VERSION_DATE="2024-04-16"
       |LIBC="gnu"
       |MODULES="java.base ..."
       |OS_ARCH="aarch64"
       |OS_NAME="Linux"
    """.trimMargin()

  private val RELEASE_KONA_17_0_12 =
    """|IMPLEMENTOR="Tencent"
       |IMPLEMENTOR_VERSION="TencentKonaJDK"
       |JAVA_RUNTIME_VERSION="17.0.12+1-LTS"
       |JAVA_VERSION="17.0.12"
       |JAVA_VERSION_DATE="2024-07-23"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="aarch64"
       |OS_NAME="Darwin"
    """.trimMargin()

  private val RELEASE_ALIBABA_1_8_0_412 =
    """|JAVA_VERSION="1.8.0_412"
       |OS_NAME="Linux"
       |OS_VERSION="2.6"
       |OS_ARCH="amd64"
       |IMPLEMENTOR="Alibaba"
       |FULL_VERSION="1.8.0_412-b01"
       |SEMANTIC_VERSION="8.0.412+1"
       |BUILD_INFO="OS: Linux Version: 4.15.0-187-generic"
       |JVM_VARIANT="Dragonwell"
       |JVM_VERSION="25.412-b01"
       |IMAGE_TYPE="JDK"
    """.trimMargin()

  private val RELEASE_MICROSOFT_21_0_4 =
    """|IMPLEMENTOR="Microsoft"
       |IMPLEMENTOR_VERSION="Microsoft-9911842"
       |JAVA_RUNTIME_VERSION="21.0.4+7-LTS"
       |JAVA_VERSION="21.0.4"
       |JAVA_VERSION_DATE="2024-07-16"
       |LIBC="default"
       |MODULES="java.base ..."
       |OS_ARCH="aarch64"
       |OS_NAME="Darwin"
    """.trimMargin()

  private val RELEASE_UNKNOWN_21 =
    """|IMPLEMENTOR="Foo"
       |IMPLEMENTOR_VERSION="Foo-1234"
       |JAVA_RUNTIME_VERSION="21"
       |JAVA_VERSION="21"
       |LIBC="default"
       |MODULES="java.base ..."
    """.trimMargin()

  @Rule @JvmField val tempDir = TempDirectory()

  @Test fun `Oracle OpenJDK 8`() = assertVariant(Unknown, RELEASE_ORACLE_OPEN_1_8_0_41, MANIFEST_ORACLE_OPEN_1_8_0_41)  // no vendor info
  @Test fun `Oracle Commercial 8`() = assertVariant(Oracle, RELEASE_ORACLE_1_8_0_291, MANIFEST_ORACLE_1_8_0_291)
  @Test fun `Oracle 16`() = assertVariant(Oracle, RELEASE_ORACLE_16_0_1)
  @Test fun `AdoptOpenJDK 8 (HotSpot)`() = assertVariant(AdoptOpenJdk_HS, RELEASE_ADOPT_HOTSPOT_1_8_0_282)
  @Test fun `AdoptOpenJDK 11 (OpenJ9)`() = assertVariant(AdoptOpenJdk_J9, RELEASE_ADOPT_J9_11_0_10)
  @Test fun `BiSheng 17`() = assertVariant(BiSheng, RELEASE_BISHENG_17_0_11)
  @Test fun `Corretto 8`() = assertVariant(Corretto, RELEASE_CORRETTO_1_8_0_292, MANIFEST_CORRETTO_1_8_0_292)
  @Test fun `Corretto 11`() = assertVariant(Corretto, RELEASE_CORRETTO_11_0_8_10_1)
  @Test fun `Dragonwell 8`() = assertVariant(Dragonwell, RELEASE_ALIBABA_1_8_0_412)
  @Test fun `Homebrew 17`() = assertVariant(Homebrew, RELEASE_HOMEBREW_OPENJDK_17_0_11)
  @Test fun `Kona 17`() = assertVariant(Kona, RELEASE_KONA_17_0_12)
  @Test fun `Liberica 11`() = assertVariant(Liberica, RELEASE_LIBERICA_11_0_8)
  @Test fun `Microsoft 21`() = assertVariant(Microsoft, RELEASE_MICROSOFT_21_0_4)
  @Test fun `SapMachine 11`() = assertVariant(SapMachine, RELEASE_SAP_MACHINE_11_0_8)
  @Test fun `Zulu 8`() = assertVariant(Unknown, RELEASE_ZULU_1_8_0_292, MANIFEST_ZULU_1_8_0_292)  // no vendor info
  @Test fun `Zulu 11`() = assertVariant(Zulu, RELEASE_ZULU_11_0_8)
  @Test fun `JetBrains Runtime 11`() = assertVariant(JBR, RELEASE_JBR_11_0_10)
  @Test fun `IBM JDK 8`() = assertVariant(IBM, RELEASE_IBM_1_8_0_291, MANIFEST_IBM_1_8_0_291)
  @Test fun `IBM JDK 11`() = assertVariant(IBM, RELEASE_IBM_11_0_11)
  @Test fun `GraalVM 8`() = assertVariant(GraalVM, RELEASE_GRAALVM_1_8_0_292)
  @Test fun `GraalVM 21`() = assertVariant(GraalVM, RELEASE_GRAALVM_21_0_2)
  @Test fun `GraalVM CE 16`() = assertVariant(GraalVMCE, RELEASE_GRAALVM_CE_16_0_1)
  @Test fun `Semeru 16`() = assertVariant(Semeru, RELEASE_SEMERU_16_0_2)
  @Test fun `Temurin 17`() = assertVariant(Temurin, RELEASE_TEMURIN_17_0_1)
  @Test fun `Unknown variant`() = assertVariant(Unknown, RELEASE_UNKNOWN_21)

  @Test fun `GraalVM 21 - version string`() = assertEquals(
    "GraalVM CE 17.0.7 - VM 23.0.0",
    JdkVersionDetector.JdkVersionInfo(JavaVersion.parse("17.0.7"), GraalVMCE, CpuArch.X86_64, "23.0.0").displayVersionString())

  @Test fun `Unknwown - version string`() = assertEquals(
    "Java 21",
    JdkVersionDetector.JdkVersionInfo(JavaVersion.parse("21"), Unknown, CpuArch.X86_64, null).displayVersionString()
  )

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
