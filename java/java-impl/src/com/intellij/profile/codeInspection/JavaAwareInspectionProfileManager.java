/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * User: anna
 * Date: 21-Feb-2008
 */
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.JavaAwareInspectionProfileCoverter;
import com.intellij.codeInspection.ex.InspectionProfileManagerImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.util.messages.MessageBus;

public class JavaAwareInspectionProfileManager extends InspectionProfileManagerImpl {
  public JavaAwareInspectionProfileManager(InspectionToolRegistrar registrar,
                                           SchemesManagerFactory schemesManagerFactory,
                                           MessageBus messageBus) {
    super(registrar, schemesManagerFactory, messageBus);
  }

  @Override
  public InspectionProfileConvertor getConverter() {
    return new JavaAwareInspectionProfileCoverter(this);
  }
}