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
package jetbrains.antlayout.datatypes;

/**
 * @author max
 */
public class IdeaModule extends IdeaModuleBase {
  public IdeaModule() {
  }

  public IdeaModule(IdeaModule fileset) {
    super(fileset);
  }

  @Override
  protected String getKind() {
    return "production";
  }

  @Override
  protected String getOutputDirProperty() {
    return "module." + getName() + ".output.main";
  }
}
