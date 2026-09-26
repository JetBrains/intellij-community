// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inheritance

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence

/**
 * Provides information about classes/interfaces that will be implicitly subclassed/implemented at runtime,
 * e.g. by some framework (CGLIB Proxy in Spring).
 *
 * @author Nicolay Mitropolsky
 */
public abstract class ImplicitSubclassProvider {

  /**
   * Checks if this provider could probably provide a subclass for passed psiClass.
   * **Note:** this check is expected to be cheap. If it requires long computations then it is better just to return true.

   * @param psiClass a class to check for possible subclass.
   *
   * @return `false` if definitely no subclass will be created for the psiClass and all further checks could be skipped;
   * `true` if a subclass for the psiClass will probably be created,
   * and then you should call [getSubclassingInfo] to get concrete information about created subclass.
   */
  public abstract fun isApplicableTo(psiClass: PsiClass): Boolean


  /**
   * **Note:** assumes that you have called [isApplicableTo] and will not check it again.
   * So you can get wrong results if you haven't check.

   * @param psiClass a class to check for possible subclass.
   *
   * @return  an info about implicitly created subclass, or `null` if given class will not be subclassed.
   */
  public abstract fun getSubclassingInfo(psiClass: PsiClass): SubclassingInfo?

  /**
   * Information about implicitly overridden methods.
   * @property description an explanation why this method was overridden.
   * @property acceptedModifiers modifiers that allowed to be overriden. by default: all not 'private' modifiers.
   */
  public class OverridingInfo @JvmOverloads constructor(
    @Nls(capitalization = Sentence)
    public val description: String,
    public val acceptedModifiers: Array<JvmModifier> = arrayOf(JvmModifier.PROTECTED, JvmModifier.PACKAGE_LOCAL, JvmModifier.PUBLIC),
  )

  /**
   * Information about implicitly created subclass.
   * @property description an explanation why this class was subclassed.
   * @property isAbstract is created subclass abstract.
   * @property methodsInfo map of methods overridden in class and corresponding [OverridingInfo]s,
   * or `null` if no method-level info is provided
   */
  public class SubclassingInfo @JvmOverloads constructor(
    @Nls(capitalization = Sentence)
    public val description: String,
    public val methodsInfo: Map<PsiMethod, OverridingInfo>? = null,
    public val isAbstract: Boolean = false,
  )

  public companion object {
    @JvmField
    public val EP_NAME: ExtensionPointName<ImplicitSubclassProvider> = ExtensionPointName.create("com.intellij.codeInsight.implicitSubclassProvider")
  }

}
