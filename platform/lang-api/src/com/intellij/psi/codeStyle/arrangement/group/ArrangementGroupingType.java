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
package com.intellij.psi.codeStyle.arrangement.group;

/**
 * Enumerates available grouping types.
 * 
 * @author Denis Zhdanov
 * @since 9/17/12 11:47 AM
 */
public enum ArrangementGroupingType {
  
  GETTERS_AND_SETTERS,

  OVERRIDDEN_METHODS,

  DEPENDENT_METHODS,

  GROUP_PROPERTY_FIELD_WITH_GETTER_SETTER
}
