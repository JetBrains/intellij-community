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
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.Nullable;

public class ModuleLinkImpl extends ModuleLink {

  public com.intellij.openapi.module.impl.ModuleLinkImpl getDelegate() {
    return (com.intellij.openapi.module.impl.ModuleLinkImpl)super.getDelegate();
  }

  public ModuleLinkImpl(final com.intellij.openapi.module.impl.ModuleLinkImpl delegate) {
    super(delegate);
  }

  public ModuleLinkImpl(Module module, Module parentModule) {
    this(new com.intellij.openapi.module.impl.ModuleLinkImpl(module, parentModule));
  }

  public ModuleLinkImpl(String moduleName, Module parentModule) {
    this(new com.intellij.openapi.module.impl.ModuleLinkImpl(moduleName, parentModule));
  }

  public @Nullable Module getModule() {
    return getDelegate().getModule();
  }

  public String getId() {
    return getDelegate().getId();
  }

  public boolean hasId(String id) {
    return getDelegate().hasId(id);
  }

  public boolean resolveElement(ModulesProvider provider) {
    return getDelegate().resolveElement(provider);
  }

  public String getName() {
    return getDelegate().getName();
  }

  public ModuleLinkImpl clone() {
    return new ModuleLinkImpl((com.intellij.openapi.module.impl.ModuleLinkImpl)getDelegate().clone());
  }
}
