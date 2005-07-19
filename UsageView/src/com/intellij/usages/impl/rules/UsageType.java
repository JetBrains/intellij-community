/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 9:36:12 PM
 * To change this template use File | Settings | File Templates.
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl.rules;

public class UsageType {
  public static final UsageType CLASS_INSTANCE_OF = new UsageType("Usage in instanceof");
  public static final UsageType CLASS_IMPORT = new UsageType("Usage in import");
  public static final UsageType CLASS_CAST_TO = new UsageType("Usage in cast target type");
  public static final UsageType CLASS_EXTENDS_IMPLEMENTS_LIST = new UsageType("Usage in extends/implements clause");
  public static final UsageType CLASS_STATIC_MEMBER_ACCESS = new UsageType("Class static member access");
  public static final UsageType CLASS_METHOD_THROWS_LIST = new UsageType("Method throws list");
  public static final UsageType CLASS_CLASS_OBJECT_ACCESS = new UsageType("Usage in .class");
  public static final UsageType CLASS_FIELD_DECLARATION = new UsageType("Field declaration");
  public static final UsageType CLASS_LOCAL_VAR_DECLARATION = new UsageType("Local variable declaration");
  public static final UsageType CLASS_METHOD_PARAMETER_DECLARATION = new UsageType("Method parameter declaration");
  public static final UsageType CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION = new UsageType("Catch clause parameter declaration");
  public static final UsageType CLASS_METHOD_RETURN_TYPE = new UsageType("Method return type");

  public static final UsageType LITERAL_USAGE = new UsageType("Usage in string constants");
  public static final UsageType COMMENT_USAGE = new UsageType("Usage in comments");

  public static final UsageType UNCLASSIFIED = new UsageType("Unclassified usage");

  private final String myName;

  private UsageType(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }
}
