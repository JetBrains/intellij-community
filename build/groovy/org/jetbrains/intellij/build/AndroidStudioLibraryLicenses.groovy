/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

import static org.jetbrains.intellij.build.LibraryLicense.jetbrainsLibrary

@CompileStatic
class AndroidStudioLibraryLicenses {
  public static final List<LibraryLicense> LICENSES_LIST = (([
    new LibraryLicense(name: "AAPT Protos", libraryName: "aapt-proto", license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Am Instrument Data proto", libraryName: "libam-instrumentation-data-proto",
                       license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Android Emulator gRPC API", libraryName: "emulator-proto", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0.txt"),
    // for game-tools-protos module library in android.game-tools.main
    new LibraryLicense(name: "Android Game Tools Protos", libraryName: "game-tools-protos", license: "Apache 2.0", url: "http://source.android.com/"),
    // for instantapps-api module library in intellij.android.core
    new LibraryLicense(name: "Android Instant Apps SDK API", libraryName: "instantapps-api", license: "Apache 2.0"),
    // for jetifier-core module library in db-compilerCommon
    new LibraryLicense(name: "Android Jetifier Core", libraryName: "jetifier-core", license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Android Studio Analytics Protos", libraryName: "studio-analytics-proto", license: "Apache 2.0", url: "http://source.android.com/"),
    // for androidx-test-core-proto module library in intellij.android.core
    new LibraryLicense(name: "AndroidX Test Library core protos", libraryName: "androidx-test-core-proto", license: "Android Software Development Kit License Agreement", licenseUrl: "https://developer.android.com/studio/terms"),
    new LibraryLicense(name: "ANTLR 4 Runtime", libraryName: "antlr4-runtime", version: "4.5.3", license: "BSD",
                       url: "http://www.antlr.org", licenseUrl: "http://www.antlr.org/license.html"),
    // for commons-lang module library in db-compiler
    new LibraryLicense(name: "Apache Commons Lang", libraryName: "commons-lang", version: "2.6", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0.txt", url: "http://commons.apache.org/proper/commons-lang/"),
    // for bouncycastle module library in android.sdktools.sdk-common
    new LibraryLicense(name: "Bouncy Castle", libraryName: "bouncycastle", license: "MIT License", url: "http://bouncycastle.org",
                       licenseUrl: "http://bouncycastle.org/licence.html"),
    new LibraryLicense(name: "CDT", libraryName: "org.eclipse.cdt", license: "Eclipse Public License 1.0"),
    // for compose-compiler-hosted module library in intellij.android.compose-ide-plugin
    new LibraryLicense(name: "Compose Compiler Hosted", libraryName: "compose-compiler-hosted",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    // For ADB wireless QR Code generation
    new LibraryLicense(name: "Core barcode encoding/decoding library", libraryName: "zxing-core",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    // for eclipse-layout-kernel module library in intellij.android.designer
    new LibraryLicense(name: "Eclipse Layout Kernel", libraryName: "eclipse-layout-kernel", license: "Eclipse Public License 1.0"),
    // for LSP4J module libraries in intellij.c
    new LibraryLicense(name: "Eclipse LSP4J", libraryName: "org.eclipse.lsp4j:org.eclipse.lsp4j:0.7.1", license: "Eclipse Public License 1.0"),
    new LibraryLicense(name: "Eclipse LSP4J", libraryName: "org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.7.1", license: "Eclipse Public License 1.0"),
    new LibraryLicense(name: "fetchasgoogle.jar", libraryName: "fetchasgoogle.jar", license: "Apache 2.0"),
    // for flatbuffers-java module library in android.sdktools.mlkit-common
    new LibraryLicense(name: "FlatBuffers Java API", libraryName: "flatbuffers-java",
                       version: "1.11.1", url: "https://google.github.io/flatbuffers/",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Google APIs Client Library for Java", version: "min-repackaged-1.20.0",
                       libraryName: "google-api-java-client", url: "https://developers.google.com/api-client-library/java/",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Google Analytics API", version: "v3-rev115-1.20.0",
                       libraryName: "google-api-services-analytics-v3-rev115-1.20.0.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-api-services-appengine", version: "v1-rev9-1.22.0",
                       libraryName: "google-api-services-appengine-v1-rev9-1.22.0.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    // for auto-common module library in db-compiler
    new LibraryLicense(name: "Google Auto Common Utilities", libraryName: "auto-common", version: "0.10", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0.txt", url: "https://github.com/google/auto/tree/master/common"),
    new LibraryLicense(name: "Google Cloud Resource Manager API", version: "v1beta1-rev12-1.21.0",
                       libraryName: "google-api-services-cloudresourcemanager-v1beta1-rev12-1.21.0.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-api-services-debugger", version: "",
                       libraryName: "google-api-services-debugger.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-api-services-mobilesdk", version: "v1",
                       libraryName: "google-api-services-mobilesdk-v1.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Google OAuth2 API", version: "v2-rev70-1.18.0-rc",
                       libraryName: "google-api-services-oauth2-v2-rev70-1.18.0-rc.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-api-services-source", version: "",
                       libraryName: "google-api-services-source.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Google Cloud Storage JSON API", version: "v1-rev1-1.18.0-rc",
                       libraryName: "google-api-services-storage-v1-rev1-1.18.0-rc.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-api-services-testing", version: "v1-revsnapshot-1.20.0",
                       libraryName: "google-api-services-testing-v1-revsnapshot-1.20.0.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-api-services-toolresults", version: "v1beta3-rev20151013-1.20.0",
                       libraryName: "google-api-services-toolresults-v1beta3-rev20151013-1.20.0.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google-http-client-jackson", version: "1.18.0-rc",
                       libraryName: "google-http-client-jackson-1.18.0-rc.jar", url: "",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "google.gdt.eclipse.login.common.jar", libraryName: "google.gdt.eclipse.login.common.jar",
                       license: "Eclipse Public License 1.0", licenseUrl: "http://www.eclipse.org/org/documents/epl-v10.html"),
    new LibraryLicense(name: "google-gct-login-context-pg.jar", libraryName: "google-gct-login-context-pg.jar",
                       license: "Eclipse Public License 1.0", licenseUrl: "http://www.eclipse.org/org/documents/epl-v10.html"),
    new LibraryLicense(name: "Gradle App Engine Tooling Model", version: "0.1.0",
                       libraryName: "gradle-appengine-builder-model-0.1.0.jar",
                       url: "https://github.com/GoogleCloudPlatform/gradle-appengine-plugin",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "HdrHistogram", version: "2.1.4", libraryName: "HdrHistogram",
                       license: "BSD 2-Clause", licenseUrl: "https://opensource.org/licenses/BSD-2-Clause"),
    new LibraryLicense(name: "Jackson", version: "1.9.11", libraryName: "jackson-core-asl-1.9.11.jar", url: "http://jackson.codehaus.org",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    // for javapoet module library in db-compiler
    new LibraryLicense(name: "Java Poet", libraryName: "javapoet", version: "1.8.0", license: "Apache 2.0",
                       url: "https://github.com/square/javapoet"),
    new LibraryLicense(name: "Java Servlet API", libraryName: "javax.servlet-api-3.0.1.jar", license: "CDDL + GPLv2 w/ Classpath Exception",
                       licenseUrl: "https://glassfish.java.net/nonav/public/CDDL+GPL.html"),
    // for juniversalchardet module library in db-compiler
    new LibraryLicense(name: "Juniversalchardet", libraryName: "juniversalchardet", version: "1.0.3",
                       url: "https://code.google.com/archive/p/juniversalchardet",
                       license: "MPL 1.1",  licenseUrl: "http://www.mozilla.org/MPL/MPL-1.1.html"),
    new LibraryLicense(name: "kotlin-gradle-plugin-model", libraryName: "kotlin-gradle-plugin-model", version: "1.3.0",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Layout Inspector Protos", libraryName: "layoutinspector-proto", license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Layoutlib", libraryName: "layoutlib.jar", version: "1.0", license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Layoutlib Native", libraryName: "layoutlib_native.jar", version: "1.0", license: "Apache 2.0", url: "http://source.android.com/"),
    // for moshi module library in intellij.android.core
    new LibraryLicense(name: "Moshi", libraryName: "moshi", version: "1.6.0", license: "Apache 2.0",
                       url: "https://github.com/square/moshi"),
    // for okio module library in intellij.android.core
    new LibraryLicense(name: "Okio", libraryName: "okio", version: "1.14.0", license: "Apache 2.0",
                       url: "https://github.com/square/okio"),
    // for pepk module library in intellij.android.core
    new LibraryLicense(name: "PEPK", libraryName: "pepk", license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Perfetto protos", libraryName: "perfetto-proto", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "samplesindex-v1-1.0-SNAPSHOT.jar", libraryName: "samplesindex-v1-1.0-SNAPSHOT.jar",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "SQLite Inspector Proto", libraryName: "sqlite-inspector-proto", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Studio gRPC", libraryName: "studio-grpc", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Studio Protobuf", libraryName: "studio-proto", license: "protobuf",
                       licenseUrl: "https://github.com/protocolbuffers/protobuf/blob/master/LICENSE"),
    new LibraryLicense(name: "swt.jar", libraryName: "swt.jar",
                       license: "Eclipse Public License 1.0", licenseUrl: "http://www.eclipse.org/legal/epl-v10.html"),
    new LibraryLicense(name: "uiautomatorviewer.jar", libraryName: "uiautomatorviewer.jar", license: "Apache 2.0"),
    new LibraryLicense(name: "TightVNC", libraryName: "tightvnc", license: "Commercial License"),
    // for tensorflow-lite-metadata module library in android.sdktools.mlkit-common
    new LibraryLicense(name: "TensorFlow Lite Metadata Library", libraryName: "tensorflow-lite-metadata",
                       version: "0.1.0-rc1", url: "https://tensorflow.org/lite",
                       license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    // for traceprocessor-proto module library in intellij.android.profilersAndroid
    new LibraryLicense(name: "TraceProcessor Daemon Protos", libraryName: "traceprocessor-proto", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Transport Pipeline", libraryName: "transport-proto", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    // for workmanager-inspector-proto module library in intellij.android.app-inspection.inspectors.workmanager.model
    new LibraryLicense(name: "WorkManager Inspector Proto", libraryName: "workmanager-inspector-proto", license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Xtext", libraryName: "org.eclipse.xtext.xbase", license: "Eclipse Public License 1.0"),
    new LibraryLicense(name: "Instant App Proto Manifest", libraryName: "aia-proto",
                       license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Archive Patcher",
                       libraryName: "archive-patcher",
                       url: "https://github.com/andrewhayden/archive-patcher",
                       additionalLibraryNames: ["explainer.jar", "generator.jar", "shared.jar"],
                       license: "Apache 2.0",
                       licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "Instant run protos", libraryName: "deploy_java_proto.jar",
                       license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "Instant run version", libraryName: "libjava_version.jar",
                       license: "Apache 2.0", url: "http://source.android.com/"),
    new LibraryLicense(name: "R8", libraryName: "r8.jar", license: "BSD"),
  ] as List<LibraryLicense>) + (("true" == System.getProperty("bundle.ui.tests") ? [
    new LibraryLicense(name: "truth", libraryName: "truth", version: "0.28", license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "easymock-tools", libraryName: "easymock-tools", version: "3.1", license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "fest-reflect", libraryName: "fest-reflect-2.0-SNAPSHOT.jar", version: "2.0-SNAPSHOT", license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "fest", libraryName: "fest", version: "0", license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0"),
    new LibraryLicense(name: "jcip-annotations", libraryName: "jcip-annotations-1.0-1.jar", version: "1.0-1", license: "Apache 2.0", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0")
    ] : []) as List<LibraryLicense>)) as List<LibraryLicense>
}
