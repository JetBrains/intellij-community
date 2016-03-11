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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.Editor;

import javax.swing.text.Document;

/**
 * An implementation of {@link Editor} on top of a plain {@link javax.swing.text.JTextComponent}.
 * The corresponding document ({@link #getDocument()}) is a wrapper over Swing {@link Document} and
 * doesn't require write action for modifications.
 */
public interface TextComponentEditor extends Editor {
}
