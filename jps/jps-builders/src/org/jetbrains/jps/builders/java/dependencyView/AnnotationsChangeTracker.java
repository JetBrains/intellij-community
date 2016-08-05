/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 25-Jul-16
 * @noinspection UnusedParameters
 */
public abstract class AnnotationsChangeTracker {

  public enum Recompile {
    USAGES, SUBCLASSES;
  }

  public static final Set<Recompile> RECOMPILE_ALL = Collections.unmodifiableSet(EnumSet.allOf(Recompile.class));
  public static final Set<Recompile> RECOMPILE_NONE = Collections.unmodifiableSet(EnumSet.noneOf(Recompile.class));

  @NotNull
  public Set<Recompile> methodAnnotationsChanged(
    DependencyContext context, MethodRepr method,
    Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff,
    Difference.Specifier<ParamAnnotation, Difference> paramAnnotationsDiff
  ) {
    return RECOMPILE_NONE;
  }

  @NotNull
  public Set<Recompile> fieldAnnotationsChanged(DependencyContext context, FieldRepr field, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    return RECOMPILE_NONE;
  }

  @NotNull
  public Set<Recompile> classAnnotationsChanged(DependencyContext context, ClassRepr aClass, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    return RECOMPILE_NONE;
  }
}
