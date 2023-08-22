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
package com.intellij.ant;

/**
 * This ant task is used to instrument compiled classes with idea
 * extensions without compiling java sources.
 */
public class InstrumentIdeaExtensions extends Javac2 {
    /**
     * Customize behavior of {@link Javac2} disabling compilation of java classes.
     *
     * @return false, meaning that java classes are not compiled
     * @see Javac2#areJavaClassesCompiled()
     */
    @Override
    protected boolean areJavaClassesCompiled() {
        return false;
    }
}
