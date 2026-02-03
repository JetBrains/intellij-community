import org.jetbrains.annotations.NotNull;

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
class OverrideDemo2 {

  private static class Override2Impl implements OverrideDemo2Interface{
    @Override
    public void perform(<warning descr="Parameter annotated @NotNull should not override non-annotated parameter">@NotNull</warning> Object object) {
      // error in Eclipse: Illegal redefinition of parameter object, inherited method from OverrideDemo2.OverrideDemo2Interface does not constrain this parameter
    }
  }

  private static interface OverrideDemo2Interface {
    void perform(Object object);
  }
}