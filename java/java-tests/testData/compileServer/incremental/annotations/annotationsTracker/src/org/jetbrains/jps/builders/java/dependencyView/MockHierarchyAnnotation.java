package org.jetbrains.jps.builders.java.dependencyView;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface MockHierarchyAnnotation {
}
