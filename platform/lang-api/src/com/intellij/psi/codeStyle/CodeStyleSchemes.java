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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.ServiceManager;

/**
 * @author MYakovlev
 * Date: Jul 19, 2002
 */
public abstract class CodeStyleSchemes {

  public static CodeStyleSchemes getInstance(){
    return ServiceManager.getService(CodeStyleSchemes.class);
  }

  public abstract CodeStyleScheme[] getSchemes();

  public abstract CodeStyleScheme getCurrentScheme();

  public abstract void setCurrentScheme(CodeStyleScheme scheme);

  public abstract CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme);

  public abstract void deleteScheme(CodeStyleScheme scheme);

  public abstract CodeStyleScheme findSchemeByName(String name);

  public abstract CodeStyleScheme getDefaultScheme();

  public abstract void addScheme(CodeStyleScheme currentScheme);
}

