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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.VariableResolverProcessor;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiJavaCodeReferenceElementImpl extends CompositePsiElement implements PsiJavaCodeReferenceElement, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl");

  private volatile String myCachedQName = null;
  private volatile String myCachedTextSkipWhiteSpaceAndComments;
  private int myKindWhenDummy = CLASS_NAME_KIND;

  public static final int CLASS_NAME_KIND = 1;
  public static final int PACKAGE_NAME_KIND = 2;
  public static final int CLASS_OR_PACKAGE_NAME_KIND = 3;
  public static final int CLASS_FQ_NAME_KIND = 4;
  public static final int CLASS_FQ_OR_PACKAGE_NAME_KIND = 5;
  public static final int CLASS_IN_QUALIFIED_NEW_KIND = 6;

  public PsiJavaCodeReferenceElementImpl() {
    super(JavaElementType.JAVA_CODE_REFERENCE);
  }

  public int getTextOffset() {
    final ASTNode refName = getReferenceNameNode();
    return refName != null ? refName.getStartOffset() : super.getTextOffset();
  }

  public void setKindWhenDummy(final int kind) {
    LOG.assertTrue(getTreeParent().getElementType() == TokenType.DUMMY_HOLDER);
    myKindWhenDummy = kind;
  }

  public int getKind() {
    IElementType i = getTreeParent().getElementType();
    if (i == TokenType.DUMMY_HOLDER) {
      return myKindWhenDummy;
    }
    if (i == JavaElementType.TYPE) {
      return getTreeParent().getTreeParent().getPsi() instanceof PsiTypeCodeFragment ? CLASS_OR_PACKAGE_NAME_KIND : CLASS_NAME_KIND;
    }
    if (i == JavaElementType.EXTENDS_LIST ||
        i == JavaElementType.IMPLEMENTS_LIST ||
        i == JavaElementType.EXTENDS_BOUND_LIST ||
        i == JavaElementType.THROWS_LIST ||
        i == JavaElementType.THIS_EXPRESSION ||
        i == JavaElementType.SUPER_EXPRESSION ||
        i == JavaDocElementType.DOC_METHOD_OR_FIELD_REF ||
        i == JavaDocTokenType.DOC_TAG_VALUE_TOKEN ||
        i == JavaElementType.REFERENCE_PARAMETER_LIST ||
        i == JavaElementType.ANNOTATION) {
      if (isQualified()) {
        return CLASS_OR_PACKAGE_NAME_KIND;
      }

      return CLASS_NAME_KIND;
    }
    if (i == JavaElementType.NEW_EXPRESSION) {
      final ASTNode qualifier = getTreeParent().findChildByRole(ChildRole.QUALIFIER);
      return qualifier != null ? CLASS_IN_QUALIFIED_NEW_KIND : CLASS_NAME_KIND;
    }
    if (i == JavaElementType.ANONYMOUS_CLASS) {
      if (getTreeParent().getChildRole(this) == ChildRole.BASE_CLASS_REFERENCE) {
        LOG.assertTrue(getTreeParent().getTreeParent().getElementType() == JavaElementType.NEW_EXPRESSION);
        final ASTNode qualifier = getTreeParent().getTreeParent().findChildByRole(ChildRole.QUALIFIER);
        return qualifier != null ? CLASS_IN_QUALIFIED_NEW_KIND : CLASS_NAME_KIND;
      }
      else {
        return CLASS_OR_PACKAGE_NAME_KIND; // uncomplete code
      }
    }
    if (i == JavaElementType.PACKAGE_STATEMENT) {
      return PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATEMENT) {
      final boolean isOnDemand = ((PsiImportStatement)SourceTreeToPsiMap.treeElementToPsi(getTreeParent())).isOnDemand();
      return isOnDemand ? CLASS_FQ_OR_PACKAGE_NAME_KIND : CLASS_FQ_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATIC_STATEMENT) {
      return CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      final int parentKind = ((PsiJavaCodeReferenceElementImpl)getTreeParent()).getKind();
      switch (parentKind) {
        case CLASS_NAME_KIND:
          return CLASS_OR_PACKAGE_NAME_KIND;

        case PACKAGE_NAME_KIND:
          return PACKAGE_NAME_KIND;

        case CLASS_OR_PACKAGE_NAME_KIND:
          return CLASS_OR_PACKAGE_NAME_KIND;

        case CLASS_FQ_NAME_KIND:
          return CLASS_FQ_OR_PACKAGE_NAME_KIND;

        case CLASS_FQ_OR_PACKAGE_NAME_KIND:
          return CLASS_FQ_OR_PACKAGE_NAME_KIND;

        case CLASS_IN_QUALIFIED_NEW_KIND:
          return CLASS_IN_QUALIFIED_NEW_KIND; //??

        default:
          LOG.assertTrue(false);
          return -1;
      }
    }
    if (i == JavaElementType.CLASS || i == JavaElementType.PARAMETER_LIST || i == TokenType.ERROR_ELEMENT) {
      return CLASS_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATIC_REFERENCE) {
      return CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaDocElementType.DOC_TAG ||
        i == JavaDocElementType.DOC_INLINE_TAG ||
        i == JavaDocElementType.DOC_REFERENCE_HOLDER ||
        i == JavaDocElementType.DOC_TYPE_HOLDER) {
      return CLASS_OR_PACKAGE_NAME_KIND;
    }
    if (isCodeFragmentType(i)) {
      PsiJavaCodeReferenceCodeFragment fragment = (PsiJavaCodeReferenceCodeFragment)getTreeParent().getPsi();
      return fragment.isClassesAccepted() ? CLASS_FQ_OR_PACKAGE_NAME_KIND : PACKAGE_NAME_KIND;
    }
    CompositeElement parent = getTreeParent();
    String message = "Unknown parent for java code reference: '" + parent +"'; " +
                     "Type: "+i+"; \n";

    while (parent != null && parent.getPsi() instanceof PsiExpression) {
      parent = parent.getTreeParent();
      message += " Parent: '" + parent+"'; ";
    }
    LOG.error(message);
    return CLASS_NAME_KIND;
  }

  private static boolean isCodeFragmentType(IElementType type) {
    return type == TokenType.CODE_FRAGMENT || type instanceof ICodeFragmentElementType;
  }

  public void deleteChildInternal(@NotNull final ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      final ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public final ASTNode findChildByRole(final int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
    default:
           return null;

    case ChildRole.REFERENCE_NAME:
           if (getLastChildNode().getElementType() == JavaTokenType.IDENTIFIER) {
             return getLastChildNode();
           }
           else {
             if (getLastChildNode().getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST) {
               ASTNode current = getLastChildNode().getTreePrev();
               while (current != null && StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(current.getElementType())) {
                 current = current.getTreePrev();
               }
               if (current != null && current.getElementType() == JavaTokenType.IDENTIFIER) {
                 return current;
               }
             }
             return null;
           }

    case ChildRole.REFERENCE_PARAMETER_LIST:
           if (getLastChildNode().getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST) {
             return getLastChildNode();
           }
           else {
             return null;
           }

    case ChildRole.QUALIFIER:
           if (getFirstChildNode().getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
             return getFirstChildNode();
           }
           else {
             return null;
           }

    case ChildRole.DOT:
      return findChildByType(JavaTokenType.DOT);
    }
  }

  public final int getChildRole(final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType i = child.getElementType();
    if (i == JavaElementType.REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    else if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    }
    else if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return ChildRole.REFERENCE_NAME;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public String getCanonicalText() {
    switch (getKind()) {
      case CLASS_NAME_KIND:
      case CLASS_OR_PACKAGE_NAME_KIND:
      case CLASS_IN_QUALIFIED_NEW_KIND:
        final PsiElement target = resolve();
        if (target instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)target;
          String name = aClass.getQualifiedName();
          if (name == null) {
            name = aClass.getName(); //?
          }
          final PsiType[] types = getTypeParameters();
          if (types.length == 0) return name;

          final StringBuilder buf = new StringBuilder();
          buf.append(name);
          buf.append('<');
          for (int i = 0; i < types.length; i++) {
            if (i > 0) buf.append(',');
            buf.append(types[i].getCanonicalText());
          }
          buf.append('>');

          return buf.toString();
        }
        else if (target instanceof PsiPackage) {
          return ((PsiPackage)target).getQualifiedName();
        }
        else {
          LOG.assertTrue(target == null);
          return getTextSkipWhiteSpaceAndComments();
        }
      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return getTextSkipWhiteSpaceAndComments();

      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  public PsiReference getReference() {
    return this;
  }

  public final PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantResolver<PsiJavaReference> {
    private static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    public static JavaResolveResult[] _resolve(final PsiJavaReference ref, final boolean incompleteCode) {
      final PsiJavaCodeReferenceElementImpl referenceElement = (PsiJavaCodeReferenceElementImpl)ref;
      final int kind = referenceElement.getKind();
      JavaResolveResult[] result = referenceElement.resolve(kind);
      if (incompleteCode && result.length == 0 && kind != CLASS_FQ_NAME_KIND && kind != CLASS_FQ_OR_PACKAGE_NAME_KIND) {
        final VariableResolverProcessor processor = new VariableResolverProcessor(referenceElement);
        PsiScopesUtil.resolveAndWalk(processor, referenceElement, null, incompleteCode);
        result = processor.getResult();
        if (result.length > 0) {
          return result;
        }
        if (kind == CLASS_NAME_KIND) {
          return referenceElement.resolve(PACKAGE_NAME_KIND);
        }
      }
      return result;
    }

    public JavaResolveResult[] resolve(final PsiJavaReference ref, final boolean incompleteCode) {
      final JavaResolveResult[] result = _resolve(ref, incompleteCode);
      if (result.length > 0 && result[0].getElement() instanceof PsiClass) {
        final PsiType[] parameters = ((PsiJavaCodeReferenceElement)ref).getTypeParameters();
        final JavaResolveResult[] newResult = new JavaResolveResult[result.length];
        for (int i = 0; i < result.length; i++) {
          final CandidateInfo resolveResult = (CandidateInfo)result[i];
          final PsiClass aClass = (PsiClass)resolveResult.getElement();
          assert aClass != null;
          newResult[i] = !aClass.hasTypeParameters() ? resolveResult : new CandidateInfo(resolveResult, resolveResult.getSubstitutor().putAll(aClass, parameters));
        }
        return newResult;
      }
      return result;
    }
  }

  @NotNull
  public JavaResolveResult advancedResolve(final boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @NotNull
  public JavaResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManagerEx manager = getManager();
    if (manager == null) {
      LOG.assertTrue(false, "getManager() == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }

    final ResolveCache resolveCache = manager.getResolveCache();
    final ResolveResult[] results = resolveCache.resolveWithCaching(this, OurGenericsResolver.INSTANCE, true, incompleteCode);
    return results.length == 0 ? JavaResolveResult.EMPTY_ARRAY : (JavaResolveResult[])results;
  }

  private PsiSubstitutor updateSubstitutor(PsiSubstitutor subst, final PsiClass psiClass) {
    final PsiType[] parameters = getTypeParameters();
    if (psiClass != null) {
      subst = subst.putAll(psiClass, parameters);
    }
    return subst;
  }

  private JavaResolveResult[] resolve(final int kind) {
    switch (kind) {
      case CLASS_FQ_NAME_KIND:
        // TODO: support type parameters in FQ names
        final String textSkipWhiteSpaceAndComments = getTextSkipWhiteSpaceAndComments();
        if (textSkipWhiteSpaceAndComments == null || textSkipWhiteSpaceAndComments.length() == 0) return JavaResolveResult.EMPTY_ARRAY;
        final PsiClass aClass =
          JavaPsiFacade.getInstance(getProject()).findClass(textSkipWhiteSpaceAndComments, getResolveScope());
        if (aClass == null) return JavaResolveResult.EMPTY_ARRAY;
        return new JavaResolveResult[]{new CandidateInfo(aClass, updateSubstitutor(PsiSubstitutor.EMPTY, aClass), this, false)};

      case CLASS_IN_QUALIFIED_NEW_KIND: {
        PsiElement parent = getParent();
        if (parent instanceof JavaDummyHolder) {
          parent = parent.getContext();
        }

        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        final PsiExpression qualifier;
        if (parent instanceof PsiNewExpression) {
          qualifier = ((PsiNewExpression)parent).getQualifier();
          LOG.assertTrue(qualifier != null);
        }
        else if (parent instanceof PsiJavaCodeReferenceElement) {
          return JavaResolveResult.EMPTY_ARRAY;
        }
        else {
          LOG.assertTrue(false, "Invalid java reference!");
          return JavaResolveResult.EMPTY_ARRAY;
        }

        final PsiType qualifierType = qualifier.getType();
        if (qualifierType == null) return JavaResolveResult.EMPTY_ARRAY;
        if (!(qualifierType instanceof PsiClassType)) return JavaResolveResult.EMPTY_ARRAY;
        final JavaResolveResult result = PsiUtil.resolveGenericsClassInType(qualifierType);
        if (result.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
        final PsiElement classNameElement = getReferenceNameElement();
        if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
        final String className = classNameElement.getText();

        final ClassResolverProcessor processor = new ClassResolverProcessor(className, this);
        result.getElement().processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), this, this);
        return processor.getResult();
      }
      case CLASS_NAME_KIND: {
        final PsiElement classNameElement = getReferenceNameElement();
        if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
        final String className = classNameElement.getText();

        final ClassResolverProcessor processor = new ClassResolverProcessor(className, this);
        PsiScopesUtil.resolveAndWalk(processor, this, null);

        return processor.getResult();
      }

      case PACKAGE_NAME_KIND:
        final String packageName = getTextSkipWhiteSpaceAndComments();
        final PsiManager manager = getManager();
        final PsiPackage aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(packageName);
        if (aPackage == null || !aPackage.isValid()) {
          return JavaPsiFacade.getInstance(manager.getProject()).isPartOfPackagePrefix(packageName)
                 ? CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE
                 : JavaResolveResult.EMPTY_ARRAY;
        }
        return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY)};

      case CLASS_FQ_OR_PACKAGE_NAME_KIND: {
        final JavaResolveResult[] result = resolve(CLASS_FQ_NAME_KIND);
        if (result.length == 0) {
          return resolve(PACKAGE_NAME_KIND);
        }
        return result;
      }
      case CLASS_OR_PACKAGE_NAME_KIND:
        final JavaResolveResult[] classResolveResult = resolve(CLASS_NAME_KIND);
        // [dsl]todo[ik]: review this change I guess ResolveInfo should be merged if both
        // class and package resolve failed.
        if (classResolveResult.length == 0) {
          final JavaResolveResult[] packageResolveResult = resolve(PACKAGE_NAME_KIND);
          if (packageResolveResult.length > 0) return packageResolveResult;
        }
        return classResolveResult;
      default:
        LOG.assertTrue(false);
    }
    return JavaResolveResult.EMPTY_ARRAY;
  }

  public final PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    final PsiElement oldIdentifier = getReferenceNameElement();
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final PsiElement identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }


  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (isReferenceTo(element)) return this;

    switch (getKind()) {
      case CLASS_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
        if (!(element instanceof PsiClass)) {
          throw cannotBindError(element);
        }
        return bindToClass((PsiClass)element);

      case PACKAGE_NAME_KIND:
        if (!(element instanceof PsiPackage)) {
          throw cannotBindError(element);
        }
        return bindToPackage((PsiPackage)element);

      case CLASS_OR_PACKAGE_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        if (element instanceof PsiClass) {
          return bindToClass((PsiClass)element);
        }
        else if (element instanceof PsiPackage) {
          return bindToPackage((PsiPackage)element);
        }
        else {
          throw cannotBindError(element);
        }
      case CLASS_IN_QUALIFIED_NEW_KIND:
        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          final String name = aClass.getName();
          if (name == null) {
            throw new IncorrectOperationException(aClass.toString());
          }
          final TreeElement ref =
            Parsing.parseJavaCodeReferenceText(aClass.getManager(), name, SharedImplUtil.findCharTableByTree(this));
          getTreeParent().replaceChildInternal(this, ref);
          return SourceTreeToPsiMap.treeElementToPsi(ref);
        }
        else {
          throw cannotBindError(element);
        }

      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  private static IncorrectOperationException cannotBindError(final PsiElement element) {
    return new IncorrectOperationException("Cannot bind to "+element);
  }

  private PsiElement bindToClass(final PsiClass aClass) throws IncorrectOperationException {
    String qName = aClass.getQualifiedName();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    if (qName == null) {
      qName = aClass.getName();
      final PsiClass psiClass = facade.getResolveHelper().resolveReferencedClass(qName, this);
      if (!getManager().areElementsEquivalent(psiClass, aClass)) {
        throw cannotBindError(aClass);
      }
    }
    else {
      if (facade.findClass(qName, getResolveScope()) == null) {
        return this;
      }
    }

    final boolean preserveQualification = CodeStyleSettingsManager.getSettings(getProject()).USE_FQ_CLASS_NAMES && isFullyQualified();
    final PsiManager manager = aClass.getManager();
    String text = qName + getParameterList().getText();
    ASTNode ref = Parsing.parseJavaCodeReferenceText(manager, text, SharedImplUtil.findCharTableByTree(this));
    LOG.assertTrue(ref != null, "Failed to parse reference from text '" + text + "'");
    getTreeParent().replaceChildInternal(this, (TreeElement)ref);
    if (!preserveQualification /*&& (TreeUtil.findParent(ref, ElementType.DOC_COMMENT) == null)*/) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(aClass.getProject());
      ref = SourceTreeToPsiMap.psiElementToTree(
        codeStyleManager.shortenClassReferences(SourceTreeToPsiMap.treeElementToPsi(ref), JavaCodeStyleManager.UNCOMPLETE_CODE)
      );
    }
    return SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  private boolean isFullyQualified() {
    switch (getKind()) {
      case CLASS_OR_PACKAGE_NAME_KIND:
        if (resolve() instanceof PsiPackage) return true;
      case CLASS_NAME_KIND:
        break;

      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return true;

      default:
        LOG.assertTrue(false);
        return true;
    }

    final ASTNode qualifier = findChildByRole(ChildRole.QUALIFIER);
    if (qualifier == null) return false;

    LOG.assertTrue(qualifier.getElementType() == JavaElementType.JAVA_CODE_REFERENCE);
    final PsiElement refElement = ((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(qualifier)).resolve();
    if (refElement instanceof PsiPackage) return true;

    return ((PsiJavaCodeReferenceElementImpl)SourceTreeToPsiMap.treeElementToPsi(qualifier)).isFullyQualified();
  }

  private PsiElement bindToPackage(final PsiPackage aPackage) throws IncorrectOperationException {
    final String qName = aPackage.getQualifiedName();
    if (qName.length() == 0) {
      throw new IncorrectOperationException("Cannot bind to default package: "+aPackage);
    }
    final TreeElement ref = Parsing.parseJavaCodeReferenceText(getManager(), qName, SharedImplUtil.findCharTableByTree(this));
    getTreeParent().replaceChildInternal(this, ref);
    return SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  public boolean isReferenceTo(final PsiElement element) {
    switch (getKind()) {
      case CLASS_NAME_KIND:
      case CLASS_IN_QUALIFIED_NEW_KIND:
        if (!(element instanceof PsiClass)) return false;
        break;

      case CLASS_FQ_NAME_KIND: {
        if (!(element instanceof PsiClass)) return false;
        final String qName = ((PsiClass)element).getQualifiedName();
        return qName != null && qName.equals(getCanonicalText());
      }

      case PACKAGE_NAME_KIND: {
        if (!(element instanceof PsiPackage)) return false;
        final String qName = ((PsiPackage)element).getQualifiedName();
        return qName.equals(getCanonicalText());
      }

      case CLASS_OR_PACKAGE_NAME_KIND:
        //        if (lastChild.type != IDENTIFIER) return false;
        if (element instanceof PsiPackage) {
          final String qName = ((PsiPackage)element).getQualifiedName();
          return qName.equals(getCanonicalText());
        }
        else if (element instanceof PsiClass) {
          final PsiIdentifier nameIdentifier = ((PsiClass)element).getNameIdentifier();
          if (nameIdentifier == null) return false;
          PsiElement nameElement = getReferenceNameElement();
          return nameElement != null && nameElement.textMatches(nameIdentifier) &&
                 element.getManager().areElementsEquivalent(resolve(), element);
        }
        else {
          return false;
        }

      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        if (element instanceof PsiClass) {
          final String qName = ((PsiClass)element).getQualifiedName();
          return qName != null && qName.equals(getCanonicalText());
        }
        else if (element instanceof PsiPackage) {
          final String qName = ((PsiPackage)element).getQualifiedName();
          return qName.equals(getCanonicalText());
        }
        else {
          return false;
        }
      default:
        LOG.assertTrue(false);
        return true;
    }

    final ASTNode referenceNameElement = getReferenceNameNode();
    if (referenceNameElement == null || referenceNameElement.getElementType() != JavaTokenType.IDENTIFIER) return false;
    final String name = ((PsiClass)element).getName();
    return name != null && referenceNameElement.getText().equals(name) && element.getManager().areElementsEquivalent(resolve(), element);
  }

  private String getTextSkipWhiteSpaceAndComments() {
    String whiteSpaceAndComments = myCachedTextSkipWhiteSpaceAndComments;
    if (whiteSpaceAndComments == null) {
      myCachedTextSkipWhiteSpaceAndComments = whiteSpaceAndComments = SourceUtil.getTextSkipWhiteSpaceAndComments(this);
    }
    return whiteSpaceAndComments;
  }

  public String getClassNameText() {
    String cachedQName = myCachedQName;
    if (cachedQName == null) {
      myCachedQName = cachedQName = PsiNameHelper.getQualifiedClassName(getTextSkipWhiteSpaceAndComments(), false);
    }
    return cachedQName;
  }

  public void fullyQualify(final PsiClass targetClass) {
    final int kind = getKind();
    if (kind != CLASS_NAME_KIND && kind != CLASS_OR_PACKAGE_NAME_KIND && kind != CLASS_IN_QUALIFIED_NEW_KIND) {
      LOG.error("Wrong kind " + kind);
      return;
    }
    JavaSourceUtil.fullyQualifyReference(this, targetClass);
  }

  public boolean isQualified() {
    return getChildRole(getFirstChildNode()) != ChildRole.REFERENCE_NAME;
  }

  public PsiElement getQualifier() {
    return SourceTreeToPsiMap.treeElementToPsi(findChildByRole(ChildRole.QUALIFIER));
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedQName = null;
    myCachedTextSkipWhiteSpaceAndComments = null;
  }

  public Object[] getVariants() {
    final ElementFilter filter;
    switch (getKind()) {

    case CLASS_OR_PACKAGE_NAME_KIND:
           filter = new OrFilter();
             ((OrFilter)filter).addFilter(ElementClassFilter.CLASS);
             ((OrFilter)filter).addFilter(ElementClassFilter.PACKAGE_FILTER);
           break;
    case CLASS_NAME_KIND:
           filter = ElementClassFilter.CLASS;
           break;
    case PACKAGE_NAME_KIND:
           filter = ElementClassFilter.PACKAGE_FILTER;
           break;
    case CLASS_FQ_NAME_KIND:
    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           filter = new OrFilter();
             ((OrFilter)filter).addFilter(ElementClassFilter.PACKAGE_FILTER);
           if (isQualified()) {
               ((OrFilter)filter).addFilter(ElementClassFilter.CLASS);
           }
           break;
    case CLASS_IN_QUALIFIED_NEW_KIND:
           filter = ElementClassFilter.CLASS;
           break;
    default:
           throw new RuntimeException("Unknown reference type");
    }

    return PsiImplUtil.getReferenceVariantsByFilter(this, filter);
  }

  public boolean isSoft() {
    return false;
  }

  public void processVariants(final PsiScopeProcessor processor) {
    final OrFilter filter = new OrFilter();
    PsiElement superParent = getParent();
    boolean smartCompletion = true;
    if (isQualified()) {
      smartCompletion = false;
    }
    else {
      while (superParent != null) {
        if (superParent instanceof PsiCodeBlock || superParent instanceof PsiVariable) {
          smartCompletion = false;
          break;
        }
        superParent = superParent.getParent();
      }
    }
    if (!smartCompletion && !isCodeFragmentType(getTreeParent().getElementType()) && !(getParent() instanceof PsiAnnotation)) {
      /*filter.addFilter(ElementClassFilter.CLASS);
      filter.addFilter(ElementClassFilter.PACKAGE);*/
      filter.addFilter(new AndFilter(ElementClassFilter.METHOD, new NotFilter(new ConstructorFilter())));
      filter.addFilter(ElementClassFilter.VARIABLE);
    }
    switch (getKind()) {
    case CLASS_OR_PACKAGE_NAME_KIND:
      addClassFilter(filter);
      filter.addFilter(ElementClassFilter.PACKAGE_FILTER);
           break;
    case CLASS_NAME_KIND:
      addClassFilter(filter);
      break;
    case PACKAGE_NAME_KIND:
           filter.addFilter(ElementClassFilter.PACKAGE_FILTER);
           break;
    case CLASS_FQ_NAME_KIND:
    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           filter.addFilter(ElementClassFilter.PACKAGE_FILTER);
           if (isQualified()) {
             filter.addFilter(ElementClassFilter.CLASS);
           }
           break;
    case CLASS_IN_QUALIFIED_NEW_KIND:
           final PsiElement parent = getParent();
           if (parent instanceof PsiNewExpression) {
             final PsiNewExpression newExpr = (PsiNewExpression)parent;
             final PsiType type = newExpr.getQualifier().getType();
             final PsiClass aClass = PsiUtil.resolveClassInType(type);
             if (aClass != null) {
               aClass.processDeclarations(new FilterScopeProcessor(new AndFilter(ElementClassFilter.CLASS, new ModifierFilter(PsiModifier.STATIC, false)),
                                                                   processor), ResolveState.initial(), null, this);
             }
             //          else{
             //            throw new RuntimeException("Qualified new is not allowed for primitives");
             //          }
           }
           //        else{
           //          throw new RuntimeException("Reference type is qualified new, but parent expression is: " + getParent());
           //        }

           return;
    default:
           throw new RuntimeException("Unknown reference type");
    }
    final FilterScopeProcessor proc = new FilterScopeProcessor(filter, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  private void addClassFilter(final OrFilter filter) {
    if (getParent() instanceof PsiAnnotation) {
      filter.addFilter(new AnnotationTypeFilter());
    }
    else {
      filter.addFilter(ElementClassFilter.CLASS);
    }
  }

  public PsiElement getReferenceNameElement() {
    return SourceTreeToPsiMap.treeElementToPsi(getReferenceNameNode());
  }

  @Nullable
  private ASTNode getReferenceNameNode() {
    return findChildByRole(ChildRole.REFERENCE_NAME);
  }


  public PsiReferenceParameterList getParameterList() {
    return (PsiReferenceParameterList)findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  public String getQualifiedName() {
    switch (getKind()) {
      case CLASS_NAME_KIND:
      case CLASS_OR_PACKAGE_NAME_KIND:
      case CLASS_IN_QUALIFIED_NEW_KIND:
        final PsiElement target = resolve();
        if (target instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)target;
          String name = aClass.getQualifiedName();
          if (name == null) {
            name = aClass.getName(); //?
          }
          return name;
        }
        else if (target instanceof PsiPackage) {
          return ((PsiPackage)target).getQualifiedName();
        }
        else {
          LOG.assertTrue(target == null);
          return getClassNameText();
        }

      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return getTextSkipWhiteSpaceAndComments(); // there cannot be any <...>

      default:
        LOG.assertTrue(false);
        return null;
    }

  }

  public String getReferenceName() {
    final ASTNode childByRole = getReferenceNameNode();
    if (childByRole == null) return null;
    return childByRole.getText();
  }

  public final TextRange getRangeInElement() {
    final TreeElement nameChild = (TreeElement)getReferenceNameNode();
    if (nameChild == null) return new TextRange(0, getTextLength());
    final int startOffset = nameChild.getStartOffsetInParent();
    return new TextRange(startOffset, startOffset + nameChild.getTextLength());
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList == null) return PsiType.EMPTY_ARRAY;
    return parameterList.getTypeArguments();

  }

  public final PsiElement getElement() {
    return this;
  }

  public final void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public final String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }
}
