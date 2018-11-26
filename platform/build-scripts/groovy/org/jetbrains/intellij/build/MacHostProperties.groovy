/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import groovy.transform.Immutable


/**
 * The purpose of using Mac host is preparation and signing OS X specific artifacts.<br>
 * The necessary software for Mac host:
 * <ul>
 * <li>OS X 10.9</li>
 * <li>FTP server (it is part of OS X installation).</li>
 * <li>Perl 5.16 (it is part of OS X installation).</li>
 * <li>DSStore perl module</li>
 * <li>Private key and digital certificate for signing apps.</li>
 *</ul>
 * <br>
 *How to setup Mac host:<br>
 *<ol>
 *  <li>Install OS X 10.9.</li>
 *  <li>Import private key and signing certificate <a href=https://developer.apple.com/library/mac/documentation/Security/Conceptual/CodeSigningGuide/Procedures/Procedures.html">(CodeSigningGuide)</a></li>
 *  <li>Enable FTP Server. Run the command in terminal:<br>
 *     sudo -s launchctl load -w /System/Library/LaunchDaemons/ftp.plist</li>
 *  <li>Create user which will be used for ftp connection</li>
 *  <li>Install DSStore perl module. Run the command in terminal:<br>
 *     sudo cpan Mac::Finder::DSStore<br>
 *     During the cpan and the module installation process will be need to allow install the command line pkg which is part of cpan installation and required for successful result.</li>
 *  <li>Set environment variable VERSIONER_PERL_PREFER_32_BIT to "true". <a href=http://apple.stackexchange.com/questions/83109/macosx-10-8-and-32-bit-perl-modules">More information about it.</a></li>
 * </ol>
 */
@CompileStatic
@Immutable
class MacHostProperties {
  /**
   * Mac host host name.
   */
  final String host

  /**
   * userName for access to Mac host via FTP
   */
  final String userName

  /**
   * password for access to Mac host via FTP
   */
  final String password

  /**
   * Full name of a keychain identity (Applications > Utilities > Keychain Access).
   * More info in SIGNING IDENTITIES (https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man1/codesign.1.html)
   */
  final String codesignString
}