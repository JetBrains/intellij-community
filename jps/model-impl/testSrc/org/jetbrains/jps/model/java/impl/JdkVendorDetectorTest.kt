// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl


import com.intellij.testFramework.assertions.Assertions.assertThat
import org.jetbrains.jps.model.java.impl.JdkVendorDetector.*
import org.junit.Test
import java.io.StringReader
import java.util.*


@Suppress("SpellCheckingInspection")
class JdkVendorDetectorTest {

  private /*const*/ val RELEASE_ORACLE_1_7_0_40 =
    """|JAVA_VERSION="1.7.0"
       |OS_NAME="Darwin"
       |OS_VERSION="11.2"
       |OS_ARCH="x86_64"
       |SOURCE=" .:1d53bd8fd2a6 corba:e29ea0b297e5 deploy:b85f185c8ae5 hotspot:eceae0478243 hotspot/make/closed:6bd65eae6152 hotspot/src/closed:75561b86f615 hotspot/test/closed:fc4f796b9fb4 install:df46e3cf2551 jaxp:c0bd71414ea5 jaxws:3ee85b3793de jdk:fb25cdef17e9 jdk/make/closed:d514b7bf8658 jdk/src/closed:adcdaaa030f5 jdk/test/closed:a8d6b800ea6b langtools:988ece7b6865 pubs:0c966c422c8a sponsors:9b375572541c"
    """.trimMargin()

  private /*const*/ val RELEASE_ORACLE_1_8_0_102 =
    """|JAVA_VERSION="1.8.0_102"
       |OS_NAME="Darwin"
       |OS_VERSION="11.2"
       |OS_ARCH="x86_64"
       |SOURCE=" .:daafd7d3a76a corba:56b133772ec1 deploy:de4043049895 hotspot:ac29c9c1193a hotspot/make/closed:c65fdf789fd7 hotspot/src/closed:60c728d85221 hotspot/test/closed:1e18c2279cc4 install:6f5ea1d3b6a0 jaxp:1f032000ff4b jaxws:81f2d81a48d7 jdk:48c99b423839 jdk/make/closed:e4e4e633f6e0 jdk/src/closed:e49a105b44cf jdk/test/closed:410f3c278e89 langtools:0549bf2f507d nashorn:0948e61a3722 pubs:6b7e6c06ce51 sponsors:b2b1e386c6b3"
       |BUILD_TYPE="commercial"
    """.trimMargin()

  private /*const*/ val RELEASE_CORRETTO_11_0_8_10_1 =
    """|IMPLEMENTOR="Amazon.com Inc."
       |IMPLEMENTOR_VERSION="Corretto-11.0.8.10.1"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-14"
       |LIBC="default"
       |MODULES="java.base java.compiler java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management java.security.sasl java.naming java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset java.xml.crypto java.se java.smartcardio jdk.accessibility jdk.internal.vm.ci jdk.management jdk.unsupported jdk.internal.vm.compiler jdk.aot jdk.internal.jvmstat jdk.attach jdk.charsets jdk.compiler jdk.crypto.ec jdk.crypto.cryptoki jdk.dynalink jdk.internal.ed jdk.editpad jdk.hotspot.agent jdk.httpserver jdk.internal.le jdk.internal.opt jdk.internal.vm.compiler.management jdk.jartool jdk.javadoc jdk.jcmd jdk.management.agent jdk.jconsole jdk.jdeps jdk.jdwp.agent jdk.jdi jdk.jfr jdk.jlink jdk.jshell jdk.jsobject jdk.jstatd jdk.localedata jdk.management.jfr jdk.naming.dns jdk.naming.rmi jdk.net jdk.pack jdk.rmic jdk.scripting.nashorn jdk.scripting.nashorn.shell jdk.sctp jdk.security.auth jdk.security.jgss jdk.unsupported.desktop jdk.xml.dom jdk.zipfs"
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=""
    """.trimMargin()

  private /*const*/ val RELEASE_LIBRICA_11_0_8 =
    """|IMPLEMENTOR="BellSoft"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-14"
       |MODULES="java.base java.compiler java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management java.security.sasl java.naming java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset jav
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:hg:030bc020dc04+"
    """.trimMargin()

  private /*const*/ val RELEASE_SAP_11_0_8 =
    """|IMPLEMENTOR="SAP SE"
       |IMPLEMENTOR_VERSION="SapMachine"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-15"
       |MODULES="java.base java.compiler java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management java.security.sasl java.naming java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset jav
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:git:21ef36a0f46a+"
    """.trimMargin()

  private /*const*/ val RELEASE_ZULU_11_0_8 =
    """|IMPLEMENTOR="Azul Systems, Inc."
       |IMPLEMENTOR_VERSION="Zulu11.41+23-CA"
       |JAVA_VERSION="11.0.8"
       |JAVA_VERSION_DATE="2020-07-14"
       |LIBC="default"
       |MODULES="java.base java.compiler java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management java.security.sasl java.naming java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset jav
       |OS_ARCH="x86_64"
       |OS_NAME="Darwin"
       |SOURCE=".:hg:5b0e54350bbc"
    """.trimMargin()

  private /*const*/ val RELEASE_JBR_11_0_10 =
    """|MODULES="java.base java.datatransfer java.xml java.prefs java.desktop gluegen.rt java.compiler java.instrument java.logging java.management java.security.sasl java.naming java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset java.xml.crypto java.se java.smartcardio jogl.all jcef jdk.accessibility jdk.internal.vm.ci jdk.management jdk.unsupported jdk.internal.vm.compiler jdk.aot jdk.internal.jvmstat jdk.attach jdk.charsets jdk.compiler jdk.crypto.ec jdk.crypto.cryptoki jdk.dynalink jdk.hotspot.agent jdk.httpserver jdk.internal.ed jdk.internal.le jdk.internal.vm.compiler.management jdk.jdwp.agent jdk.jdi jdk.jfr jdk.jsobject jdk.localedata jdk.management.agent jdk.management.jfr jdk.naming.dns jdk.naming.rmi jdk.net jdk.pack jdk.scripting.nashorn jdk.scripting.nashorn.shell jdk.sctp jdk.security.auth jdk.security.jgss jdk.xml.dom jdk.zipfs"
       |IMPLEMENTOR="JetBrains s.r.o."
       |SOURCE=".\:git\:58e267d9f35a+ jcef_git\:git\:e09d9c4a783e"
       |OS_ARCH="x86_64"
       |IMPLEMENTOR_VERSION="JBR-11.0.10.9-1304.4-dcevm"
       |OS_NAME="Windows"
       |JAVA_VERSION="11.0.10"
    """.trimMargin()


  // %formatter:off

  @Test fun checkOracle07()   = checkVendor(RELEASE_ORACLE_1_7_0_40,      ORACLE)
  @Test fun checkOracle08()   = checkVendor(RELEASE_ORACLE_1_8_0_102,     ORACLE)
  @Test fun checkCorretto11() = checkVendor(RELEASE_CORRETTO_11_0_8_10_1, CORRETTO)
  @Test fun checkLiberica11() = checkVendor(RELEASE_LIBRICA_11_0_8,       LIBERICA)
  @Test fun checkSap11()      = checkVendor(RELEASE_SAP_11_0_8,           SAP)
  @Test fun checkZulu11()     = checkVendor(RELEASE_ZULU_11_0_8,          AZUL)
  @Test fun checkJbr11()      = checkVendor(RELEASE_JBR_11_0_10,          JBR)

  // %formatter:on


  private fun checkVendor(releaseText: String, expectedVendor: Vendor) {
    val releaseProperties = Properties()
    StringReader(releaseText).use { reader ->
      releaseProperties.load(reader)
    }
    assertThat(releaseProperties.isNotEmpty())

    val detectedVendor = detectJdkVendorByReleaseFile(releaseProperties)

    assertThat(detectedVendor)
      .withFailMessage("Java Vendor for $expectedVendor should be detected").isNotNull
      .withFailMessage("Java Vendor expected $expectedVendor but got $detectedVendor").isEqualTo(expectedVendor)
  }


}