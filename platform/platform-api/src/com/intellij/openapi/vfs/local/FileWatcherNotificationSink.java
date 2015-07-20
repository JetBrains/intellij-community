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
package com.intellij.openapi.vfs.local;

import com.intellij.notification.NotificationListener;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;

/**
 * @author dslomov
 */
public interface FileWatcherNotificationSink {
  void notifyDirtyPaths(Collection<String> paths);

  void notifyDirtyDirectories(Collection<String> paths);

  void notifyPathsRecursive(Collection<String> paths);

  void notifyPathsCreatedOrDeleted(Collection<String> paths);

  @TestOnly
  void notifyOnEvent();

  void notifyUserOnFailure(String cause, NotificationListener listener);
}
