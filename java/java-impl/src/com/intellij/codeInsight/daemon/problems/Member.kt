// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.psi.*
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope

internal class ScopedMember(val member: Member, val scope: SearchScope) {

  val name = member.name

  internal fun hasChanged(other: ScopedMember) = member.hasChanged(other.member)

  override fun equals(other: Any?) = other is ScopedMember && member == other.member

  override fun hashCode(): Int = member.hashCode()

  override fun toString(): String {
    return "ScopedMember(member=$member, scope=$scope)"
  }

  companion object {
    internal fun create(psiMember: PsiMember,
                        scope: SearchScope = PsiSearchHelper.getInstance(psiMember.project).getUseScope(psiMember)): ScopedMember? {
      val member = Member.create(psiMember) ?: return null
      return ScopedMember(member, scope)
    }
  }
}

internal sealed class Member(open val name: String, open val modifiers: Set<String>) {

  internal open fun hasChanged(other: Member): Boolean {
    return name != other.name || modifiers != other.modifiers
  }

  protected abstract fun copy(modifiers: MutableSet<String>): Member

  companion object {
    internal fun create(psiMember: PsiMember) = when (psiMember) {
      is PsiMethod -> Method.create(psiMember)
      is PsiField -> Field.create(psiMember)
      is PsiClass -> Class.create(psiMember)
      is PsiEnumConstant -> EnumConstant.create(psiMember)
      else -> null
    }

    private fun extractModifiers(psiModifierList: PsiModifierList?): Set<String> {
      if (psiModifierList == null) return mutableSetOf()
      return PsiModifier.MODIFIERS.filterTo(mutableSetOf()) { psiModifierList.hasModifierProperty(it) }
    }

    private fun getReferencedClassesNames(referenceList: PsiReferenceList?): Set<String> {
      if (referenceList == null) return emptySet()
      return referenceList.referenceElements.mapTo(mutableSetOf()) { it.qualifiedName }
    }

    private fun getTypeParameters(typeParametersList: PsiTypeParameterList?): List<String> {
      if (typeParametersList == null) return emptyList()
      return typeParametersList.typeParameters.map { it.text }
    }
  }

  internal data class EnumConstant(override val name: String, override val modifiers: Set<String>) : Member(name, modifiers) {

    override fun copy(modifiers: MutableSet<String>): Member = copy(name = this.name, modifiers = modifiers)

    companion object {
      internal fun create(psiEnumConstant: PsiEnumConstant): EnumConstant {
        val name = psiEnumConstant.name
        val modifiers = extractModifiers(psiEnumConstant.modifierList)
        return EnumConstant(name, modifiers)
      }
    }
  }

  internal data class Field(override val name: String,
                            override val modifiers: Set<String>,
                            val type: String) : Member(name, modifiers) {

    override fun hasChanged(other: Member): Boolean {
      return other !is Field || super.hasChanged(other) || type != other.type
    }

    override fun copy(modifiers: MutableSet<String>): Member = copy(name = name, modifiers = modifiers, type = type)

    companion object {
      internal fun create(psiField: PsiField): Field? {
        val name = psiField.name
        val modifiers = extractModifiers(psiField.modifierList)
        val type = psiField.type.canonicalText
        return Field(name, modifiers, type)
      }
    }
  }

  internal data class Method(override val name: String,
                             override val modifiers: Set<String>,
                             val returnType: String?,
                             val paramTypes: List<String>,
                             val throwsTypes: List<String>,
                             val typeParametersList: List<String>) : Member(name, modifiers) {

    override fun hasChanged(other: Member): Boolean {
      return other !is Method || super.hasChanged(other) || returnType != other.returnType ||
             paramTypes != other.paramTypes || throwsTypes != other.throwsTypes ||
             typeParametersList != other.typeParametersList
    }

    override fun copy(modifiers: MutableSet<String>): Member = copy(name = name, modifiers = modifiers, returnType = returnType,
                                                                    paramTypes = paramTypes, throwsTypes = throwsTypes,
                                                                    typeParametersList = typeParametersList)

    companion object {
      internal fun create(psiMethod: PsiMethod): Method? {
        val returnType = psiMethod.returnType?.canonicalText
        if (returnType == null && !psiMethod.isConstructor) return null
        val name = psiMethod.name
        val modifiers = extractModifiers(psiMethod.modifierList)
        val paramTypes = psiMethod.parameterList.parameters.map { it.type.canonicalText }
        val throwsTypes = psiMethod.throwsList.referencedTypes.map { it.canonicalText }
        val typeParameterList = getTypeParameters(psiMethod.typeParameterList)
        return Method(name, modifiers, returnType, paramTypes, throwsTypes, typeParameterList)
      }
    }
  }

  internal data class Class(override val name: String,
                            override val modifiers: Set<String>,
                            val isEnum: Boolean,
                            val isInterface: Boolean,
                            val isAnnotationType: Boolean,
                            val extendsList: Set<String>,
                            val implementsList: Set<String>,
                            val permitsList: Set<String>,
                            val typeParametersList: List<String>) : Member(name, modifiers) {

    override fun hasChanged(other: Member): Boolean {
      return other !is Class || super.hasChanged(other) ||
             isEnum != other.isEnum || isInterface != other.isInterface || isAnnotationType != other.isAnnotationType ||
             extendsList != other.extendsList || implementsList != other.implementsList || permitsList != other.permitsList ||
             typeParametersList != other.typeParametersList
    }

    override fun copy(modifiers: MutableSet<String>): Member = copy(name = name, modifiers = modifiers, isEnum = isEnum,
                                                                    isInterface = isInterface, isAnnotationType = isAnnotationType,
                                                                    extendsList = extendsList, implementsList = implementsList,
                                                                    typeParametersList = typeParametersList)

    companion object {
      internal fun create(psiClass: PsiClass): Class? {
        val name = psiClass.name ?: return null
        val modifiers = extractModifiers(psiClass.modifierList)
        val isEnum = psiClass.isEnum
        val isInterface = psiClass.isInterface
        val isAnnotationType = psiClass.isAnnotationType
        val extendsList = getReferencedClassesNames(psiClass.extendsList)
        val implementsList = getReferencedClassesNames(psiClass.implementsList)
        val permitsList = getReferencedClassesNames(psiClass.permitsList)
        val typeParametersList = getTypeParameters(psiClass.typeParameterList)
        return Class(name, modifiers, isEnum, isInterface, isAnnotationType, extendsList, implementsList, permitsList, typeParametersList)
      }
    }
  }
}