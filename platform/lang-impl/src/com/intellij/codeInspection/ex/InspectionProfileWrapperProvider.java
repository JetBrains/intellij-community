/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for retrieving {@link InspectionProfileWrapper} objects.
 *
 * @author Denis Zhdanov
 * @since Aug 13, 2010 10:26:33 AM
 */
public interface InspectionProfileWrapperProvider {

  /**
   * Generic-purpose key object that is intended to be used for customizing inspection profile wrapper retrieval
   * via {@link UserDataHolder} API.
   */
  Key<InspectionProfileWrapperProvider> KEY = Key.create(InspectionProfileWrapperProvider.class.getName());

  /**
   * @return    managed inspection profile wrapper
   */
  @NotNull
  InspectionProfileWrapper getWrapper();
}
