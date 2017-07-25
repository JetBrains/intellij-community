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
/**
 * Classes from this package represent elements of JVM class-files
 * which will be produced from source code of a JVM-based language after compilation.
 * They can be used to implement features for all JVM-based languages at once
 * if these features depend on structure of produced class-files rather than on source code constructions.
 * <p>
 * E.g. if a framework provides an annotation and requires that signatures of methods annotated by it must satisfy some requirements
 * it's possible to write a single inspection which will check this for all JVM-based languages which supports these interfaces.
 *
 * @see com.intellij.lang.jvm.JvmElement
 */
@Experimental
package com.intellij.lang.jvm;

import org.jetbrains.annotations.ApiStatus.Experimental;