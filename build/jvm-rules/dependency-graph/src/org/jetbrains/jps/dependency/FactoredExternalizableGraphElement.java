// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

public interface FactoredExternalizableGraphElement<Factor extends ExternalizableGraphElement> extends ExternalizableGraphElement{
  Factor getFactorData();
}
