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
package com.intellij.lang;

/**
 * A language that isn't meant to be user-visible. So it won't be shown in the popups suggesting a user to choose a language, e.g. for injection.
 * This marker interface can be used for languages that are implementation details, e.g. languages of some lazy-parseable element type,
 * or specific dialects chosen by a {@link com.intellij.psi.LanguageSubstitutor}.
 *
 * @see com.intellij.psi.templateLanguages.TemplateLanguage
 * @see InjectableLanguage
 * @author peter
 */
public interface DependentLanguage {
}
