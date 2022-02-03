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
package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiField;

class FieldSpec{
    private final boolean setterRequired;
    private final boolean getterRequired;
    private final PsiField field;

    FieldSpec(PsiField field, boolean getterRequired, boolean setterRequired) {
        super();
        this.field = field;
        this.getterRequired = getterRequired;
        this.setterRequired = setterRequired;
    }

    public PsiField getField() {
        return field;
    }

    public boolean isSetterRequired() {
        return setterRequired;
    }

    public boolean isGetterRequired() {
        return getterRequired;
    }
}
