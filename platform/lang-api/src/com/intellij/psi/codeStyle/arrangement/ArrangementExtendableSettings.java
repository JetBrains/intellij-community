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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementRuleAliasToken;

import java.util.List;
import java.util.Set;

/**
 * Arrangement settings that can be extended by aliasing sequence of rules to one token,
 * e.g. alias: 'visibility' -> {'public', 'protected', 'private'}
 *
 * @author Svetlana.Zemlyanskaya
 */
public interface ArrangementExtendableSettings extends ArrangementSettings {

  Set<StdArrangementRuleAliasToken> getRuleAliases();

  /**
   * Use this method to get all rules with replaced custom aliases
   */
  List<ArrangementSectionRule> getExtendedSectionRules();
}
