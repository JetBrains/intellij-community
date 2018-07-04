/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.hyperlink;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Describes a hyperlink inside a run dashboard node
 *
 * @author Konstantin Aleev
 */
public interface RunDashboardHyperlinkComponent {
  Key<Object> AIMED_OBJECT = Key.create("RunDashboardAimedObject") ;

  void onClick(@NotNull MouseEvent event);

  void setAimed(boolean aimed);

  boolean isAimed();
}
