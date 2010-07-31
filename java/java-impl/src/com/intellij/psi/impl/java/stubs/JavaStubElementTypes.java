/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.impl.source.JavaFileElementType;

public class JavaStubElementTypes {
  public static final JavaClassElementType CLASS = new JavaClassElementType("CLASS");
  public static final JavaClassElementType ANONYMOUS_CLASS = new JavaClassElementType("ANONYMOUS_CLASS");
  public static final JavaClassElementType ENUM_CONSTANT_INITIALIZER = new JavaClassElementType("ENUM_CONSTANT_INITIALIZER");

  public static final JavaModifierListElementType MODIFIER_LIST = new JavaModifierListElementType();
  public static final JavaMethodElementType METHOD = new JavaMethodElementType("METHOD");
  public static final JavaMethodElementType ANNOTATION_METHOD = new JavaMethodElementType("ANNOTATION_METHOD");
  public static final JavaFieldStubElementType FIELD = new JavaFieldStubElementType("FIELD");
  public static final JavaFieldStubElementType ENUM_CONSTANT = new JavaFieldStubElementType("ENUM_CONSTANT");

  public static final JavaAnnotationElementType ANNOTATION = new JavaAnnotationElementType();
  public static final JavaClassReferenceListElementType EXTENDS_LIST = new JavaClassReferenceListElementType("EXTENDS_LIST");
  public static final JavaClassReferenceListElementType IMPLEMENTS_LIST = new JavaClassReferenceListElementType("IMPLEMENTS_LIST");
  public static final JavaClassReferenceListElementType THROWS_LIST = new JavaClassReferenceListElementType("THROWS_LIST");
  public static final JavaClassReferenceListElementType EXTENDS_BOUND_LIST = new JavaClassReferenceListElementType("EXTENDS_BOUND_LIST");

  public static final JavaParameterElementType PARAMETER = new JavaParameterElementType();
  public static final JavaParameterListElementType PARAMETER_LIST = new JavaParameterListElementType();
  public static final JavaTypeParameterElementType TYPE_PARAMETER = new JavaTypeParameterElementType();
  public static final JavaTypeParameterListElementType TYPE_PARAMETER_LIST = new JavaTypeParameterListElementType();
  public static final JavaClassInitializerElementType CLASS_INITIALIZER = new JavaClassInitializerElementType();
  public static final JavaImportListElementType IMPORT_LIST = new JavaImportListElementType();
  public static final JavaImportStatementElementType IMPORT_STATEMENT = new JavaImportStatementElementType("IMPORT_STATEMENT");
  public static final JavaImportStatementElementType IMPORT_STATIC_STATEMENT = new JavaImportStatementElementType("IMPORT_STATIC_STATEMENT");
}