/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

/**
 * @deprecated specify groupBundle="messages.InspectionsBundle" and corresponding groupKey attribute in localInspection tag instead of
 * using constant from this class
 */
@Deprecated
public interface GroupNames {
  /** @deprecated use groupKey="group.names.probable.bugs" instead */
  @Deprecated
  String BUGS_GROUP_NAME = "Probable bugs";
  /** @deprecated use groupKey="group.names.compiler.issues" instead */
  @Deprecated
  String COMPILER_ISSUES = "Compiler issues";
  /** @deprecated use groupKey="group.names.potentially.confusing.code.constructs" instead */
  @Deprecated
  String CONFUSING_GROUP_NAME = "Potentially confusing code constructs";
  /** @deprecated use groupKey="group.names.encapsulation.issues" instead */
  @Deprecated
  String ENCAPSULATION_GROUP_NAME = "Encapsulation";
  /** @deprecated use groupKey="group.names.imports" instead */
  @Deprecated
  String IMPORTS_GROUP_NAME = "Imports";
  /** @deprecated use groupKey="group.names.initialization.issues" instead */
  @Deprecated
  String INITIALIZATION_GROUP_NAME = "Initialization";
  /** @deprecated use groupKey="group.names.internationalization.issues" instead */
  @Deprecated
  String INTERNATIONALIZATION_GROUP_NAME = "Internationalization";
    /** @deprecated use groupKey="group.names.logging.issues" instead */
  @Deprecated
  String LOGGING_GROUP_NAME = "Logging";
    /** @deprecated use groupKey="group.names.code.maturity.issues" instead */
  @Deprecated
  String MATURITY_GROUP_NAME = "Code maturity";
    /** @deprecated use groupKey="group.names.naming.conventions" instead */
  @Deprecated
  String NAMING_CONVENTIONS_GROUP_NAME = "Naming conventions";
    /** @deprecated use groupKey="group.names.performance.issues" instead */
  @Deprecated
  String PERFORMANCE_GROUP_NAME = "Performance";
  /** @deprecated use groupKey="group.names.code.style.issues" instead */
  @Deprecated
  String STYLE_GROUP_NAME = "Code style issues";
  /** @deprecated use groupKey="group.names.visibility.issues" instead */
  @Deprecated
  String VISIBILITY_GROUP_NAME = "Visibility";
  /** @deprecated use groupKey="group.names.j2me.issues" instead */
  @Deprecated
  String J2ME_GROUP_NAME = "Embedded";
  /** @deprecated use groupKey="group.names.inheritance.issues" instead */
  @Deprecated
  String INHERITANCE_GROUP_NAME = "Inheritance issues";
  /** @deprecated use groupKey="group.names.declaration.redundancy" instead */
  @Deprecated
  String DECLARATION_REDUNDANCY = "Declaration redundancy";
  /** @deprecated use groupKey="group.names.dependency.issues" instead */
  @Deprecated
  String DEPENDENCY_GROUP_NAME = "Dependency issues";
  /** @deprecated use groupKey="group.names.modularization.issues" instead */
  @Deprecated
  String MODULARIZATION_GROUP_NAME = "Modularization issues";
  /** @deprecated use groupKey="group.names.properties.files" instead */
  @Deprecated
  String PROPERTIES_GROUP_NAME = "Properties files";
}
