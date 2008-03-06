/*
 * @author max
 */
package com.intellij.psi.stubs;

import java.lang.annotation.*;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SerializerClass {
  Class<? extends StubSerializer> value();
}