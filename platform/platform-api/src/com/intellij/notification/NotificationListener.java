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
package com.intellij.notification;

import com.intellij.ide.BrowserUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author spleaner
 */
public interface NotificationListener {

  void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event);

  NotificationListener URL_OPENING_LISTENER = new NotificationListener() {
    @Override
    public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull final HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        BrowserUtil.browse(event.getURL());
      }
    }
  };

}
