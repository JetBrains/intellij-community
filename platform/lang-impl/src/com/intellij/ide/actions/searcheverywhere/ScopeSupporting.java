// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public interface ScopeSupporting {

  ScopeDescriptor getScope();

  void setScope(ScopeDescriptor scope);

  List<ScopeDescriptor> getSupportedScopes();
}
