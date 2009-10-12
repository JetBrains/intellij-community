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

package com.intellij.diagnostic.logging;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jdom.Element;

import java.util.List;

/**
 * User: anna
 * Date: 22-Mar-2006
 */
public abstract class LogFilterRegistrar implements PersistentStateComponent<Element> {
  public abstract void registerFilter(LogFilter filter);

  public abstract List<LogFilter> getRegisteredLogFilters();

  public abstract boolean isFilterSelected(LogFilter filter);

  public abstract void setFilterSelected(LogFilter filter, boolean state);

}
