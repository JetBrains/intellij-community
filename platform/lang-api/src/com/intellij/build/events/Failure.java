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
package com.intellij.build.events;

import com.intellij.notification.Notification;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public interface Failure {
  @Nullable
  @BuildEventsNls.Message
  String getMessage();

  @Nullable
  @BuildEventsNls.Description
  String getDescription();

  @Nullable
  default Throwable getError() {return null;}

  List<? extends Failure> getCauses();

  @Nullable
  default Notification getNotification() {return null;}

  @Nullable
  default Navigatable getNavigatable() {return null;}
}
