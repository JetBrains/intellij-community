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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.xml.XmlBundle;

/**
 * User: anna
 * Date: Jun 22, 2005
 */
public interface GroupNames {
  String ABSTRACTION_GROUP_NAME = InspectionsBundle.message("group.names.abstraction.issues");
  String ASSIGNMENT_GROUP_NAME = InspectionsBundle.message("group.names.assignment.issues");
  String BUGS_GROUP_NAME = InspectionsBundle.message("group.names.probable.bugs");
  String BITWISE_GROUP_NAME = InspectionsBundle.message("group.names.bitwise.operation.issues");
  String CLASSLAYOUT_GROUP_NAME = InspectionsBundle.message("group.names.class.structure");
  String CLASSMETRICS_GROUP_NAME = InspectionsBundle.message("group.names.class.metrics");
  String CONFUSING_GROUP_NAME = InspectionsBundle.message("group.names.potentially.confusing.code.constructs");
  String ENCAPSULATION_GROUP_NAME = InspectionsBundle.message("group.names.encapsulation.issues");
  String ERRORHANDLING_GROUP_NAME = InspectionsBundle.message("group.names.error.handling");
  String FINALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.finalization.issues");
  String IMPORTS_GROUP_NAME = InspectionsBundle.message("group.names.imports");
  String INITIALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.initialization.issues");
  String INTERNATIONALIZATION_GROUP_NAME = InspectionsBundle.message("group.names.internationalization.issues");
  String JUNIT_GROUP_NAME = InspectionsBundle.message("group.names.junit.issues");
  String LOGGING_GROUP_NAME = InspectionsBundle.message("group.names.logging.issues");
  String MATURITY_GROUP_NAME = InspectionsBundle.message("group.names.code.maturity.issues");
  String METHODMETRICS_GROUP_NAME = InspectionsBundle.message("group.names.method.metrics");
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
  String JDK15_SPECIFIC_GROUP_NAME = InspectionsBundle.message("group.names.j2sdk5.0.specific.issues.and.migration.aids");
  String JAVABEANS_GROUP_NAME = InspectionsBundle.message("group.names.javabeans.issues");
  String INHERITANCE_GROUP_NAME = InspectionsBundle.message("group.names.inheritance.issues");
  String DATA_FLOW_ISSUES = InspectionsBundle.message("group.names.data.flow.issues");
  String DECLARATION_REDUNDANCY = InspectionsBundle.message("group.names.declaration.redundancy");

  String PACKAGING_GROUP_NAME = InspectionsBundle.message("group.names.packaging.issues");
  String DEPENDENCY_GROUP_NAME = InspectionsBundle.message("group.names.dependency.issues");
  String MODULARIZATION_GROUP_NAME = InspectionsBundle.message("group.names.modularization.issues");

  String GENERAL_GROUP_NAME = InspectionsBundle.message("inspection.general.tools.group.name");
  String HTML_INSPECTIONS = XmlBundle.message("html.inspections.group.name");
  String JSP_INSPECTIONS = XmlBundle.message("jsp.inspections.group.name");

  String JAVAEE_GROUP_NAME = InspectionsBundle.message("group.names.javaee.issues");

}
