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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.header.HeaderParser;
import org.jetbrains.lang.manifest.header.HeaderParserProvider;

import java.util.Map;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class StandardManifestHeaderParsers implements HeaderParserProvider {
  private final Map<String, HeaderParser> myParsers;

  public StandardManifestHeaderParsers() {
    myParsers = ContainerUtil.newHashMap();
    myParsers.put("Manifest-Version", StandardHeaderParser.INSTANCE);
    myParsers.put("Created-By", StandardHeaderParser.INSTANCE);
    myParsers.put("Signature-Version", StandardHeaderParser.INSTANCE);
    myParsers.put("Class-Path", StandardHeaderParser.INSTANCE);
    myParsers.put(ClassReferenceParser.MAIN_CLASS, ClassReferenceParser.INSTANCE);
    myParsers.put("Implementation-Title", StandardHeaderParser.INSTANCE);
    myParsers.put("Implementation-Version", StandardHeaderParser.INSTANCE);
    myParsers.put("Implementation-Vendor", StandardHeaderParser.INSTANCE);
    myParsers.put("Implementation-Vendor-Id", StandardHeaderParser.INSTANCE);
    myParsers.put("Implementation-URL", StandardHeaderParser.INSTANCE);
    myParsers.put("Specification-Title", StandardHeaderParser.INSTANCE);
    myParsers.put("Specification-Version", StandardHeaderParser.INSTANCE);
    myParsers.put("Specification-Vendor", StandardHeaderParser.INSTANCE);
    myParsers.put("Sealed", StandardHeaderParser.INSTANCE);
    myParsers.put("Name", StandardHeaderParser.INSTANCE);
    myParsers.put("Content-Type", StandardHeaderParser.INSTANCE);
    myParsers.put("Java-Bean", StandardHeaderParser.INSTANCE);
    myParsers.put("MD5-Digest", StandardHeaderParser.INSTANCE);
    myParsers.put("SHA-Digest", StandardHeaderParser.INSTANCE);
    myParsers.put("Magic", StandardHeaderParser.INSTANCE);
    myParsers.put(ClassReferenceParser.PREMAIN_CLASS, ClassReferenceParser.INSTANCE);
    myParsers.put(ClassReferenceParser.AGENT_CLASS, ClassReferenceParser.INSTANCE);
    myParsers.put("Boot-Class-Path", StandardHeaderParser.INSTANCE);
    myParsers.put("Can-Redefine-Classes", StandardHeaderParser.INSTANCE);
    myParsers.put("Can-Retransform-Classes", StandardHeaderParser.INSTANCE);
    myParsers.put("Can-Set-Native-Method-Prefix", StandardHeaderParser.INSTANCE);
  }

  @NotNull
  @Override
  public Map<String, HeaderParser> getHeaderParsers() {
    return myParsers;
  }
}