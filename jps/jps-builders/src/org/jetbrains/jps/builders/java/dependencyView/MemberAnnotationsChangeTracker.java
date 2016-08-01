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

/**
 * @author Eugene Zhuravlev
 *         Date: 25-Jul-16
 */
public abstract class MemberAnnotationsChangeTracker {

  public enum Action {
    NO_ACTION, RECOMPILE_USAGES;
  }

  public abstract Action methodAnnotationsChanged(
    MethodRepr method,
    Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff,
    Difference.Specifier<ParamAnnotation, Difference> paramAnnotationsDiff
  );

  public abstract Action fieldAnnotationsChanged(FieldRepr field, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff);

}
