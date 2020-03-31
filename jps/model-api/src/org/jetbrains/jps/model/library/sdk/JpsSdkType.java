/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.library.sdk;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.library.JpsLibraryType;

public abstract class JpsSdkType<P extends JpsElement> extends JpsElementTypeBase<JpsSdk<P>> implements JpsLibraryType<JpsSdk<P>> {
  private final JpsElementChildRole<P> mySdkPropertiesRole = new JpsElementChildRole<>();

  public final JpsElementChildRole<P> getSdkPropertiesRole() {
    return mySdkPropertiesRole;
  }

  public String getPresentableName() {
    return StringUtil.getShortName(getClass());
  }
}
