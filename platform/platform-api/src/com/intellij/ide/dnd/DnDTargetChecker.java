/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.dnd;

/**
 * @author Konstantin Bulenkov
 */
public interface DnDTargetChecker {
  /**
   * @param event Drag-n-Drop event
   * @return <code>true</code> - if this target is unable to handle the event and parent component should be asked to process it.
   *         <code>false</code> - if this target is unable to handle the event and parent component should NOT be asked to process it.
   *
   * @see DnDEvent#setDropPossible(boolean, String)
   * @see DnDEvent#setDropPossible(String, DropActionHandler)
   */
  boolean update(DnDEvent event);
}
