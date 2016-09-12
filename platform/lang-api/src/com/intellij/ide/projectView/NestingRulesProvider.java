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
 * This interface is intended for a provider of nesting rules for the project view.
 * Implementations of the {@link #addFileNestingRules} method should pass
 * the longest possible file name suffix to the consumer.
 * Usually this suffix starts with a dot. For example ".js"->".min.js".
 * You can specify a custom provider in the {@code plugin.xml} file:<pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;projectViewNestingRulesProvider implementation="my.package.MyRulesProvider"/&gt;
 * &lt;/extensions&gt;</pre>
 */
public interface NestingRulesProvider {
  void addFileNestingRules(@NotNull Consumer consumer);

  interface Consumer {
    void addNestingRule(@NotNull String parentFileSuffix, @NotNull String childFileSuffix);
  }
}
