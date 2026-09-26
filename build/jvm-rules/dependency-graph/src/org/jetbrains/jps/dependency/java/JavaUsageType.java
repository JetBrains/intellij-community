// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

public enum JavaUsageType  {
  ANNOTATION, MODULE, IMPORT_STATIC_ON_DEMAND, IMPORT_STATIC_MEMBER, METHOD, META_METHOD, FIELD, CLASS, CLASS_AS_GENERIC_BOUND, CLASS_EXTENDS, CLASS_NEW_USAGE
}
