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

package com.intellij.openapi.compiler.make;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;

import java.util.Collection;

/**
 * @author nik
 *
 * @deprecated this class is part of the obsolete build system which runs as part of the IDE process. Since IDEA 12 plugins need to
 * integrate into 'external build system' instead (http://confluence.jetbrains.com/display/IDEADEV/External+Builder+API+and+Plugins).
 * Since IDEA 13 users cannot switch to the old build system via UI and it will be completely removed in IDEA 14.
 */
public abstract class BuildParticipantProvider {
  public static final ExtensionPointName<BuildParticipantProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.compiler.buildParticipantProvider");


  public abstract Collection<? extends BuildParticipant> getParticipants(Module module);

}
