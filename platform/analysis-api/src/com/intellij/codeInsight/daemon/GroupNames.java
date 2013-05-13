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

import com.intellij.codeInspection.InspectionsBundle;

/**
 * @author anna
 * Date: Jun 22, 2005
 */
public interface GroupNames {
  String ABSTRACTION_GROUP_NAME = InspectionsBundle.message("group.names.abstraction.issues");
  String ASSIGNMENT_GROUP_NAME = InspectionsBundle.message("group.names.assignment.issues");
  String BUGS_GROUP_NAME = InspectionsBundle.message("group.names.probable.bugs");
  String BITWISE_GROUP_NAME = InspectionsBundle.message("group.names.bitwise.operation.issues");
  String CLASS_LAYOUT_GROUP_NAME = InspectionsBundle.message("group.names.class.structure");
  String CLASS_METRICS_GROUP_NAME = InspectionsBundle.message("group.names.class.metrics");
  String COMPILER_ISSUES = InspectionsBundle.message("group.names.compiler.issues");
  String CONFUSING_GROUP_NAME = InspectionsBundle.message("group.names.potentially.confusing.code.constructs");
  String ENCAPSULATION_GROUP_NAME = InspectionsBundle.message("group.names.encapsulation.issues");
  String ERROR_HANDLING_GROUP_NAME = InspectionsBundle.message("group.names.error.handling");
  String FINALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.finalization.issues");
  String IMPORTS_GROUP_NAME = InspectionsBundle.message("group.names.imports");
  String INITIALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.initialization.issues");
  String INTERNATIONALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.internationalization.issues");
  String JUNIT_GROUP_NAME = InspectionsBundle.message("group.names.junit.issues");
  String LOGGING_GROUP_NAME = InspectionsBundle.message("group.names.logging.issues");
  String MATURITY_GROUP_NAME = InspectionsBundle.message("group.names.code.maturity.issues");
  String METHOD_METRICS_GROUP_NAME = InspectionsBundle.message("group.names.method.metrics");
  String NAMING_CONVENTIONS_GROUP_NAME = InspectionsBundle.message("group.names.naming.conventions");
  String PERFORMANCE_GROUP_NAME = InspectionsBundle.message("group.names.performance.issues");
  String MEMORY_GROUP_NAME = InspectionsBundle.message("group.names.memory.issues");
  String JDK_GROUP_NAME = InspectionsBundle.message("group.names.java.language.level.issues");
  String PORTABILITY_GROUP_NAME = InspectionsBundle.message("group.names.portability.issues");
  String SECURITY_GROUP_NAME = InspectionsBundle.message("group.names.security.issues");
  String SERIALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.serialization.issues");
  String STYLE_GROUP_NAME = InspectionsBundle.message("group.names.code.style.issues");
  String THREADING_GROUP_NAME = InspectionsBundle.message("group.names.threading.issues");
  String VERBOSE_GROUP_NAME = InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  String VISIBILITY_GROUP_NAME = InspectionsBundle.message("group.names.visibility.issues");
  String CLONEABLE_GROUP_NAME = InspectionsBundle.message("group.names.cloning.issues");
  String RESOURCE_GROUP_NAME = InspectionsBundle.message("group.names.resource.management.issues");
  String J2ME_GROUP_NAME = InspectionsBundle.message("group.names.j2me.issues");
  String CONTROL_FLOW_GROUP_NAME = InspectionsBundle.message("group.names.control.flow.issues");
  String NUMERIC_GROUP_NAME = InspectionsBundle.message("group.names.numeric.issues");
  String LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME = InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  String JAVABEANS_GROUP_NAME = InspectionsBundle.message("group.names.javabeans.issues");
  String INHERITANCE_GROUP_NAME = InspectionsBundle.message("group.names.inheritance.issues");
  String DATA_FLOW_ISSUES = InspectionsBundle.message("group.names.data.flow.issues");
  String DECLARATION_REDUNDANCY = InspectionsBundle.message("group.names.declaration.redundancy");
  String PACKAGING_GROUP_NAME = InspectionsBundle.message("group.names.packaging.issues");
  String DEPENDENCY_GROUP_NAME = InspectionsBundle.message("group.names.dependency.issues");
  String MODULARIZATION_GROUP_NAME = InspectionsBundle.message("group.names.modularization.issues");
  String JAVAEE_GROUP_NAME = InspectionsBundle.message("group.names.javaee.issues");
  String CONCURRENCY_ANNOTATION_ISSUES = InspectionsBundle.message("group.names.concurrency.annotation.issues");
  String JAVADOC_GROUP_NAME = InspectionsBundle.message("group.names.javadoc.issues");
  String PROPERTIES_GROUP_NAME = InspectionsBundle.message("group.names.properties.files");
}
