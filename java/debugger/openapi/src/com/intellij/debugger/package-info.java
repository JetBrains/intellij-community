// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Provides interfaces for interacting with IDEA's debugger.
 * <p>
 * The main extensibility point of the debugger allows a plugin
 * to provide custom mapping of positions in compiled classes to source code lines,
 * which is mostly necessary for JSP debugging.
 * Such mapping is provided through the {@link com.intellij.debugger.PositionManager} interface.
 * One standard implementation of this interface for application servers
 * compatible with the JSR-45 specification
 * is provided by the {@link com.intellij.debugger.engine.JSR45PositionManager} class.
 * Another implementation of this interface,
 * which can be used as an example for the debugger API,
 * is found in the Tomcat plugin.
 */
package com.intellij.debugger;