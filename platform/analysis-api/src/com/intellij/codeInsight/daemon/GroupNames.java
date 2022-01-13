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

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated specify groupBundle="messages.InspectionsBundle" and corresponding groupKey attribute in localInspection tag instead of
 * using constant from this class
 */
@Deprecated
public interface GroupNames {
  /** @deprecated use groupKey="group.names.probable.bugs" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String BUGS_GROUP_NAME = "Probable bugs";
  /** @deprecated use groupKey="group.names.compiler.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String COMPILER_ISSUES = "Compiler issues";
  /** @deprecated use groupKey="group.names.potentially.confusing.code.constructs" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String CONFUSING_GROUP_NAME = "Potentially confusing code constructs";
  /** @deprecated use groupKey="group.names.encapsulation.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String ENCAPSULATION_GROUP_NAME = "Encapsulation";
  /** @deprecated use groupKey="group.names.imports" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String IMPORTS_GROUP_NAME = "Imports";
  /** @deprecated use groupKey="group.names.initialization.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String INITIALIZATION_GROUP_NAME = "Initialization";
  /** @deprecated use groupKey="group.names.internationalization.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String INTERNATIONALIZATION_GROUP_NAME = "Internationalization";
    /** @deprecated use groupKey="group.names.logging.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String LOGGING_GROUP_NAME = "Logging";
  /** @deprecated use groupKey="group.names.naming.conventions" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String NAMING_CONVENTIONS_GROUP_NAME = "Naming conventions";
  /** @deprecated use groupKey="group.names.code.style.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String STYLE_GROUP_NAME = "Code style issues";

  /** @deprecated use groupKey="group.names.declaration.redundancy" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String DECLARATION_REDUNDANCY = "Declaration redundancy";
  /** @deprecated use groupKey="group.names.modularization.issues" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String MODULARIZATION_GROUP_NAME = "Modularization issues";
  /** @deprecated use groupKey="group.names.properties.files" instead */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String PROPERTIES_GROUP_NAME = "Properties files";
}
