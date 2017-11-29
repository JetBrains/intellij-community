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
package com.intellij.ide.ui;

import java.util.EventListener;

/**
 * If you are interested in listening UI changes you have to
 * use this listener instead of registening {@code PropertyChangeListener}
 * into {@code UIManager}
 *
 * @author Vladimir Kondratyev
 */
public interface LafManagerListener extends EventListener{
  void lookAndFeelChanged(LafManager source);
}
