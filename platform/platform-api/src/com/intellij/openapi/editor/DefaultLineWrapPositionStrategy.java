/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

/**
 * Default {@link LineWrapPositionStrategy} implementation. Is assumed to provide language-agnostic algorithm that may
 * be used with almost any kind of text.
 *
 * @author Denis Zhdanov
 * @since Aug 25, 2010 11:33:00 AM
 */
public class DefaultLineWrapPositionStrategy extends GenericLineWrapPositionStrategy {

  public DefaultLineWrapPositionStrategy() {
    // Commas.
    addRule(new Rule(',', WrapCondition.AFTER));

    // Symbols to wrap either before or after.
    addRule(new Rule(' '));
    addRule(new Rule('\t'));

    // Symbols to wrap after.
    addRule(new Rule(';', WrapCondition.AFTER));
    addRule(new Rule(')', WrapCondition.AFTER));

    // Symbols to wrap before
    addRule(new Rule('(', WrapCondition.BEFORE));
    addRule(new Rule('.', WrapCondition.BEFORE));
  }
}
