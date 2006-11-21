/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.javaee.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LibraryLinkImpl extends LibraryLink {

  public com.intellij.openapi.module.impl.LibraryLinkImpl getDelegate() {
    return (com.intellij.openapi.module.impl.LibraryLinkImpl)super.getDelegate();
  }

  public LibraryLinkImpl(final com.intellij.openapi.module.impl.LibraryLinkImpl delegate) {
    super(delegate);
  }

  public LibraryLinkImpl(Library library, Module parentModule) {
    this(new com.intellij.openapi.module.impl.LibraryLinkImpl(library, parentModule));
  }

  public @Nullable Library getLibrary() {
    return getDelegate().getLibrary();
  }

  public @Nullable Library getLibrary(@Nullable ModulesProvider provider) {
    return getDelegate().getLibrary(provider);
  }

  public void addUrl(String url) {
    getDelegate().addUrl(url);
  }

  public List<String> getUrls() {
    return getDelegate().getUrls();
  }

  public String getSingleFileName() {
    return getDelegate().getSingleFileName();
  }

  public boolean hasDirectoriesOnly() {
    return getDelegate().hasDirectoriesOnly();
  }

  public String getName() {
    return getDelegate().getName();
  }

  public String getLevel() {
    return getDelegate().getLevel();
  }

  public boolean resolveElement(ModulesProvider provider) {
    return getDelegate().resolveElement(provider);
  }

  public LibraryLink clone() {
    return new LibraryLinkImpl((com.intellij.openapi.module.impl.LibraryLinkImpl)getDelegate().clone());
  }

}
