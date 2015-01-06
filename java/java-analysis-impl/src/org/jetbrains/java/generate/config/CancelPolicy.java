/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.config;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * This policy is to cancel.
 */
public class CancelPolicy implements ConflictResolutionPolicy {

    private static final CancelPolicy instance = new CancelPolicy();

    private CancelPolicy() {}

    public static CancelPolicy getInstance() {
        return instance;
    }

    public void setNewMethodStrategy(InsertNewMethodStrategy strategy) {
        // not used as this is cancel
    }

    public PsiMethod applyMethod(PsiClass clazz, PsiMethod existingMethod, @NotNull PsiMethod newMethod, Editor editor) {
        return null;
    }

    public String toString() {
        return "Cancel";
    }
}
