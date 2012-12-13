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

import com.intellij.openapi.Disposable;
import com.intellij.util.Function;

/**
 * @author Konstantin Bulenkov
 */
public interface DnDSupportBuilder {
  DnDSupportBuilder disableAsTarget();
  DnDSupportBuilder disableAsSource();
  DnDSupportBuilder enableAsNativeTarget();
  DnDSupportBuilder setImageProvider(Function<DnDActionInfo, DnDImage> provider);
  DnDSupportBuilder setBeanProvider(Function<DnDActionInfo, DnDDragStartBean> provider);
  DnDSupportBuilder setDropHandler(DnDDropHandler handler);
  DnDSupportBuilder setTargetChecker(DnDTargetChecker checker);
  DnDSupportBuilder setDropActionHandler(DnDDropActionHandler handler);
  DnDSupportBuilder setDropEndedCallback(Runnable callback);
  DnDSupportBuilder setCleanUpOnLeaveCallback(Runnable callback);
  DnDSupportBuilder setDisposableParent(Disposable parent);
  void install();
}
