/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 */
public abstract class BuildMessage {
  public enum Kind {
    ERROR, WARNING, INFO, PROGRESS, JPS_INFO, OTHER
  }

  private final String myMessageText;
  private final Kind myKind;

  protected BuildMessage(String messageText, Kind kind) {
    myMessageText = messageText;
    myKind = kind;
  }

  public Kind getKind() {
    return myKind;
  }

  public String getMessageText() {
    return myMessageText;
  }

  public String toString() {
    return getMessageText();
  }
}
