// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCWatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.java.psi.GenericsFixture.genericsFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestApplication
public class TypesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> genericsFixture = genericsFixture();

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = genericsFixture.get();
    final String testPath = JavaTestUtil.getJavaTestDataPath().replace(File.separatorChar, '/') + "/psi/types/" + fixture.getTestName(true);
    final VirtualFile[] testRoot = { null };
    WriteAction.runAndWait(() -> {
      testRoot[0] = StandardFileSystems.local().refreshAndFindFileByPath(testPath);
    });
    if (testRoot[0] != null) {
      PsiTestUtil.addSourceRoot(fixture.getModule(), testRoot[0]);
    }
  }

  @Test
  public void testSimpleStuff() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] methodStatements = method.getBody().getStatements();
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) methodStatements[0];
      final PsiVariable varList = (PsiVariable) declarationStatement.getDeclaredElements()[0];
      final PsiType typeListOfA = factory.createTypeFromText("test.List<java.lang.String>", null);
      assertEquals(varList.getType(), typeListOfA);
      final PsiType typeListOfObject = factory.createTypeFromText("test.List<java.lang.Object>", null);
      assertNotEquals(varList.getType(), typeListOfObject);

      final PsiReferenceExpression methodExpression
              = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[1]).getExpression()).getMethodExpression();
      final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof PsiMethod);
      final PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
      final PsiType typeOfFirstParameterOfAdd = methodFromList.getParameterList().getParameters()[0].getType();
      final PsiType substitutedType = resolveResult.getSubstitutor().substitute(typeOfFirstParameterOfAdd);
      final PsiClassType typeA = factory.createTypeByFQClassName("java.lang.String");
      assertEquals(typeA, substitutedType);
      assertTrue(typeA.equalsToText("java.lang.String"));

      final PsiType aListIteratorType = ((PsiExpressionStatement) methodStatements[2]).getExpression().getType();
      final PsiType aIteratorType = factory.createTypeFromText("test.Iterator<java.lang.String>", null);
      assertEquals(aIteratorType, aListIteratorType);
      final PsiType objectIteratorType = factory.createTypeFromText("test.Iterator<java.lang.Object>", null);
      assertNotEquals(objectIteratorType, aListIteratorType);
    });
  }

  @Test
  public void testRawTypes() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] methodStatements = method.getBody().getStatements();
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) methodStatements[0];
      final PsiVariable varList = (PsiVariable) declarationStatement.getDeclaredElements()[0];
      final PsiType typeFromText = factory.createTypeFromText("test.List", null);
      assertEquals(typeFromText, varList.getType());

      final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[1]).getExpression()).getMethodExpression();
      final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof PsiMethod);
      final PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
      final PsiType typeOfFirstParameterOfAdd = methodFromList.getParameterList().getParameters()[0].getType();
      final PsiType substitutedType = resolveResult.getSubstitutor().substitute(typeOfFirstParameterOfAdd);
      assertEquals(PsiType.getJavaLangObject(genericsFixture.get().getPsiManager(), method.getResolveScope()), substitutedType);

      final PsiType methodCallType = ((PsiExpressionStatement) methodStatements[2]).getExpression().getType();
      final PsiType rawIteratorType = factory.createTypeFromText("test.Iterator", null);
      assertEquals(rawIteratorType, methodCallType);
    });
  }

  @Test
  public void testSubstWithInheritor() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] methodStatements = method.getBody().getStatements();
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) methodStatements[0];
      final PsiVariable varList = (PsiVariable) declarationStatement.getDeclaredElements()[0];
      final PsiType typeFromText = factory.createTypeFromText("test.IntList", null);
      assertEquals(typeFromText, varList.getType());

      final PsiReferenceExpression methodExpression
              = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[1]).getExpression()).getMethodExpression();
      final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof PsiMethod);
      final PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
      final PsiType typeOfFirstParameterOfAdd = methodFromList.getParameterList().getParameters()[0].getType();
      final PsiType substitutedType = resolveResult.getSubstitutor().substitute(typeOfFirstParameterOfAdd);
      final PsiType javaLangInteger = factory.createTypeFromText("java.lang.Integer", null);
      assertEquals(javaLangInteger, substitutedType);

      final PsiType intListIteratorReturnType = ((PsiExpressionStatement) methodStatements[2]).getExpression().getType();
      final PsiType integerIteratorType = factory.createTypeFromText("test.Iterator<java.lang.Integer>", null);
      assertEquals(integerIteratorType, intListIteratorReturnType);
      final PsiType objectIteratorType = factory.createTypeFromText("test.Iterator<java.lang.Object>", null);
      assertNotEquals(objectIteratorType, integerIteratorType);
    });
  }

  @Test
  public void testSimpleRawTypeInMethodArg() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] methodStatements = method.getBody().getStatements();

      final PsiVariable variable = (PsiVariable) ((PsiDeclarationStatement) methodStatements[0]).getDeclaredElements()[0];
      final PsiClassType type = (PsiClassType) variable.getType();
      final PsiClassType.ClassResolveResult resolveClassTypeResult = type.resolveGenerics();
      assertNotNull(resolveClassTypeResult.getElement());

      final PsiReferenceExpression methodExpression
              = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[2]).getExpression()).getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      final PsiClassType qualifierType = (PsiClassType) qualifierExpression.getType();
      assertFalse(qualifierType.hasParameters());
      final PsiType typeFromText = factory.createTypeFromText("test.List", null);
      assertEquals(typeFromText, qualifierType);

      final PsiElement psiElement = ((PsiReferenceExpression) qualifierExpression).resolve();
      assertTrue(psiElement instanceof PsiVariable);
      final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof PsiMethod);
      final PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
      assertEquals("add", methodFromList.getName());
      assertEquals("test.List", methodFromList.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testRawTypeInMethodArg() {
    ReadAction.runBlocking(() -> {
      final PsiClass classA = genericsFixture.get().getJavaFacade().findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] methodStatements = method.getBody().getStatements();
      final PsiReferenceExpression methodExpression
              = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[2]).getExpression()).getMethodExpression();
      final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof PsiMethod);
      final PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
      assertEquals("putAll", methodFromList.getName());
      assertEquals("test.List", methodFromList.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testBoundedParams() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] statements = method.getBody().getStatements();

      final PsiVariable var = (PsiVariable) ((PsiDeclarationStatement) statements[0]).getDeclaredElements()[0];
      final PsiType varType = var.getType();
      final PsiType typeRawIterator = factory.createTypeFromText("test.Iterator", null);
      assertEquals(typeRawIterator, varType);

      final PsiType initializerType = var.getInitializer().getType();
      assertEquals(typeRawIterator, initializerType);
      assertTrue(varType.isAssignableFrom(initializerType));
    });
  }

  @Test
  public void testRawTypeExtension() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.getMethods()[0];
      final PsiStatement[] statements = method.getBody().getStatements();

      final PsiVariable var = (PsiVariable) ((PsiDeclarationStatement) statements[0]).getDeclaredElements()[0];
      final PsiType varType = var.getType();
      final PsiType typeRawIterator = factory.createTypeFromText("test.Iterator", null);
      assertEquals(typeRawIterator, varType);

      final PsiType initializerType = var.getInitializer().getType();
      assertEquals(typeRawIterator, initializerType);
      assertTrue(varType.isAssignableFrom(initializerType));
    });
  }

  @Test
  public void testTypesInGenericClass() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiTypeParameter parameterT = classA.getTypeParameters()[0];
      assertEquals("T", parameterT.getName());

      final PsiMethod method = classA.findMethodsByName("method", false)[0];
      final PsiType type = ((PsiExpressionStatement) method.getBody().getStatements()[0]).getExpression().getType();
      final PsiClassType typeT = factory.createType(parameterT);
      assertEquals("T", typeT.getPresentableText());

      assertEquals(typeT, type);
    });
  }

  @Test
  public void testAssignableSubInheritor() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classCollection = psiManager.findClass("test.Collection");
      final PsiClass classList = psiManager.findClass("test.List");
      final PsiType collectionType = factory.createType(classCollection, PsiSubstitutor.EMPTY);
      final PsiType listType = factory.createType(classList, PsiSubstitutor.EMPTY);
      assertEquals("test.Collection<E>", collectionType.getCanonicalText());
      assertEquals("test.List<T>", listType.getCanonicalText());

      final PsiType typeListOfString = factory.createTypeFromText("test.List<java.lang.String>", null);
      final PsiType typeCollectionOfString = factory.createTypeFromText("test.Collection<java.lang.String>", null);
      assertTrue(typeCollectionOfString.isAssignableFrom(typeListOfString));
    });
  }

  @Test
  public void testComplexInheritance() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.findMethodsByName("method", false)[0];
      final PsiExpression expression = ((PsiExpressionStatement) method.getBody().getStatements()[1]).getExpression();
      assertEquals("l.get(0)", expression.getText());

      final PsiType type = expression.getType();
      final PsiType listOfInteger = factory.createTypeFromText("test.List<java.lang.Integer>", null);
      assertEquals(listOfInteger, type);
      final PsiType collectionOfInteger = factory.createTypeFromText("test.Collection<java.lang.Integer>", null);
      assertTrue(collectionOfInteger.isAssignableFrom(type));
    });
  }

  @Test
  public void testListListInheritance() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.findMethodsByName("method", false)[0];

      final PsiExpression expression1 = ((PsiExpressionStatement) method.getBody().getStatements()[1]).getExpression();
      assertEquals("l.get(0)", expression1.getText());
      final PsiType type1 = expression1.getType();
      final PsiType typeListOfInteger = factory.createTypeFromText("test.List<java.lang.Integer>", null);
      assertEquals(typeListOfInteger, type1);
      assertTrue(typeListOfInteger.isAssignableFrom(type1));

      final PsiExpression expression2 = ((PsiExpressionStatement) method.getBody().getStatements()[3]).getExpression();
      assertEquals("b.get(0)", expression2.getText());
      final PsiType type2 = expression2.getType();
      assertEquals(typeListOfInteger, type2);
    });
  }

  @Test
  public void testSpaceInTypeParameterList() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.findMethodsByName("method", false)[0];

      final PsiVariable variable = (PsiVariable) ((PsiDeclarationStatement) method.getBody().getStatements()[0]).getDeclaredElements()[0];
      final PsiType type = variable.getType();
      final PsiType typeListOfListOfInteger = factory.createTypeFromText("test.List<test.List<java.lang.Integer>>", null);
      assertEquals(typeListOfListOfInteger, type);
    });
  }

  @Test
  public void testMethodTypeParameter() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.findMethodsByName("method", false)[0];
      final PsiStatement[] statements = method.getBody().getStatements();

      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[1]).getExpression();
      isCollectionUtilSort(methodCallExpression, factory.createTypeFromText("java.lang.Integer", null));

      final PsiMethodCallExpression methodCallExpression1 = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[3]).getExpression();
      isCollectionUtilSort(methodCallExpression1, null);
    });
  }

  private static void isCollectionUtilSort(final PsiMethodCallExpression methodCallExpression,
                                    final PsiType typeParameterValue) {
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final JavaResolveResult methodResolve = methodExpression.advancedResolve(false);
    assertTrue(methodResolve.getElement() instanceof PsiMethod);
    final PsiMethod methodSort = (PsiMethod) methodResolve.getElement();
    assertEquals("sort", methodSort.getName());
    assertEquals("test.CollectionUtil", methodSort.getContainingClass().getQualifiedName());
    final PsiTypeParameter methodSortTypeParameter = methodSort.getTypeParameters()[0];
    final PsiType sortParameterActualType = methodResolve.getSubstitutor().substitute(methodSortTypeParameter);
    assertTrue(Comparing.equal(sortParameterActualType, typeParameterValue));
    assertTrue(
            PsiUtil.isApplicable(methodSort, methodResolve.getSubstitutor(), methodCallExpression.getArgumentList()));
  }

  @Test
  public void testRawArrayTypes() {
    ReadAction.runBlocking(() -> {
      final JavaPsiFacadeEx psiManager = genericsFixture.get().getJavaFacade();
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiClass classA = psiManager.findClass("A");
      assertNotNull(classA);

      final PsiMethod method = classA.findMethodsByName("method", false)[0];
      final PsiStatement[] statements = method.getBody().getStatements();

      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) statements[0];
      final PsiClassType typeOfL = (PsiClassType) ((PsiVariable) declarationStatement.getDeclaredElements()[0]).getType();
      final PsiType typeRawList = factory.createTypeFromText("test.List", null);
      assertTrue(Comparing.equal(typeOfL, typeRawList));
      final PsiSubstitutor typeOfLSubstitutor = typeOfL.resolveGenerics().getSubstitutor();

      final PsiMethodCallExpression exprGetArray = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[1]).getExpression();
      final PsiType typeOfGetArrayCall = exprGetArray.getType();
      final PsiType objectArrayType = factory.createTypeFromText("java.lang.Object[]", null);
      assertTrue(Comparing.equal(typeOfGetArrayCall, objectArrayType));
      final PsiMethod methodGetArray = (PsiMethod) exprGetArray.getMethodExpression().resolve();
      final PsiType subtitutedGetArrayReturnType = typeOfLSubstitutor.substitute(methodGetArray.getReturnType());
      assertTrue(Comparing.equal(subtitutedGetArrayReturnType, objectArrayType));


      final PsiMethodCallExpression exprGetListOfArray = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[2]).getExpression();
      final PsiMethod methodGetListOfArray = (PsiMethod) exprGetListOfArray.getMethodExpression().resolve();
      final PsiType returnType = methodGetListOfArray.getReturnType();
      final PsiType substitutedReturnType = typeOfLSubstitutor.substitute(returnType);
      assertTrue(Comparing.equal(substitutedReturnType, typeRawList));

      final PsiType typeOfGetListOfArrayCall = exprGetListOfArray.getType();
      assertTrue(Comparing.equal(typeOfGetListOfArrayCall, typeRawList));
    });
  }

  @Test
  public void testWildcardTypeParsing() {
    ReadAction.runBlocking(() -> {
      final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(genericsFixture.get().getModule());
      final PsiClassType javaLangObject = PsiType.getJavaLangObject(genericsFixture.get().getPsiManager(), scope);

      PsiElement element = ((PsiDeclarationStatement)genericsFixture.get().getJavaFacade().getElementFactory().createStatementFromText("X<? extends Y, ? super Z<A,B>, ?> x;", null)).getDeclaredElements()[0];
      PsiJavaCodeReferenceElement referenceElement = ((PsiVariable) element).getTypeElement().getInnermostComponentReferenceElement();
      PsiType[] typeArguments = referenceElement.getTypeParameters();
      assertEquals(3, typeArguments.length);
      assertTrue(typeArguments[0] instanceof PsiWildcardType);
      assertTrue(typeArguments[1] instanceof PsiWildcardType);
      assertTrue(typeArguments[2] instanceof PsiWildcardType);
      PsiWildcardType extendsWildcard = (PsiWildcardType)typeArguments[0];
      PsiWildcardType superWildcard = (PsiWildcardType)typeArguments[1];
      PsiWildcardType unboundedWildcard = (PsiWildcardType)typeArguments[2];

      // extends wildcard test
      assertTrue(extendsWildcard.isExtends());
      assertFalse(extendsWildcard.isSuper());
      assertEquals("Y", extendsWildcard.getBound().getCanonicalText());
      assertEquals("Y", extendsWildcard.getExtendsBound().getCanonicalText());
      assertEquals(PsiTypes.nullType(), extendsWildcard.getSuperBound());

      // super wildcard test
      assertFalse(superWildcard.isExtends());
      assertTrue(superWildcard.isSuper());
      assertEquals("Z<A,B>", superWildcard.getBound().getCanonicalText());
      assertEquals(javaLangObject, superWildcard.getExtendsBound());
      assertEquals("Z<A,B>", superWildcard.getSuperBound().getCanonicalText());

      // unbounded wildcard test
      assertFalse(unboundedWildcard.isExtends());
      assertFalse(unboundedWildcard.isSuper());
      assertNull(unboundedWildcard.getBound());
      assertEquals(javaLangObject, unboundedWildcard.getExtendsBound());
      assertEquals(PsiTypes.nullType(), unboundedWildcard.getSuperBound());
    });
  }

  @Test
  public void testWildcardTypesAssignable() {
    ReadAction.runBlocking(() -> {
      PsiClassType listOfExtendsBase = (PsiClassType)genericsFixture.get().getJavaFacade().getElementFactory().createTypeFromText("test.List<? extends usages.Base>", null);
      PsiClassType.ClassResolveResult classResolveResult = listOfExtendsBase.resolveGenerics();
      PsiClass listClass = classResolveResult.getElement();
      assertNotNull(listClass);
      PsiTypeParameter listTypeParameter = PsiUtil.typeParametersIterator(listClass).next();
      PsiType listParameterTypeValue = classResolveResult.getSubstitutor().substitute(listTypeParameter);
      assertTrue(listParameterTypeValue instanceof PsiWildcardType);
      assertTrue(((PsiWildcardType)listParameterTypeValue).isExtends());
      assertEquals("usages.Base", ((PsiWildcardType)listParameterTypeValue).getBound().getCanonicalText());
      PsiClassType listOfIntermediate = (PsiClassType)genericsFixture.get().getJavaFacade().getElementFactory().createTypeFromText("test.List<usages.Intermediate>", null);
      assertNotNull(listOfIntermediate.resolve());
      assertTrue(listOfExtendsBase.isAssignableFrom(listOfIntermediate));
    });
  }

  @Test
  public void testEllipsisType() {
    ReadAction.runBlocking(() -> {
      PsiElementFactory factory = genericsFixture.get().getJavaFacade().getElementFactory();
      PsiMethod method = factory.createMethodFromText("void foo (int ... args) {}", null);
      PsiType paramType = method.getParameterList().getParameters()[0].getType();
      assertTrue(paramType instanceof PsiEllipsisType);
      PsiType arrayType = ((PsiEllipsisType)paramType).getComponentType().createArrayType();
      assertTrue(paramType.isAssignableFrom(arrayType));
      assertTrue(arrayType.isAssignableFrom(paramType));

      PsiType typeFromText = factory.createTypeFromText("int ...", null);
      assertTrue(typeFromText instanceof PsiEllipsisType);
    });
  }

  @Test
  public void testBinaryNumericPromotion() {
    ReadAction.runBlocking(() -> {
      PsiElementFactory factory = genericsFixture.get().getJavaFacade().getElementFactory();
      final PsiExpression conditional = factory.createExpressionFromText("b ? new Integer (0) : new Double(0.0)", null);
      assertEquals(PsiTypes.doubleType(), conditional.getType());
      final PsiExpression shift = factory.createExpressionFromText("Integer.valueOf(0) << 2", null);
      assertEquals(PsiTypes.intType(), shift.getType());
    });
  }

  @Test
  public void testUnaryExpressionType() {
    ReadAction.runBlocking(() -> {
      final PsiElementFactory factory = genericsFixture.get().getJavaFacade().getElementFactory();
      final PsiExpression plusPrefix = factory.createExpressionFromText("+Integer.valueOf(1)", null);
      assertEquals(PsiTypes.intType(), plusPrefix.getType());
      final PsiExpression plusBytePrefix = factory.createExpressionFromText("+Byte.valueOf(1)", null);
      assertEquals(PsiTypes.intType(), plusBytePrefix.getType());
      final PsiStatement declaration = factory.createStatementFromText("Byte b = 1;", null);
      final PsiExpression plusPlusPostfix = factory.createExpressionFromText("b++", declaration);
      assertEquals(PsiTypes.byteType().getBoxedType(declaration), plusPlusPostfix.getType());
    });
  }

  @Test
  public void testVariableTypeInvalidation() {
    PsiElementFactory factory = genericsFixture.get().getJavaFacade().getElementFactory();
    Ref<PsiTypeElement> ref = Ref.create();
    PsiType type = ReadAction.computeBlocking(() -> {
      PsiStatement statement = factory.createStatementFromText("String s;", null);
      PsiLocalVariable var = (PsiLocalVariable)((PsiDeclarationStatement)statement).getDeclaredElements()[0];
      PsiType varType = var.getType();
      assertTrue(varType.isValid());

      ref.set(var.getTypeElement());
      ref.get().replace(factory.createTypeElement(PsiTypes.intType()));

      assertFalse(varType.isValid());
      return varType;
    });

    GCWatcher.fromClearedRef(ref).ensureCollected();

    assertFalse(ReadAction.computeBlocking(() -> type.isValid())); // shouldn't throw
  }
}
