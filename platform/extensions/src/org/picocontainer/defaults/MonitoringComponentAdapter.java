/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;

import java.io.Serializable;

/**
 * Abstract {@link ComponentAdapter ComponentAdapter} supporting a
 * {@link ComponentMonitorStrategy ComponentMonitorStrategy}.
 *
 * @author Mauro Talevi
 * @version $Revision: $
 * @see ComponentAdapter
 * @see ComponentMonitorStrategy
 * @since 1.2
 */
public abstract class MonitoringComponentAdapter implements ComponentAdapter, Serializable {
}
