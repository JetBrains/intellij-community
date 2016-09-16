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
package com.intellij.ide.projectView;

import org.jetbrains.annotations.NotNull;

/**
 * This interface is intended to provide file nesting rules,
 * which allow to improve folder contents presentation in the project view
 * by showing some files as children of another peer file.
 * It is useful when a folder contains both source file and its compiled output.
 * For example, a generated {@code foo.min.js} file will be shown
 * as a child of a {@code foo.js} file.
 * <br/>Note that nesting logic is based on file names only.<br/>
 * You can specify a custom provider in the {@code plugin.xml} file:<pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;projectViewNestingRulesProvider implementation="my.package.MyRulesProvider"/&gt;
 * &lt;/extensions&gt;</pre>
 */
public interface ProjectViewNestingRulesProvider {
  /**
   * Implementations of this method should pass the longest possible file name suffix to the consumer.
   * Usually this suffix starts with a dot. For example ".js"->".min.js".
   *
   * @param consumer a consumer which maps extensions of a parent file and its child
   */
  void addFileNestingRules(@NotNull Consumer consumer);

  interface Consumer {
    void addNestingRule(@NotNull String parentFileSuffix, @NotNull String childFileSuffix);
  }
}
