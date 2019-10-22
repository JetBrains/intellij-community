// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.picocontainer.monitors;

import org.picocontainer.ComponentMonitor;
import org.picocontainer.PicoException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link ComponentMonitor} which collects lifecycle failures
 * and rethrows them on demand after the failures.
 *
 * @author Paul Hammant
 * @author Mauro Talevi
 */
public class LifecycleComponentMonitor implements ComponentMonitor {

    private final ComponentMonitor delegate;
    private final List lifecycleFailures = new ArrayList();

    public LifecycleComponentMonitor(ComponentMonitor delegate) {
        this.delegate = delegate;
    }

    public LifecycleComponentMonitor() {
        delegate = new NullComponentMonitor();
    }

    @Override
    public void instantiating(Constructor constructor) {
        delegate.instantiating(constructor);
    }

    @Override
    public void instantiated(Constructor constructor, long duration) {
        delegate.instantiated(constructor, duration);
    }

    @Override
    public void instantiationFailed(Constructor constructor, Exception cause) {
        delegate.instantiationFailed(constructor, cause);
    }

    @Override
    public void invoking(Method method, Object instance) {
        delegate.invoking(method, instance);
    }

    @Override
    public void invoked(Method method, Object instance, long duration) {
        delegate.invoked(method, instance, duration);
    }

    @Override
    public void invocationFailed(Method method, Object instance, Exception cause) {
        delegate.invocationFailed(method, instance, cause);
    }

    @Override
    public void lifecycleInvocationFailed(Method method, Object instance, RuntimeException cause) {
        lifecycleFailures.add(cause);
        delegate.lifecycleInvocationFailed(method, instance, cause);
    }


    public void rethrowLifecycleFailuresException() {
        throw new LifecycleFailuresException(lifecycleFailures);
    }

    /**
     * Subclass of {@link PicoException} that is thrown when the collected
     * lifecycle failures need to be be collectively rethrown.
     *
     * @author Paul Hammant
     * @author Mauro Talevi
     */
    public class LifecycleFailuresException extends PicoException {

        private List lifecycleFailures;

        public LifecycleFailuresException(List lifecycleFailures) {
            this.lifecycleFailures = lifecycleFailures;
        }

        @Override
        public String getMessage() {
            StringBuffer message = new StringBuffer();
            for ( Iterator i = lifecycleFailures.iterator(); i.hasNext(); ) {
                Exception failure = (Exception)  i.next();
                message.append(failure.getMessage()).append(";  ");
            }
            return message.toString();
        }

        public Collection getFailures() {
            return lifecycleFailures;
        }
    }
}
