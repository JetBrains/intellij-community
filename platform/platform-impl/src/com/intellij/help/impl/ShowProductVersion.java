/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.help.impl;

import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationInfoEx;

/**
 * @author Konstantin Bulenkov
 */
public class ShowProductVersion implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "-version";
  }

  @Override
  public void premain(String[] args) {

  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Override
  public void main(String[] args) {
    System.out.println(ApplicationInfoEx.getInstanceEx().getFullVersion());
    System.exit(0);
  }
}
