// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Service for creating instances of Java and JavaDoc PSI elements which don't have
 * an underlying source code file.
 *
 * @see JavaPsiFacade#getElementFactory()
 * @see PsiFileFactory
 */
@NonNls
public interface PsiElementFactory extends PsiJavaParserFacade, JVMElementFactory {

  /**
   * @deprecated please use {@link #getInstance(Project)}
   */
  @Deprecated
  final
  class SERVICE {
    private SERVICE() { }

    public static PsiElementFactory getInstance(Project project) {
      return PsiElementFactory.getInstance(project);
    }
  }

  static PsiElementFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiElementFactory.class);
  }

  /**
   * Creates an empty class with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @Override
  @NotNull
  PsiClass createClass(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @Override
  @NotNull
  PsiClass createInterface(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @Override
  @NotNull
  PsiClass createEnum(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a record with no components with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @NotNull
  PsiClass createRecord(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty annotation type with the specified name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid Java identifier.
   */
  @Override
  @NotNull
  PsiClass createAnnotationType(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier
   *                                     or {@code type} represents an invalid type.
   */
  @Override
  @NotNull
  PsiField createField(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier
   *                                     or {@code type} represents an invalid type.
   */
  @Override
  @NotNull
  PsiMethod createMethod(@NotNull String name, PsiType returnType) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   */
  @Override
  @NotNull
  PsiMethod createConstructor();

  /**
   * Creates an empty constructor with a given name.
   */
  @Override
  @NotNull
  PsiMethod createConstructor(@NotNull String name);

  /**
   * Creates an empty class initializer block.
   *
   * @throws IncorrectOperationException in case of an internal error.
   */
  @Override
  @NotNull
  PsiClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * Creates a parameter with the specified name and type.
   *
   * @throws IncorrectOperationException {@code name} is not a valid Java identifier
   *                                     or {@code type} represents an invalid type.
   */
  @Override
  @NotNull
  PsiParameter createParameter(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty Java code block.
   */
  @NotNull
  PsiCodeBlock createCodeBlock();

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   */
  @Override
  @NotNull
  PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param languageLevel to memorize language level for allowing/prohibiting boxing/unboxing.
   */
  @Override
  @NotNull
  PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel);

  /**
   * Creates a class type for the specified reference pointing to a class.
   */
  @NotNull
  PsiClassType createType(@NotNull PsiJavaCodeReferenceElement classReference);

  @Override
  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass, PsiType parameters);

  @Override
  @NotNull
  PsiClassType createType(@NotNull PsiClass aClass, PsiType... parameters);

  /**
   * Creates a substitutor for the specified class which replaces all type parameters
   * with their corresponding raw types.
   */
  @Override
  @NotNull
  PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner);

  /**
   * Creates a substitutor which uses the specified mapping between type parameters and types.
   */
  @Override
  @NotNull
  PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map);

  /**
   * Returns the primitive type instance for the specified type name.
   *
   * @param text the name of a Java primitive type (for example, {@code int})
   * @return the primitive type instance, or {@code null} if {@code name} is not a valid
   * primitive type name.
   */
  @Override
  @Nullable
  PsiPrimitiveType createPrimitiveType(@NotNull String text);

  /**
   * The same as {@link #createTypeByFQClassName(String, GlobalSearchScope)}
   * with {@link GlobalSearchScope#allScope(Project)}.
   */
  @Override
  @NotNull
  PsiClassType createTypeByFQClassName(@NotNull String qName);

  /**
   * Creates a class type referencing a class with the specified class name in the specified
   * search scope.
   */
  @Override
  @NotNull
  PsiClassType createTypeByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates a type element referencing the specified type.
   */
  @NotNull
  PsiTypeElement createTypeElement(@NotNull PsiType psiType);

  /**
   * Creates a reference element resolving to the specified class type.
   *
   * @param type the class type to create the reference to.
   * @return the reference element instance.
   */
  @Override
  @NotNull
  PsiJavaCodeReferenceElement createReferenceElementByType(@NotNull PsiClassType type);

  /**
   * Creates a reference element resolving to the specified class.
   */
  @NotNull
  PsiJavaCodeReferenceElement createClassReferenceElement(@NotNull PsiClass aClass);

  /**
   * Creates a reference element resolving to the class with the specified name
   * in the specified search scope. The text of the created reference is the short name of the class.
   */
  @NotNull
  PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates a reference element resolving to the class with the specified name
   * in the specified search scope. The text of the created reference is the fully qualified name of the class.
   */
  @NotNull
  PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@NotNull String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates a reference element resolving to the specified package.
   *
   * @throws IncorrectOperationException if {@code aPackage} is the default (root) package.
   */
  @NotNull
  PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull PsiPackage aPackage) throws IncorrectOperationException;

  /**
   * Creates a reference element resolving to the package with the specified name.
   *
   * @throws IncorrectOperationException if {@code packageName} is an empty string.
   */
  @NotNull
  PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull String packageName) throws IncorrectOperationException;

  /**
   * Creates a reference expression resolving to the specified class.
   *
   * @throws IncorrectOperationException never (the exception is kept for compatibility purposes).
   */
  @NotNull
  PsiReferenceExpression createReferenceExpression(@NotNull PsiClass aClass) throws IncorrectOperationException;

  /**
   * Creates a reference expression resolving to the specified package.
   *
   * @throws IncorrectOperationException if {@code aPackage} is the default (root) package.
   */
  @NotNull
  PsiReferenceExpression createReferenceExpression(@NotNull PsiPackage aPackage) throws IncorrectOperationException;

  /**
   * Creates a Java identifier with the specified text.
   *
   * @throws IncorrectOperationException if {@code text} is not a valid Java identifier.
   */
  @NotNull
  PsiIdentifier createIdentifier(@NotNull String text) throws IncorrectOperationException;

  /**
   * Creates a Java keyword with the specified text.
   *
   * @throws IncorrectOperationException if {@code text} is not a valid Java keyword.
   */
  @NotNull
  PsiKeyword createKeyword(@NotNull String keyword) throws IncorrectOperationException;

  @NotNull
  PsiKeyword createKeyword(@NotNull String keyword, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an import statement for importing the specified class.
   *
   * @throws IncorrectOperationException if {@code aClass} is an anonymous or local class.
   */
  @NotNull
  PsiImportStatement createImportStatement(@NotNull PsiClass aClass) throws IncorrectOperationException;

  /**
   * Creates an on-demand import statement for importing classes from the package with the specified name.
   *
   * @throws IncorrectOperationException if {@code packageName} is not a valid qualified package name.
   */
  @NotNull
  PsiImportStatement createImportStatementOnDemand(@NotNull String packageName) throws IncorrectOperationException;

  /**
   * Creates a local variable declaration statement with the specified name, type and initializer,
   * optionally without reformatting the declaration.
   * <p>
   * Note that depending on code style settings the resulting variable may be declared as {@code final}.
   * </p>
   *
   * @throws IncorrectOperationException if {@code name} is not a valid identifier or
   *                                     {@code type} is not a valid type.
   */
  @NotNull
  PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String name,
                                                             @NotNull PsiType type,
                                                             @Nullable PsiExpression initializer)
    throws IncorrectOperationException;

  /**
   * Creates a local variable declaration statement with the specified name, type and initializer,
   * optionally without reformatting the declaration.
   * <p>
   * Note that depending on code style settings the resulting variable may be declared as {@code final}.
   * </p>
   *
   * @param context the context used to resolve symbols in the resulting declaration.
   * @throws IncorrectOperationException if {@code name} is not a valid identifier or
   *                                     {@code type} is not a valid type.
   */
  @NotNull
  PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String name, @NotNull PsiType type,
                                                             @Nullable PsiExpression initializer, @Nullable PsiElement context)
    throws IncorrectOperationException;

  /**
   * Creates a resource variable (which can be inserted into the resource list of try-with-resources statement)
   * with the specified name, type and initializer
   *
   * @param context the context for dummy holder
   */
  PsiResourceVariable createResourceVariable(@NotNull String name,
                                             @NotNull PsiType type,
                                             @Nullable PsiExpression initializer,
                                             @Nullable PsiElement context);

  /**
   * Creates a PSI element for the "&#64;param" JavaDoc tag.
   *
   * @throws IncorrectOperationException if the name or description are invalid.
   */
  @NotNull
  PsiDocTag createParamTag(@NotNull String parameterName, String description) throws IncorrectOperationException;

  /**
   * Returns a synthetic Java class containing methods which are defined on Java arrays.
   *
   * @param languageLevel language level used to construct array class.
   */
  @NotNull
  PsiClass getArrayClass(@NotNull LanguageLevel languageLevel);

  /**
   * Returns the class type for a synthetic Java class containing methods which
   * are defined on Java arrays with the specified element type.
   *
   * @param languageLevel language level used to construct array class.
   */
  @NotNull
  PsiClassType getArrayClassType(@NotNull PsiType componentType, @NotNull final LanguageLevel languageLevel);

  /**
   * Creates a package statement for the specified package name.
   *
   * @throws IncorrectOperationException if {@code name} is not a valid package name.
   */
  @NotNull
  PsiPackageStatement createPackageStatement(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an {@code import static} statement for importing the specified member
   * from the specified class.
   *
   * @throws IncorrectOperationException if the class is inner or local, or
   *                                     {@code memberName} is not a valid identifier.
   */
  @NotNull
  PsiImportStaticStatement createImportStaticStatement(@NotNull PsiClass aClass, @NotNull String memberName)
    throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @throws IncorrectOperationException if any of the parameter names or types are invalid.
   */
  @Override
  @NotNull
  PsiParameterList createParameterList(String @NotNull [] names, PsiType @NotNull [] types) throws IncorrectOperationException;

  /**
   * Creates a reference list element from the specified array of references.
   *
   * @throws IncorrectOperationException if some of the references are invalid.
   */
  @NotNull
  PsiReferenceList createReferenceList(PsiJavaCodeReferenceElement @NotNull [] references) throws IncorrectOperationException;

  @NotNull
  PsiSubstitutor createRawSubstitutor(@NotNull PsiSubstitutor baseSubstitutor, PsiTypeParameter @NotNull [] typeParameters);

  /**
   * Create a lightweight PsiElement of given element type in a lightweight non-physical PsiFile (aka DummyHolder) in a given context.
   * Element type's language should have a parser definition which supports parsing for this element type (first
   * parameter in {@link com.intellij.lang.PsiParser#parse(IElementType, com.intellij.lang.PsiBuilder)}.
   *
   * @param text    text to parse
   * @param type    node type
   * @param context context
   * @return PsiElement of the desired element type
   */
  @NotNull
  PsiElement createDummyHolder(@NotNull String text, @NotNull IElementType type, @Nullable PsiElement context);

  /**
   * Creates a {@code catch} section for catching an exception of the specified
   * type and name.
   *
   * @param exceptionType the type of the exception to catch (either {@linkplain PsiClassType} or {@linkplain PsiDisjunctionType}).
   * @param exceptionName the name of the variable in which the caught exception is stored (may be an empty string).
   * @param context       the context for resolving references.
   * @throws IncorrectOperationException if any of the parameters are not valid.
   */
  @NotNull
  PsiCatchSection createCatchSection(@NotNull PsiType exceptionType, @NotNull String exceptionName, @Nullable PsiElement context)
    throws IncorrectOperationException;

  @Override
  @NotNull
  PsiExpression createExpressionFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException;
}