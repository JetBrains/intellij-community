/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.jdi;

/**
 * Copied from jvmti.h
 * @author egor
 */
public class JvmtiError {
  private JvmtiError() {
  }

  public static final int NONE = 0;
  public static final int INVALID_THREAD = 10;
  public static final int INVALID_THREAD_GROUP = 11;
  public static final int INVALID_PRIORITY = 12;
  public static final int THREAD_NOT_SUSPENDED = 13;
  public static final int THREAD_SUSPENDED = 14;
  public static final int THREAD_NOT_ALIVE = 15;
  public static final int INVALID_OBJECT = 20;
  public static final int INVALID_CLASS = 21;
  public static final int CLASS_NOT_PREPARED = 22;
  public static final int INVALID_METHODID = 23;
  public static final int INVALID_LOCATION = 24;
  public static final int INVALID_FIELDID = 25;
  public static final int NO_MORE_FRAMES = 31;
  public static final int OPAQUE_FRAME = 32;
  public static final int TYPE_MISMATCH = 34;
  public static final int INVALID_SLOT = 35;
  public static final int DUPLICATE = 40;
  public static final int NOT_FOUND = 41;
  public static final int INVALID_MONITOR = 50;
  public static final int NOT_MONITOR_OWNER = 51;
  public static final int INTERRUPT = 52;
  public static final int INVALID_CLASS_FORMAT = 60;
  public static final int CIRCULAR_CLASS_DEFINITION = 61;
  public static final int FAILS_VERIFICATION = 62;
  public static final int UNSUPPORTED_REDEFINITION_METHOD_ADDED = 63;
  public static final int UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED = 64;
  public static final int INVALID_TYPESTATE = 65;
  public static final int UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED = 66;
  public static final int UNSUPPORTED_REDEFINITION_METHOD_DELETED = 67;
  public static final int UNSUPPORTED_VERSION = 68;
  public static final int NAMES_DONT_MATCH = 69;
  public static final int UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED = 70;
  public static final int UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED = 71;
  public static final int UNMODIFIABLE_CLASS = 79;
  public static final int NOT_AVAILABLE = 98;
  public static final int MUST_POSSESS_CAPABILITY = 99;
  public static final int NULL_POINTER = 100;
  public static final int ABSENT_INFORMATION = 101;
  public static final int INVALID_EVENT_TYPE = 102;
  public static final int ILLEGAL_ARGUMENT = 103;
  public static final int NATIVE_METHOD = 104;
  public static final int CLASS_LOADER_UNSUPPORTED = 106;
  public static final int OUT_OF_MEMORY = 110;
  public static final int ACCESS_DENIED = 111;
  public static final int WRONG_PHASE = 112;
  public static final int INTERNAL = 113;
  public static final int UNATTACHED_THREAD = 115;
  public static final int INVALID_ENVIRONMENT = 116;
}
