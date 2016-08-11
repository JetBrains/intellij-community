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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 08-Aug-16
 */
public class MockAnnotationsChangeTracker extends AnnotationsChangeTracker{
  private static final String ANOTATION_NAME = MockAnnotation.class.getName().replace('.', '/');
  private static final String HIERARCHY_ANOTATION_NAME = MockHierarchyAnnotation.class.getName().replace('.', '/');

  @NotNull
  public Set<Recompile> methodAnnotationsChanged(DependencyContext context, MethodRepr method, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff, Difference.Specifier<ParamAnnotation, Difference> paramAnnotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(context, ContainerUtil.concat(
      annotationsDiff.added(),
      annotationsDiff.removed(),
      toTypeCollection(paramAnnotationsDiff.added()),
      toTypeCollection(paramAnnotationsDiff.removed()))
    );
  }

  @NotNull
  public Set<Recompile> fieldAnnotationsChanged(DependencyContext context, FieldRepr field, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(context, ContainerUtil.concat(annotationsDiff.added(), annotationsDiff.removed()));
  }

  @NotNull
  public Set<Recompile> classAnnotationsChanged(DependencyContext context, ClassRepr aClass, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(context, ContainerUtil.concat(annotationsDiff.added(), annotationsDiff.removed()));
  }

  @NotNull
  public Set<Recompile> handleChanges(DependencyContext context, Iterable<TypeRepr.ClassType> changes) {
    final int annot = context.get(ANOTATION_NAME);
    final Set<Recompile> result = EnumSet.noneOf(Recompile.class);
    if (containsAnnotation(annot, changes)) {
      result.add(Recompile.USAGES);
    }
    final int hierarchyAnnot = context.get(HIERARCHY_ANOTATION_NAME);
    if (containsAnnotation(hierarchyAnnot, changes)) {
      result.add(Recompile.SUBCLASSES);
    }
    return result;
  }

  private static Iterable<TypeRepr.ClassType> toTypeCollection(final Iterable<ParamAnnotation> paramCollection) {
    return new Iterable<TypeRepr.ClassType>() {
      public Iterator<TypeRepr.ClassType> iterator() {
        final Iterator<ParamAnnotation> iterator = paramCollection.iterator();
        return new Iterator<TypeRepr.ClassType>() {
          public boolean hasNext() {
            return iterator.hasNext();
          }

          public TypeRepr.ClassType next() {
            return iterator.next().type;
          }

          public void remove() {
            iterator.remove();
          }
        };
      }
    };
  }

  private static boolean containsAnnotation(int annotationName, Iterable<TypeRepr.ClassType> classes) {
    for (TypeRepr.ClassType type : classes) {
      if (annotationName == type.className) {
        return true;
      }
    }
    return false;
  }
}
