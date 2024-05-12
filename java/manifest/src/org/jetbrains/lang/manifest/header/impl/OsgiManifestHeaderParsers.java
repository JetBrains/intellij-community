/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest.header.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.header.HeaderParser;
import org.jetbrains.lang.manifest.header.HeaderParserProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public final class OsgiManifestHeaderParsers implements HeaderParserProvider {
  private final Map<String, HeaderParser> myParsers;

  public OsgiManifestHeaderParsers() {
    myParsers = new HashMap<>();
    myParsers.put("Application-Content", StandardHeaderParser.INSTANCE);
    myParsers.put("Application-ExportService", StandardHeaderParser.INSTANCE);
    myParsers.put("Application-ManifestVersion", StandardHeaderParser.INSTANCE);
    myParsers.put("Application-ImportService", StandardHeaderParser.INSTANCE);
    myParsers.put("Application-Name", StandardHeaderParser.INSTANCE);
    myParsers.put("Application-SymbolicName", StandardHeaderParser.INSTANCE);
    myParsers.put("Application-Version", StandardHeaderParser.INSTANCE);
    myParsers.put("Bnd-AddXmlToTest", StandardHeaderParser.INSTANCE);
    myParsers.put("Bnd-LastModified", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-ActivationPolicy", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Activator", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Blueprint", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Category", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Classpath", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-ContactAddress", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Contributors", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Copyright", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Description", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Developers", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-DocURL", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Icon", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-License", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Localization", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-ManifestVersion", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Name", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-NativeCode", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-RequiredExecutionEnvironment", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-SymbolicName", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-UpdateLocation", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Vendor", StandardHeaderParser.INSTANCE);
    myParsers.put("Bundle-Version", StandardHeaderParser.INSTANCE);
    myParsers.put("Conditional-Package", StandardHeaderParser.INSTANCE);
    myParsers.put("DynamicImport-Package", StandardHeaderParser.INSTANCE);
    myParsers.put("Eclipse-BuddyPolicy", StandardHeaderParser.INSTANCE);
    myParsers.put("Eclipse-BundleShape", StandardHeaderParser.INSTANCE);
    myParsers.put("Eclipse-ExtensibleAPI", StandardHeaderParser.INSTANCE);
    myParsers.put("Eclipse-PlatformFilter", StandardHeaderParser.INSTANCE);
    myParsers.put("Eclipse-RegisterBuddy", StandardHeaderParser.INSTANCE);
    myParsers.put("Export-Package", StandardHeaderParser.INSTANCE);
    myParsers.put("Export-Service", StandardHeaderParser.INSTANCE);
    myParsers.put("Fragment-Host", StandardHeaderParser.INSTANCE);
    myParsers.put("Import-Bundle", StandardHeaderParser.INSTANCE);
    myParsers.put("Import-Library", StandardHeaderParser.INSTANCE);
    myParsers.put("Import-Package", StandardHeaderParser.INSTANCE);
    myParsers.put("Import-Service", StandardHeaderParser.INSTANCE);
    myParsers.put("Include-Resource", StandardHeaderParser.INSTANCE);
    myParsers.put("Meta-Persistence", StandardHeaderParser.INSTANCE);
    myParsers.put("Module-Scope", StandardHeaderParser.INSTANCE);
    myParsers.put("Module-Type", StandardHeaderParser.INSTANCE);
    myParsers.put("Private-Package", StandardHeaderParser.INSTANCE);
    myParsers.put("Provide-Capability", StandardHeaderParser.INSTANCE);
    myParsers.put("Require-Bundle", StandardHeaderParser.INSTANCE);
    myParsers.put("Require-Capability", StandardHeaderParser.INSTANCE);
    myParsers.put("Service-Component", StandardHeaderParser.INSTANCE);
    myParsers.put("Test-Cases", StandardHeaderParser.INSTANCE);
    myParsers.put("Tool", StandardHeaderParser.INSTANCE);
    myParsers.put("Use-Bundle", StandardHeaderParser.INSTANCE);
    myParsers.put("Web-ContextPath", StandardHeaderParser.INSTANCE);
    myParsers.put("Web-DispatcherServletUrlPatterns", StandardHeaderParser.INSTANCE);
    myParsers.put("Web-FilterMappings", StandardHeaderParser.INSTANCE);
  }

  @NotNull
  @Override
  public Map<String, HeaderParser> getHeaderParsers() {
    return myParsers;
  }
}
