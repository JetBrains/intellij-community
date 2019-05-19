/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * Designates a system-independent path, with {@code /} separators.
 * <p>
 * Corresponding bytecode instrumentation is added by {@code SystemIndependentInstrumentingBuilder}.<p>
 *
 * @see com.intellij.util.PathUtil#toSystemIndependentName(String)
 * @see com.intellij.util.PathUtil#toSystemDependentName(String)
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface SystemIndependent {
}
