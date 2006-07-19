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

import com.intellij.usageView.UsageViewBundle;

public class UsageType {
  public static final UsageType CLASS_INSTANCE_OF = new UsageType(UsageViewBundle.message("usage.type.instanceof"));
  public static final UsageType CLASS_IMPORT = new UsageType(UsageViewBundle.message("usage.type.import"));
  public static final UsageType CLASS_CAST_TO = new UsageType(UsageViewBundle.message("usage.type.cast.target"));
  public static final UsageType CLASS_EXTENDS_IMPLEMENTS_LIST = new UsageType(UsageViewBundle.message("usage.type.extends"));
  public static final UsageType CLASS_STATIC_MEMBER_ACCESS = new UsageType(UsageViewBundle.message("usage.type.static.member"));
  public static final UsageType CLASS_METHOD_THROWS_LIST = new UsageType(UsageViewBundle.message("usage.type.throws.list"));
  public static final UsageType CLASS_CLASS_OBJECT_ACCESS = new UsageType(UsageViewBundle.message("usage.type.class.object"));
  public static final UsageType CLASS_FIELD_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.field.declaration"));
  public static final UsageType CLASS_LOCAL_VAR_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.local.declaration"));
  public static final UsageType CLASS_METHOD_PARAMETER_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.parameter.declaration"));
  public static final UsageType CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION = new UsageType(UsageViewBundle.message("usage.type.catch.declaration"));
  public static final UsageType CLASS_METHOD_RETURN_TYPE = new UsageType(UsageViewBundle.message("usage.type.return"));
  public static final UsageType CLASS_NEW_OPERATOR = new UsageType(UsageViewBundle.message("usage.type.new"));

  public static final UsageType LITERAL_USAGE = new UsageType(UsageViewBundle.message("usage.type.string.constant"));
  public static final UsageType COMMENT_USAGE = new UsageType(UsageViewBundle.message("usage.type.comment"));

  public static final UsageType UNCLASSIFIED = new UsageType(UsageViewBundle.message("usage.type.unclassified"));

  private final String myName;

  private UsageType(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }
}
