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
package com.intellij.psi.util;

import com.intellij.lang.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import static com.intellij.psi.infos.MethodCandidateInfo.ApplicabilityLevel.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class PsiUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiUtil");
  public static final int ACCESS_LEVEL_PUBLIC = 4;
  public static final int ACCESS_LEVEL_PROTECTED = 3;
  public static final int ACCESS_LEVEL_PACKAGE_LOCAL = 2;
  public static final int ACCESS_LEVEL_PRIVATE = 1;
  public static final Key<Boolean> VALID_VOID_TYPE_IN_CODE_FRAGMENT = Key.create("VALID_VOID_TYPE_IN_CODE_FRAGMENT");
  public static final Key<PsiElement> ORIGINAL_KEY = Key.create("ORIGINAL_KEY");
  public static final PsiParser NULL_PARSER = new PsiParser() {
    @NotNull
    public ASTNode parse(IElementType root, PsiBuilder builder) {
      return null;
    }
  };
  public static final PsiElement NULL_PSI_ELEMENT = new PsiElement() {
      public Project getProject() {
        throw new PsiInvalidElementAccessException(this);
      }

      @NotNull
      public Language getLanguage() {
        return null;
      }

      public PsiManager getManager() {
        return null;
      }

      @NotNull
      public PsiElement[] getChildren() {
        return new PsiElement[0];
      }

      public PsiElement getParent() {
        return null;
      }

      @Nullable
      public PsiElement getFirstChild() {
        return null;
      }

      @Nullable
      public PsiElement getLastChild() {
        return null;
      }

      @Nullable
      public PsiElement getNextSibling() {
        return null;
      }

      @Nullable
      public PsiElement getPrevSibling() {
        return null;
      }

      public PsiFile getContainingFile() {
        throw new PsiInvalidElementAccessException(this);
      }

      public TextRange getTextRange() {
        return null;
      }

      public int getStartOffsetInParent() {
        return 0;
      }

      public int getTextLength() {
        return 0;
      }

      public PsiElement findElementAt(int offset) {
        return null;
      }

      @Nullable
      public PsiReference findReferenceAt(int offset) {
        return null;
      }

      public int getTextOffset() {
        return 0;
      }

      public String getText() {
        return null;
      }

      @NotNull
      public char[] textToCharArray() {
        return new char[0];
      }

      public PsiElement getNavigationElement() {
        return null;
      }

      public PsiElement getOriginalElement() {
        return null;
      }

      public boolean textMatches(@NotNull CharSequence text) {
        return false;
      }

      public boolean textMatches(@NotNull PsiElement element) {
        return false;
      }

      public boolean textContains(char c) {
        return false;
      }

      public void accept(@NotNull PsiElementVisitor visitor) {

      }

      public void acceptChildren(@NotNull PsiElementVisitor visitor) {

      }

      public PsiElement copy() {
        return null;
      }

      public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
        return null;
      }

      public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
        return null;
      }

      public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
        return null;
      }

      public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {

      }

      public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
        return null;
      }

      public PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
        return null;
      }

      public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
        return null;
      }

      public void delete() throws IncorrectOperationException {

      }

      public void checkDelete() throws IncorrectOperationException {

      }

      public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {

      }

      public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
        return null;
      }

      public boolean isValid() {
        return false;
      }

      public boolean isWritable() {
        return false;
      }

      @Nullable
      public PsiReference getReference() {
        return null;
      }

      @NotNull
      public PsiReference[] getReferences() {
        return new PsiReference[0];
      }

      public <T> T getCopyableUserData(Key<T> key) {
        return null;
      }

      public <T> void putCopyableUserData(Key<T> key, T value) {

      }

      public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
        return false;
      }

      public PsiElement getContext() {
        return null;
      }

      public boolean isPhysical() {
        return false;
      }

      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @NotNull
      public SearchScope getUseScope() {
        return null;
      }

      public ASTNode getNode() {
        return null;
      }

      public <T> T getUserData(Key<T> key) {
        return null;
      }

      public <T> void putUserData(Key<T> key, T value) {

      }

      public Icon getIcon(int flags) {
        return null;
      }


    public boolean isGenerated() {
      return true;
    }
  };

  private PsiUtil() {}

  public static boolean isOnAssignmentLeftHand(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    return parent instanceof PsiAssignmentExpression
           && expr.equals(((PsiAssignmentExpression) parent).getLExpression());
  }

  public static boolean isAccessibleFromPackage(PsiModifierListOwner element, PsiPackage aPackage) {
    if (element.hasModifierProperty(PsiModifier.PUBLIC)) return true;
    if (element.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return element.getManager().isInPackage(element, aPackage);
  }

  public static boolean isAccessedForWriting(PsiExpression expr) {
    if (isOnAssignmentLeftHand(expr)) return true;
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiPrefixExpression) {
      PsiJavaToken sign = ((PsiPrefixExpression) parent).getOperationSign();
      IElementType tokenType = sign.getTokenType();
      return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
    }
    else if (parent instanceof PsiPostfixExpression) {
      PsiJavaToken sign = ((PsiPostfixExpression) parent).getOperationSign();
      IElementType tokenType = sign.getTokenType();
      return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
    }
    else {
      return false;
    }
  }

  public static boolean isAccessedForReading(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression) parent).getLExpression())) {
      return ((PsiAssignmentExpression)parent).getOperationSign().getTokenType() != JavaTokenType.EQ;
    }
    else {
      return true;
    }
  }

  public static boolean isAccessible(PsiMember member, PsiElement place, PsiClass accessObjectClass) {
    return place.getManager().getResolveHelper().isAccessible(member, place, accessObjectClass);
  }

  public static JavaResolveResult getAccessObjectClass(PsiExpression accessObject) {
    if (accessObject instanceof PsiSuperExpression) {
      final PsiJavaCodeReferenceElement qualifier = ((PsiSuperExpression) accessObject).getQualifier();
      if (qualifier != null) { // E.super.f
        final JavaResolveResult result = qualifier.advancedResolve(false);
        final PsiElement resolve = result.getElement();
        if (resolve instanceof PsiClass) {
          final PsiClass psiClass;
          final PsiSubstitutor substitutor;
          if (resolve instanceof PsiTypeParameter) {
            final PsiClassType parameterType = resolve.getManager().getElementFactory().createType((PsiTypeParameter) resolve);
            final PsiType superType = result.getSubstitutor().substitute(parameterType);
            if (superType instanceof PsiArrayType) {
              LanguageLevel languageLevel = PsiUtil.getLanguageLevel(accessObject);
              return resolve.getManager().getElementFactory().getArrayClassType(((PsiArrayType)superType).getComponentType(), languageLevel).resolveGenerics();
            }
            else if (superType instanceof PsiClassType) {
              final PsiClassType type = (PsiClassType)superType;
              substitutor = type.resolveGenerics().getSubstitutor();
              psiClass = type.resolve();
            }
            else {
              psiClass = null;
              substitutor = PsiSubstitutor.EMPTY;
            }
          }
          else {
            psiClass = (PsiClass) resolve;
            substitutor = PsiSubstitutor.EMPTY;
          }
          if (psiClass != null) {
            return new CandidateInfo(psiClass, substitutor);
          }
          else
            return JavaResolveResult.EMPTY;
        }
        return JavaResolveResult.EMPTY;
      }
      else {
        PsiElement scope = accessObject.getContext();
        PsiElement lastParent = accessObject;
        while (scope != null) {
          if (scope instanceof PsiClass) {
            if (scope instanceof PsiAnonymousClass) {
              if (lastParent instanceof PsiExpressionList) {
                lastParent = scope;
                scope = scope.getContext();
                continue;
              }
            }
            return new CandidateInfo(scope, PsiSubstitutor.EMPTY);
          }
          lastParent = scope;
          scope = scope.getContext();
        }
        return JavaResolveResult.EMPTY;
      }
    }
    else if (accessObject instanceof PsiMethodCallExpression) {
      return JavaResolveResult.EMPTY;
    }
    else {
      PsiType type = accessObject.getType();
      if (!(type instanceof PsiClassType)) return JavaResolveResult.EMPTY;
      return ((PsiClassType)type).resolveGenerics();
    }
  }

  public static boolean isConstantExpression(PsiExpression expression) {
    if (expression == null) return false;
    IsConstantExpressionVisitor visitor = new IsConstantExpressionVisitor();
    expression.accept(visitor);
    return visitor.myIsConstant;
  }

  // todo: move to PsiThrowsList?
  public static void addException(PsiMethod method, @NonNls String exceptionFQName) throws IncorrectOperationException {
    PsiClass exceptionClass = method.getManager().findClass(exceptionFQName, method.getResolveScope());
    addException(method, exceptionClass, exceptionFQName);
  }

  public static void addException(PsiMethod method, PsiClass exceptionClass) throws IncorrectOperationException {
    addException(method, exceptionClass, exceptionClass.getQualifiedName());
  }

  private static void addException(PsiMethod method, PsiClass exceptionClass, String exceptionName) throws IncorrectOperationException {
    PsiReferenceList throwsList = method.getThrowsList();
    PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(exceptionClass)) return;
      PsiClass aClass = (PsiClass)ref.resolve();
      if (exceptionClass != null && aClass != null) {
        if (aClass.isInheritor(exceptionClass, true)) {
          PsiElementFactory factory = method.getManager().getElementFactory();
          PsiJavaCodeReferenceElement ref1;
          if (exceptionName != null) {
            ref1 = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
          }
          else {
            PsiClassType type = factory.createType(exceptionClass);
            ref1 = factory.createReferenceElementByType(type);
          }
          ref.replace(ref1);
          return;
        }
        else if (exceptionClass.isInheritor(aClass, true)) {
          return;
        }
      }
    }

    PsiElementFactory factory = method.getManager().getElementFactory();
    PsiJavaCodeReferenceElement ref;
    if (exceptionName != null) {
      ref = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
    } else {
      PsiClassType type = factory.createType(exceptionClass);
      ref = factory.createReferenceElementByType(type);
    }
    throwsList.add(ref);
  }

  // todo: move to PsiThrowsList?
  public static void removeException(PsiMethod method, @NonNls String exceptionClass) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = method.getThrowsList().getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.getCanonicalText().equals(exceptionClass)) {
        ref.delete();
      }
    }
  }

  public static boolean isVariableNameUnique(String name, PsiElement place) {
    PsiResolveHelper helper = place.getManager().getResolveHelper();
    return helper.resolveReferencedVariable(name, place) == null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void updatePackageStatement(PsiFile file) throws IncorrectOperationException {
    if (!(file instanceof PsiJavaFile) || isInJspFile(file)) return;

    PsiManager manager = file.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return;
    PsiPackage aPackage = dir.getPackage();
    if (aPackage == null) return;
    String packageName = aPackage.getQualifiedName();
    PsiPackageStatement statement = ((PsiJavaFile) file).getPackageStatement();
    if (statement != null) {
      if (packageName.length() > 0) {
        statement.getPackageReference().bindToElement(aPackage);
      }
      else {
        statement.delete();
      }
    }
    else {
      if (packageName.length() > 0) {
        String text = "package " + packageName + ";";
        String ext = StdFileTypes.JAVA.getDefaultExtension();
        PsiJavaFile dummyFile = (PsiJavaFile) factory.createFileFromText("_Dummy_." + ext, text);
        statement = dummyFile.getPackageStatement();
        if (statement == null) {
          throw new IncorrectOperationException();
        }
        file.add(statement);
      }
    }
  }

  /**
   * @return enclosing outermost (method or class initializer) body but not higher than scope
   */
  public static PsiElement getTopLevelEnclosingCodeBlock(PsiElement element, PsiElement scope) {
    PsiElement blockSoFar = null;
    while (element != null) {
      // variable can be defined in for loop initializer
      if (element instanceof PsiCodeBlock || element instanceof PsiForStatement || element instanceof PsiForeachStatement) {
        blockSoFar = element;
      }
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethod
          && parent.getParent() instanceof PsiClass
          && !isLocalOrAnonymousClass((PsiClass)parent.getParent()))
        break;
      if (parent instanceof PsiClassInitializer && !(parent.getParent() instanceof PsiAnonymousClass)) break;
      if (parent instanceof PsiField && ((PsiField) parent).getInitializer() == element) {
        blockSoFar = element;
      }
      if (element instanceof PsiClass && !isLocalOrAnonymousClass((PsiClass)element)) {
        break;
      }
      if (PsiUtil.isInJspFile(element) && element instanceof PsiFile) {
        return element;
      }
      if (element == scope) break;
      element = parent;
    }
    return blockSoFar;
  }

  public static boolean isInJspFile(final PsiElement element) {
    if(element == null) return false;
    final PsiFile psiFile = element.getContainingFile();
    if(psiFile == null) return false;
    final Language language = psiFile.getViewProvider().getBaseLanguage();
    return language == StdLanguages.JSP || language == StdLanguages.JSPX;
  }

  public static boolean isLocalOrAnonymousClass(PsiClass psiClass) {
    return psiClass instanceof PsiAnonymousClass || isLocalClass(psiClass);
  }

  public static boolean isLocalClass(PsiClass psiClass) {
    PsiElement parent = psiClass.getParent();
    return parent instanceof PsiDeclarationStatement && parent.getParent() instanceof PsiCodeBlock;
  }

  /**
   * @return codeblock topmost codeblock where variable makes sense
   */
  @Nullable
  public static PsiElement getVariableCodeBlock(PsiVariable variable, PsiElement context) {
    PsiElement codeBlock = null;
    if (variable instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)variable).getDeclarationScope();
      if (declarationScope instanceof PsiCatchSection) {
        codeBlock = ((PsiCatchSection)declarationScope).getCatchBlock();
      }
      else if (declarationScope instanceof PsiForeachStatement) {
        codeBlock = ((PsiForeachStatement)declarationScope).getBody();
      }
      else if (declarationScope instanceof PsiMethod) {
        codeBlock = ((PsiMethod)declarationScope).getBody();
      }
    }
    else if (variable instanceof PsiLocalVariable && variable.getParent() instanceof PsiForStatement) {
      return variable.getParent();
    }
    else if (variable instanceof PsiField && context != null) {
      final PsiClass aClass = ((PsiField) variable).getContainingClass();
      while (context != null && context.getParent() != aClass) {
        context = context.getParent();
      }
      return context instanceof PsiMethod ?
             ((PsiMethod) context).getBody() :
             context instanceof PsiClassInitializer ? ((PsiClassInitializer) context).getBody() : null;
    }
    else {
      final PsiElement scope = variable.getParent() == null ? null : variable.getParent().getParent();
      codeBlock = getTopLevelEnclosingCodeBlock(variable, scope);
      if (codeBlock != null && codeBlock.getParent() instanceof PsiSwitchStatement) codeBlock = codeBlock.getParent().getParent();
    }
    return codeBlock;
  }

  public static boolean isIncrementDecrementOperation(PsiElement element) {
    if (element instanceof PsiPostfixExpression) {
      final IElementType sign = ((PsiPostfixExpression) element).getOperationSign().getTokenType();
      if (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)
        return true;
    }
    else if (element instanceof PsiPrefixExpression) {
      final IElementType sign = ((PsiPrefixExpression) element).getOperationSign().getTokenType();
      if (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)
        return true;
    }
    return false;
  }

  public static int getAccessLevel(PsiModifierList modifierList) {
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return ACCESS_LEVEL_PRIVATE;
    }
    else if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return ACCESS_LEVEL_PACKAGE_LOCAL;
    }
    else if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      return ACCESS_LEVEL_PROTECTED;
    }
    else {
      return ACCESS_LEVEL_PUBLIC;
    }
  }

  @Nullable
  public static String getAccessModifier(int accessLevel) {
    return accessLevel > accessModifiers.length ? null : accessModifiers[accessLevel - 1];
  }

  private static final String[] accessModifiers = new String[]{
    PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC
  };

  @Nullable
  public static PsiFile findRelativeFile(String uri, PsiElement base) {
    if (base instanceof PsiFile) {
      PsiFile baseFile = (PsiFile) base;
      if (baseFile.getOriginalFile() != null) return findRelativeFile(uri, baseFile.getOriginalFile());
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseFile.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findFile(file);
    }
    else if (base instanceof PsiDirectory) {
      PsiDirectory baseDir = (PsiDirectory) base;
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseDir.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findFile(file);
    }

    return null;
  }

  @Nullable
  public static PsiDirectory findRelativeDirectory(String uri, PsiElement base) {
    if (base instanceof PsiFile) {
      PsiFile baseFile = (PsiFile) base;
      if (baseFile.getOriginalFile() != null) return findRelativeDirectory(uri, baseFile.getOriginalFile());
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseFile.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findDirectory(file);
    }
    else if (base instanceof PsiDirectory) {
      PsiDirectory baseDir = (PsiDirectory) base;
      VirtualFile file = VfsUtil.findRelativeFile(uri, baseDir.getVirtualFile());
      if (file == null) return null;
      return base.getManager().findDirectory(file);
    }

    return null;
  }

  /**
   * @return true if element specified is statement or expression statement. see JLS 14.5-14.8
   */
  public static boolean isStatement(PsiElement element) {
    PsiElement parent = element.getParent();

    if (element instanceof PsiExpressionListStatement) {
      // statement list allowed in for() init or update only
      if (!(parent instanceof PsiForStatement)) return false;
      final PsiForStatement forStatement = (PsiForStatement)parent;
      if (!(element == forStatement.getInitialization() || element == forStatement.getUpdate())) return false;
      final PsiExpressionList expressionList = ((PsiExpressionListStatement) element).getExpressionList();
      final PsiExpression[] expressions = expressionList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (!isStatement(expression)) return false;
      }
      return true;
    }
    else if (element instanceof PsiExpressionStatement) {
      return isStatement(((PsiExpressionStatement) element).getExpression());
    }
    if (element instanceof PsiDeclarationStatement) {
      if (parent instanceof PsiCodeBlock) return true;
      if (parent instanceof PsiCodeFragment) return true;

      if (!(parent instanceof PsiForStatement) || ((PsiForStatement)parent).getBody() == element) {
        return false;
      }
    }

    if (element instanceof PsiStatement) return true;
    if (element instanceof PsiAssignmentExpression) return true;
    if (isIncrementDecrementOperation(element)) return true;
    if (element instanceof PsiMethodCallExpression) return true;
    if (element instanceof PsiNewExpression) {
      return !(((PsiNewExpression) element).getType() instanceof PsiArrayType);
    }
    return element instanceof PsiCodeBlock;
  }

  @Nullable
  public static PsiElement getEnclosingStatement(PsiElement element) {
    while (element != null) {
      if (element.getParent() instanceof PsiCodeBlock) return element;
      element = element.getParent();
    }
    return null;
  }


  @Nullable
  public static PsiElement getElementInclusiveRange(PsiElement scope, TextRange range) {
    PsiElement psiElement = scope.findElementAt(range.getStartOffset());
    while (!psiElement.getTextRange().contains(range)) {
      if (psiElement == scope) return null;
      psiElement = psiElement.getParent();
    }
    return psiElement;
  }

  @Nullable
  public static PsiClass resolveClassInType(PsiType type) {
    if (type instanceof PsiClassType) {
      return ((PsiClassType) type).resolve();
    }
    if (type instanceof PsiArrayType) {
      return resolveClassInType(((PsiArrayType) type).getComponentType());
    }
    return null;
  }

  public static PsiClassType.ClassResolveResult resolveGenericsClassInType(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType) type;
      return classType.resolveGenerics();
    }
    if (type instanceof PsiArrayType) {
      return resolveGenericsClassInType(((PsiArrayType) type).getComponentType());
    }
    return PsiClassType.ClassResolveResult.EMPTY;
  }

  public static PsiType convertAnonymousToBaseType(PsiType type) {
    PsiClass psiClass = resolveClassInType(type);
    if (psiClass instanceof PsiAnonymousClass) {
      int dims = type.getArrayDimensions();
      type = ((PsiAnonymousClass) psiClass).getBaseClassType();
      while (dims != 0) {
        type = type.createArrayType();
        dims--;
      }
    }
    return type;
  }

  /** @return name for element using element structure info
   * TODO: Extend functionality for XML/JSP
   */
  public static String getName(PsiElement element) {
    String name = null;
    if (element instanceof PsiMetaBaseOwner) {
      final PsiMetaDataBase data = ((PsiMetaBaseOwner) element).getMetaData();
      if (data != null)
        name = data.getName(element);
    }
    if (name == null && element instanceof PsiNamedElement) {
      name = ((PsiNamedElement) element).getName();
    }
    return name;
  }

  public static boolean isApplicable(PsiMethod method, PsiSubstitutor substitutorForMethod, PsiExpressionList argList) {
    return getApplicabilityLevel(method, substitutorForMethod, argList) != NOT_APPLICABLE;
  }

  public static int getApplicabilityLevel(PsiMethod method, PsiSubstitutor substitutorForMethod, PsiExpressionList argList) {
    PsiExpression[] args = argList.getExpressions();
    final PsiParameter[] parms = method.getParameterList().getParameters();
    if (args.length < parms.length - 1) return NOT_APPLICABLE;

    final LanguageLevel languageLevel = getLanguageLevel(argList);
    if (!areFirstArgumentsApplicable(args, parms, languageLevel, substitutorForMethod)) return NOT_APPLICABLE;
    if (args.length == parms.length) {
      if (parms.length == 0) return FIXED_ARITY;
      PsiType parmType = getParameterType(parms[parms.length - 1], languageLevel, substitutorForMethod);
      PsiType argType = args[args.length - 1].getType();
      if (argType == null) return NOT_APPLICABLE;
      if (parmType.isAssignableFrom(argType)) return FIXED_ARITY;
    }

    if (method.isVarArgs() && languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0) {
      if (args.length < parms.length) return FIXED_ARITY;
      PsiParameter lastParameter = parms[parms.length - 1];
      if (!lastParameter.isVarArgs()) return NOT_APPLICABLE;
      PsiType lastParmType = getParameterType(lastParameter, languageLevel, substitutorForMethod);
      if (!(lastParmType instanceof PsiArrayType)) return NOT_APPLICABLE;
      lastParmType = ((PsiArrayType)lastParmType).getComponentType();

      for (int i = parms.length - 1; i < args.length; i++) {
        PsiType argType = args[i].getType();
        if (argType == null || !TypeConversionUtil.isAssignable(lastParmType, argType)) {
          return NOT_APPLICABLE;
        }
      }
      return VARARGS;
    }

    return NOT_APPLICABLE;
  }

  private static boolean areFirstArgumentsApplicable(final PsiExpression[] args, final PsiParameter[] parms, final LanguageLevel languageLevel,
                                                final PsiSubstitutor substitutorForMethod) {

    for (int i = 0; i < parms.length - 1; i++) {
      final PsiExpression arg = args[i];
      final PsiType type = arg.getType();
      if (type == null) return false;
      final PsiParameter parameter = parms[i];
      final PsiType substitutedParmType = getParameterType(parameter, languageLevel, substitutorForMethod);
      if (!TypeConversionUtil.isAssignable(substitutedParmType, type)) {
        return false;
      }
    }
    return true;
  }

  private static PsiType getParameterType(final PsiParameter parameter,
                                 final LanguageLevel languageLevel,
                                 final PsiSubstitutor substitutor) {
    PsiType parmType = parameter.getType();
    if (parmType instanceof PsiClassType) {
      parmType = ((PsiClassType)parmType).setLanguageLevel(languageLevel);
    }
    return substitutor.substituteAndCapture(parmType);
  }

  public static boolean equalOnClass(PsiSubstitutor s1, PsiSubstitutor s2, PsiClass aClass) {
    return equalOnEquivalentClasses(s1, aClass, s2, aClass);
  }

  public static boolean equalOnEquivalentClasses(PsiSubstitutor s1, PsiClass aClass, PsiSubstitutor s2, PsiClass bClass) {
    // assume generic class equals to non-generic
    if (aClass.hasTypeParameters() != bClass.hasTypeParameters()) return true;
    final PsiTypeParameter[] typeParameters1 = aClass.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = bClass.getTypeParameters();
    if (typeParameters1.length != typeParameters2.length) return false;
    for (int i = 0; i < typeParameters1.length; i++) {
      if (!Comparing.equal(s1.substitute(typeParameters1[i]), s2.substitute(typeParameters2[i]))) return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return true;
    final PsiClass containingClass1 = aClass.getContainingClass();
    final PsiClass containingClass2 = bClass.getContainingClass();

    if (containingClass1 != null && containingClass2 != null) {
      return equalOnEquivalentClasses(s1, containingClass1, s2, containingClass2);
    }

    return containingClass1 == null && containingClass2 == null;
  }

  /**
   * JLS 15.28
   */
  public static boolean isCompileTimeConstant(final PsiField field) {
    return field.hasModifierProperty(PsiModifier.FINAL)
           && (TypeConversionUtil.isPrimitiveAndNotNull(field.getType()) || field.getType().equalsToText("java.lang.String"))
           && field.hasInitializer()
           && isConstantExpression(field.getInitializer());
  }

  public static boolean allMethodsHaveSameSignature(PsiMethod[] methods) {
    if (methods.length == 0) return true;
    final MethodSignature methodSignature = methods[0].getSignature(PsiSubstitutor.EMPTY);
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (!methodSignature.equals(method.getSignature(PsiSubstitutor.EMPTY))) return false;
    }
    return true;
  }

  @Nullable  
  public static PsiExpression deparenthesizeExpression(PsiExpression rExpression) {
      if (rExpression instanceof PsiParenthesizedExpression) {
        return deparenthesizeExpression(
          ((PsiParenthesizedExpression)rExpression).getExpression());
      }
      if (rExpression instanceof PsiTypeCastExpression) {
        return deparenthesizeExpression(
          ((PsiTypeCastExpression)rExpression).getOperand());
      }
      return rExpression;
    }

  /**
   * Checks whether given class is inner (as opposed to nested)
   *
   * @param aClass
   */
  public static boolean isInnerClass(PsiClass aClass) {
    return !aClass.hasModifierProperty(PsiModifier.STATIC) && aClass.getContainingClass() != null;
  }

  @Nullable
  public static PsiElement findModifierInList(@NotNull final PsiModifierList modifierList, @NonNls String modifier) {
    final PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child.getText().equals(modifier)) return child;
    }
    return null;
  }

  @Nullable
  public static PsiClass getTopLevelClass(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      for (PsiClass aClass : classes) {
        if (PsiTreeUtil.isAncestor(aClass, element, false)) return aClass;
      }
    }
    return null;
  }

  /**
   * @return element with static modifier enclosing place and enclosed by aClass (if not null)
   */
  @Nullable
  public static PsiModifierListOwner getEnclosingStaticElement(PsiElement place, PsiClass aClass) {
    LOG.assertTrue(aClass == null || PsiTreeUtil.isAncestor(aClass, place, false));
    PsiElement parent = place;
    while (parent != aClass) {
      if (parent instanceof PsiFile) break;
      if (parent instanceof PsiModifierListOwner && ((PsiModifierListOwner)parent).hasModifierProperty(PsiModifier.STATIC)) {
        return (PsiModifierListOwner)parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  @Nullable
  public static PsiType getTypeByPsiElement(final PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiMethod) return ((PsiMethod)element).getReturnType();
    return null;
  }

  public static int getRootIndex(PsiElement root) {
    ASTNode node = root.getNode();
    while(node != null && node.getTreeParent() != null) {
      node = node.getTreeParent();
    }
    if(node != null) root = node.getPsi();
    final PsiFile containingFile = root.getContainingFile();
    final PsiFile[] psiRoots = containingFile.getPsiRoots();
    for (int i = 0; i < psiRoots.length; i++) {
      if(root == psiRoots[i]) return i;
    }
    throw new RuntimeException("invalid element");
  }

  public static int getRootsCount(final Language lang) {
    if(lang == StdLanguages.JSP) return 4;
    if(lang == StdLanguages.JSPX) return 4;
    return 1;
  }

  public static boolean isJspLanguage(final Language baseLanguage) {
    return baseLanguage == StdLanguages.JSP || baseLanguage == StdLanguages.JSPX;
  }

  public static JspFile getJspFile(final PsiElement element) {
    final FileViewProvider viewProvider = element.getContainingFile().getViewProvider();
    final PsiFile psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
    return psiFile instanceof JspFile ? (JspFile)psiFile : null;
  }

  public static VirtualFile getVirtualFile(PsiElement element) {
    if (element == null) {
      return null;
    }

    if (element instanceof PsiDirectory) {
      return ((PsiDirectory)element).getVirtualFile();
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return null;
    }

    return containingFile.getVirtualFile();
  }

  private static class TypeParameterIterator implements Iterator<PsiTypeParameter> {
    private int myIndex;
    private PsiTypeParameterListOwner myCurrentOwner;
    private boolean myNextObtained;
    private PsiTypeParameter[] myCurrentParams;

    private PsiTypeParameter myNext;

      private TypeParameterIterator(PsiTypeParameterListOwner owner) {
      myCurrentOwner = owner;
      obtainCurrentParams(owner);
      myNextObtained = false;
    }

    private void obtainCurrentParams(PsiTypeParameterListOwner owner) {
      myCurrentParams = owner.getTypeParameters();
      myIndex = myCurrentParams.length - 1;
    }

    public boolean hasNext() {
      nextElement();
      return myNext != null;
    }

    public PsiTypeParameter next() {
      nextElement();
      if (myNext == null) throw new NoSuchElementException();
      myNextObtained = false;
      return myNext;
    }

    public void remove() {
      throw new UnsupportedOperationException("TypeParameterIterator.remove");
    }

    private void nextElement() {
      if (myNextObtained) return;
      if (myIndex >= 0) {
        myNext = myCurrentParams[myIndex--];
        myNextObtained = true;
        return;
      }
      if (myCurrentOwner.hasModifierProperty(PsiModifier.STATIC) || myCurrentOwner.getContainingClass() == null) {
        myNext = null;
        myNextObtained = true;
        return;
      }
      myCurrentOwner = myCurrentOwner.getContainingClass();
      obtainCurrentParams(myCurrentOwner);
      nextElement();
    }
  }

  /**
   * Returns iterator of type parameters visible in owner. Type parameters are iterated in
   * inner-to-outer, right-to-left order.
   * @param owner
   */
  public static Iterator<PsiTypeParameter> typeParametersIterator(PsiTypeParameterListOwner owner) {
    return new TypeParameterIterator(owner);
  }

  public static boolean canBeOverriden(PsiMethod method) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return false;
    if (method.isConstructor()) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (method.hasModifierProperty(PsiModifier.FINAL)) return false;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (parentClass instanceof PsiAnonymousClass) return false;
    return !parentClass.hasModifierProperty(PsiModifier.FINAL);
  }

  public static PsiElement[] mapElements(ResolveResult[] candidates) {
    PsiElement[] result = new PsiElement[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      result[i] = candidates[i].getElement();
    }
    return result;
  }

  public static boolean hasErrorElementChild(PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  @Nullable
  public static PsiMember findEnclosingConstructorOrInitializer(PsiElement expression) {
    PsiMember parent = PsiTreeUtil.getParentOfType(expression, PsiClassInitializer.class, PsiMethod.class);
    if (parent instanceof PsiMethod && !((PsiMethod)parent).isConstructor()) return null;
    return parent;
  }

  public static boolean checkName(PsiElement element, String name) {
    if (element instanceof PsiMetaBaseOwner) {
      final PsiMetaDataBase data = ((PsiMetaBaseOwner) element).getMetaData();
      if (data != null) return name.equals(data.getName(element));
    }
    if (element instanceof PsiNamedElement) {
      return name.equals(((PsiNamedElement) element).getName());
    }
    return false;
  }

  public static boolean isRawSubstitutor (PsiTypeParameterListOwner owner, PsiSubstitutor substitutor) {
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(owner);
    while (iterator.hasNext()) {
      PsiTypeParameter parameter = iterator.next();
      if (substitutor.substitute(parameter) == null) return true;
    }
    return false;
  }

  public static boolean isUnderPsiRoot(PsiFile root, PsiElement element) {
    PsiElement[] psiRoots = root.getPsiRoots();
    for (PsiElement psiRoot : psiRoots) {
      if (PsiTreeUtil.isAncestor(psiRoot, element, false)) return true;
    }
    return false;
  }

  @NotNull
  public static Language getLanguageAtOffset (PsiFile file, int offset) {
    final PsiElement elt = file.findElementAt(offset);
    if (elt == null) return file.getLanguage();
    return findLanguageFromElement(elt, file);
  }

  @NotNull
  public static Language findLanguageFromElement(final PsiElement elt, final PsiFile file) {
    final Language language = elt.getLanguage();
    if (isInJspFile(file) && language == StdLanguages.XML) {
      ASTNode root = getRoot(elt.getNode());
      return root.getPsi().getLanguage();
    }

    return language;
  }

  @NotNull
  public static ASTNode getRoot(@NotNull ASTNode node) {
    ASTNode child = node;
    do {
      final ASTNode parent = child.getTreeParent();
      if (parent == null) return child;
      child = parent;
    }
    while (true);
  }

  public static Key<LanguageLevel> FILE_LANGUAGE_LEVEL_KEY = Key.create("FORCE_LANGUAGE_LEVEL"); 

  @NotNull
  public static LanguageLevel getLanguageLevel(@NotNull PsiElement element) {
    if (element instanceof PsiDirectory) return ((PsiDirectory)element).getLanguageLevel();
    final PsiFile file = element.getContainingFile();
    if (file == null) return element.getManager().getEffectiveLanguageLevel();
    final LanguageLevel forcedLanguageLevel = file.getUserData(FILE_LANGUAGE_LEVEL_KEY);
    if (forcedLanguageLevel != null) return forcedLanguageLevel;

    if (!(file instanceof PsiJavaFile)) {
      final PsiElement context = file.getContext();
      if (context != null) return getLanguageLevel(context);
      return element.getManager().getEffectiveLanguageLevel();
    }
    return ((PsiJavaFile)file).getLanguageLevel();
  }


  public static boolean isInstantiatable(PsiClass clazz) {
    return !clazz.hasModifierProperty(PsiModifier.ABSTRACT) &&
           clazz.hasModifierProperty(PsiModifier.PUBLIC) &&
           hasDefaultConstructor(clazz);
  }

  public static boolean hasDefaultConstructor(PsiClass clazz) {
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod cls: constructors) {
        if (cls.hasModifierProperty(PsiModifier.PUBLIC) && cls.getParameterList().getParametersCount() == 0) {
          return true;
        }
      }
    } else {
      final PsiClass superClass = clazz.getSuperClass();
      return superClass == null || hasDefaultConstructor(superClass);
    }
    return false;
  }

  @NotNull
  public static <T extends PsiElement> T getOriginalElement(@NotNull T psiElement) {
    final PsiFile psiFile = psiElement.getContainingFile();
    final PsiFile originalFile = psiFile.getOriginalFile();
    if (originalFile == null) return psiElement;
    final TextRange range = psiElement.getTextRange();
    final PsiElement element = originalFile.findElementAt(range.getStartOffset());
    return (T)PsiTreeUtil.getParentOfType(element, psiElement.getClass());
  }

}
