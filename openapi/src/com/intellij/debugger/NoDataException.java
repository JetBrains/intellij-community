/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger;

/**
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 *
 * Thrown by PositionManager implementation to indicate that it lacks data to perform operation,
 * debugger should try other implementation for this operation. Default implementation never throws
 * this exception.
 */

public class NoDataException extends Exception{
}
