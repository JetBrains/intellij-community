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
 * This class defines an extension, allowing to customise dependency analysis in cases when JPS detects, that
 * annotations for certain program elements are changed. Currently annotations on classes, fields and methods are tracked.
 * For methods, their parameter annotations are tracked too.
 * Every time changes in annotation set on mentioned program elements are detected, all registered extensions are queried, and
 * all requested dependencies are marked for recompilation.
 *
 * <p/>
 * To register custom AnnotationsChangeTracker, a JPS plugin must:
 * <ul>
 * <li> define a subclass of this class implementing desired callback methods
 * <li> register this implementation via standard java Service Provider Interface mechanism:
 *   <ul>
 *     <li>create a file 'META-INF/services/org.jetbrains.jps.builders.java.dependencyView.AnnotationsChangeTracker'
 *     <li>containing fully qualified name of extension implementation, e.g. "org.plugin-name.MyAnnotationsChangeTrackerImpl"
 *   </ul>
 * </ul>
 * @author Eugene Zhuravlev
 * @noinspection UnusedParameters
 */
public abstract class AnnotationsChangeTracker {

  /**
   * Enumeration defining all possible places that extension requests to recompile
   */
  public enum Recompile {
    /**
     * If present in the returned result set, the usages of the annotated program element (class, field, method) will be affected.
     * it means that files where this program element is references, will be marked for recompilation
     */
    USAGES,

    /**
     * If present in the returned result set, the subclasses of the annotated class will be affected.
     * If returned for an annotated field/method, the subclasses of the class containing this field/method will be affected.
     */
    SUBCLASSES;
  }

  /**
   * Utility object specifying extension's return result, when all possible dependencies on the item being examined, should be recompiled
   * See {@link Recompile}
   */
  public static final Set<Recompile> RECOMPILE_ALL = Collections.unmodifiableSet(EnumSet.allOf(Recompile.class));

  /**
   * Utility object specifying extension's return result, indicating that no additional files should be recompiled.
   * See {@link Recompile}
   */
  public static final Set<Recompile> RECOMPILE_NONE = Collections.unmodifiableSet(EnumSet.noneOf(Recompile.class));

  /**
   * Invoked when changes in annotation list or parameter annotations for some method are detected
   * @param method the method in question
   * @param annotationsDiff differences descriptor for annotations on the method
   * @param paramAnnotationsDiff differences descriptor on method parameters annotations
   * @return a set of specifiers, determining what places in the program should be recompiled, see {@link Recompile}
   */
  @NotNull
  public Set<Recompile> methodAnnotationsChanged(
    DependencyContext context, MethodRepr method,
    Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff,
    Difference.Specifier<ParamAnnotation, Difference> paramAnnotationsDiff
  ) {
    return RECOMPILE_NONE;
  }

  /**
   * Invoked when changes in annotation list for some field are detected
   * @param context for conversion between string and numeric name representations in dependency graph
   * @param field the field in question
   * @param annotationsDiff differences descriptor for annotations on the field
   * @return a set of specifiers, determining what places in the program should be recompiled, see {@link Recompile}
   */
  @NotNull
  public Set<Recompile> fieldAnnotationsChanged(NamingContext context, FieldRepr field, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    return RECOMPILE_NONE;
  }

  /**
   * Invoked when changes in annotation list for some class are detected
   * @param context for conversion between string and numeric name representations in dependency graph
   * @param aClass the class in question
   * @param annotationsDiff differences descriptor for the class annotations
   * @return a set of specifiers, determining what places in the program should be recompiled, see {@link Recompile}
   */
  @NotNull
  public Set<Recompile> classAnnotationsChanged(NamingContext context, ClassRepr aClass, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    return RECOMPILE_NONE;
  }
}
