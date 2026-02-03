// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.Nullability
import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope

public open class ExpectedTypeWithNullability(public val type: JvmType, public val nullability: Nullability) : ExpectedType {
    override fun getTheType(): JvmType = type

    override fun getTheKind(): ExpectedType.Kind = ExpectedType.Kind.EXACT

    public companion object {
        public fun createExpectedKotlinType(type: JvmType, nullability: Nullability): ExpectedTypeWithNullability = ExpectedTypeWithNullability(type, nullability)

        /**
         * A placeholder to denote "This type is invalid". Only thing this type does is returning `false` for `isValid()` function.
         */
        public val INVALID_TYPE: ExpectedTypeWithNullability = createExpectedKotlinType(object : PsiType(emptyArray()) {
            override fun <A : Any?> accept(visitor: PsiTypeVisitor<A>): A {
                return visitor.visitType(PsiTypes.nullType())
            }

            override fun getPresentableText(): String = ""

            override fun getCanonicalText(): String = ""

            override fun isValid(): Boolean = false

            override fun equalsToText(text: String): Boolean = false

            override fun getResolveScope(): GlobalSearchScope? = null

            override fun getSuperTypes(): Array<PsiType> = emptyArray()
        }, Nullability.UNKNOWN)
    }
}