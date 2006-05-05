/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Represents a class type.
 *
 * @author max
 */
public abstract class PsiClassType extends PsiType {
  /**
   * The empty array of PSI class types which can be reused to avoid unnecessary allocations.
   */
  public static final PsiClassType[] EMPTY_ARRAY = new PsiClassType[0];
  protected LanguageLevel myLanguageLevel;

  /**
   * Resolves the class reference and returns the resulting class.
   *
   * @return the class instance, or null if the reference resolve failed.
   */
  @Nullable
  public abstract PsiClass resolve();

  /**
   * Returns the non-qualified name of the class referenced by the type.
   *
   * @return the name of the class.
   */
  public abstract String getClassName();

  /**
   * Returns the list of type arguments for the type.
   *
   * @return the array of type arguments, or an empty array if the type does not point to a generic class or interface.
   */
  @NotNull public abstract PsiType[] getParameters();

  public boolean equals(Object obj) {
    if (!(obj instanceof PsiClassType)) return false;
    PsiClassType otherClassType = (PsiClassType) obj;
    if (!isValid() || !otherClassType.isValid()) return false;

    if (this == obj) return true;

    String className = getClassName();
    String otherClassName = otherClassType.getClassName();
    if (!Comparing.equal(className, otherClassName)) return false;

    final ClassResolveResult result = resolveGenerics();
    final ClassResolveResult otherResult = otherClassType.resolveGenerics();
    if (result == otherResult) return true;

    final PsiClass aClass = result.getElement();
    final PsiClass otherClass = otherResult.getElement();
    if (!areClassesEqual(aClass, otherClass)) return false;
    if (aClass == null) return true;
    return PsiUtil.equalOnEquivalentClasses(result.getSubstitutor(), aClass, otherResult.getSubstitutor(), otherClass);
  }

  private static boolean areClassesEqual(final PsiClass aClass, final PsiClass otherClass) {
    if (aClass == null || otherClass == null) return aClass == otherClass;
    return aClass.getManager().areElementsEquivalent(aClass, otherClass);
  }

  /**
   * Checks if the class type has any parameters with no substituted arguments.
   *
   * @return true if the class type has non-substituted parameters, false otherwise
   */
  public boolean hasParameters() {
    final ClassResolveResult resolveResult = resolveGenerics();
    if (resolveResult.getElement() == null) return false;
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(resolveResult.getElement());
    if (!iterator.hasNext()) return false;
    while (iterator.hasNext()) {
      PsiTypeParameter parameter = iterator.next();
      if (resolveResult.getSubstitutor().substitute(parameter) == null) return false;
    }
    return true;
  }

  /**
   * Checks if the class type has any parameters which are not unbounded wildcards
   * and do not have substituted arguments.
   *
   * @return true if the class type has nontrivial non-substituted parameters, false otherwise
   */
  public boolean hasNonTrivialParameters() {
    final ClassResolveResult resolveResult = resolveGenerics();
    if (resolveResult.getElement() == null) return false;
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(resolveResult.getElement());
    if (!iterator.hasNext()) return false;
    while (iterator.hasNext()) {
      PsiTypeParameter parameter = iterator.next();
      PsiType type = resolveResult.getSubstitutor().substitute(parameter);
      if (type != null) {
        if (!(type instanceof PsiWildcardType) || ((PsiWildcardType)type).getBound() != null) {
          return true;
        }
      }
    }
    return false;
  }

  public int hashCode() {
    final String className = getClassName();
    if (className == null) return 0;
    return className.hashCode();
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    final ClassResolveResult resolveResult = resolveGenerics();
    final PsiClass aClass = resolveResult.getElement();
    if (aClass == null) return EMPTY_ARRAY;
    final PsiClassType[] superTypes = aClass.getSuperTypes();

    final PsiType[] subtitutionResults = new PsiType[superTypes.length];
    for (int i = 0; i < superTypes.length; i++) {
      subtitutionResults[i] = resolveResult.getSubstitutor().substitute(superTypes[i]);
    }
    return subtitutionResults;
  }

  /**
   * Checks whether the specified resolve result representss a raw type. <br>
   * Raw type is a class type for a class <i>with type parameters</i> which does not assign
   * any value to them. If a class does not have any type parameters, it cannot generate any raw type.
   */
  public static boolean isRaw(ClassResolveResult resolveResult) {
    if (resolveResult.getElement() == null) return false;
    return PsiUtil.isRawSubstitutor(resolveResult.getElement(), resolveResult.getSubstitutor());
  }

  /**
   * Checks whether this type is a raw type. <br>
   * Raw type is a class type for a class <i>with type parameters</i> which does not assign
   * any value to them. If a class does not have any type parameters, it cannot generate any raw type.
   */
  public boolean isRaw() {
    return isRaw(resolveGenerics());
  }

  /**
   * Returns the resolve result containing the class referenced by the class type and the
   * substitutor which can substitute the values of type arguments used in the class type
   * declaration.
   *
   * @return the resolve result instance.
   */
  @NotNull public abstract ClassResolveResult resolveGenerics();

  /**
   * Returns the raw type (with no values assigned to type parameters) corresponding to this type.
   *
   * @return the raw type instance.
   */
  @NotNull public abstract PsiClassType rawType();

  /**
   * Overrides {@link com.intellij.psi.PsiType#getResolveScope()} to narrow specify @NotNull.
   */
  @NotNull
  public abstract GlobalSearchScope getResolveScope();


  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitClassType(this);
  }

  @NotNull
  public abstract LanguageLevel getLanguageLevel();

  /**
   * Functional style setter preserving original type's language level
   * @param languageLevel level to obtain class type with
   * @return type with requested language level
   */
  public abstract PsiClassType setLanguageLevel(final LanguageLevel languageLevel);

  /**
   * Represents the result of resolving a reference to a Java class.
   */
  public interface ClassResolveResult extends JavaResolveResult {
    PsiClass getElement();

    ClassResolveResult EMPTY = new ClassResolveResult() {
      public PsiClass getElement() {
        return null;
      }

      public PsiSubstitutor getSubstitutor() {
        return PsiSubstitutor.EMPTY;
      }

      public boolean isValidResult(){
        return false;
      }

      public boolean isAccessible(){
        return false;
      }

      public boolean isStaticsScopeCorrect(){
        return false;
      }

      public PsiElement getCurrentFileResolveScope() {
        return null;
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }
    };
  }
}
