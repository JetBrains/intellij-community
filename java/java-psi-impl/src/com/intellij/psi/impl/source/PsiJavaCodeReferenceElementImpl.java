// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class PsiJavaCodeReferenceElementImpl extends CompositePsiElement implements PsiAnnotatedJavaCodeReferenceElement, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance(PsiJavaCodeReferenceElementImpl.class);

  private volatile String myCachedQName;
  private volatile String myCachedNormalizedText;
  private volatile Kind myKindWhenDummy = Kind.CLASS_NAME_KIND;

  public enum Kind {
    CLASS_NAME_KIND,
    PACKAGE_NAME_KIND,
    CLASS_OR_PACKAGE_NAME_KIND,
    CLASS_FQ_NAME_KIND,
    CLASS_FQ_OR_PACKAGE_NAME_KIND,
    CLASS_IN_QUALIFIED_NEW_KIND,
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod") private final int myHC = ourHC++;

  public PsiJavaCodeReferenceElementImpl() {
    super(JavaElementType.JAVA_CODE_REFERENCE);
  }

  @Override
  public final int hashCode() {
    return myHC;
  }

  @Override
  public int getTextOffset() {
    final ASTNode refName = getReferenceNameNode();
    return refName != null ? refName.getStartOffset() : super.getTextOffset();
  }

  public void setKindWhenDummy(@NotNull Kind kind) {
    IElementType type = getTreeParent().getElementType();
    LOG.assertTrue(isDummy(type), type);
    myKindWhenDummy = kind;
  }

  private static boolean isDummy(final IElementType type) {
    return type == TokenType.DUMMY_HOLDER || type == JavaElementType.DUMMY_ELEMENT;
  }

  @NotNull
  public Kind getKindEnum(@NotNull PsiFile containingFile) {
    if (!containingFile.isValid()) { // optimization to avoid relatively expensive this.isValid check
      // but still provide diagnostics for this element and not its containing DummyHolder file
      PsiUtilCore.ensureValid(this);
    }
    CompositeElement treeParent = getTreeParent();
    IElementType i = treeParent.getElementType();
    if (isDummy(i)) {
      return myKindWhenDummy;
    }
    if (i == JavaElementType.TYPE) {
      return treeParent.getTreeParent().getPsi() instanceof PsiTypeCodeFragment ? Kind.CLASS_OR_PACKAGE_NAME_KIND : Kind.CLASS_NAME_KIND;
    }
    if (i == JavaElementType.EXTENDS_LIST ||
        i == JavaElementType.IMPLEMENTS_LIST ||
        i == JavaElementType.EXTENDS_BOUND_LIST ||
        i == JavaElementType.THROWS_LIST ||
        i == JavaElementType.THIS_EXPRESSION ||
        i == JavaElementType.SUPER_EXPRESSION ||
        i == JavaDocElementType.DOC_METHOD_OR_FIELD_REF ||
        i == JavaDocElementType.DOC_TAG_VALUE_ELEMENT ||
        i == JavaElementType.REFERENCE_PARAMETER_LIST ||
        i == JavaElementType.ANNOTATION ||
        i == JavaElementType.USES_STATEMENT ||
        i == JavaElementType.PROVIDES_STATEMENT ||
        i == JavaElementType.PROVIDES_WITH_LIST) {
      return isQualified() ? Kind.CLASS_OR_PACKAGE_NAME_KIND : Kind.CLASS_NAME_KIND;
    }
    if (i == JavaElementType.NEW_EXPRESSION) {
      final ASTNode qualifier = treeParent.findChildByRole(ChildRole.QUALIFIER);
      return qualifier != null ? Kind.CLASS_IN_QUALIFIED_NEW_KIND : Kind.CLASS_NAME_KIND;
    }
    if (i == JavaElementType.ANONYMOUS_CLASS) {
      if (treeParent.getChildRole(this) == ChildRole.BASE_CLASS_REFERENCE) {
        CompositeElement granny = treeParent.getTreeParent();
        IElementType gType = granny.getElementType();
        LOG.assertTrue(gType == JavaElementType.NEW_EXPRESSION, gType);
        final ASTNode qualifier = granny.findChildByRole(ChildRole.QUALIFIER);
        return qualifier != null ? Kind.CLASS_IN_QUALIFIED_NEW_KIND : Kind.CLASS_NAME_KIND;
      }
      else {
        return Kind.CLASS_OR_PACKAGE_NAME_KIND; // incomplete code
      }
    }
    if (i == JavaElementType.PACKAGE_STATEMENT || i == JavaElementType.EXPORTS_STATEMENT || i == JavaElementType.OPENS_STATEMENT) {
      return Kind.PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATEMENT) {
      boolean isOnDemand = SourceTreeToPsiMap.<PsiImportStatement>treeToPsiNotNull(treeParent).isOnDemand();
      return isOnDemand ? Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND : Kind.CLASS_FQ_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATIC_STATEMENT) {
      return Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      Kind parentKind = ((PsiJavaCodeReferenceElementImpl)treeParent).getKindEnum(containingFile);
      if (parentKind == Kind.CLASS_NAME_KIND) {
        return Kind.CLASS_OR_PACKAGE_NAME_KIND;
      }
      if (parentKind == Kind.CLASS_FQ_NAME_KIND) {
        return Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND;
      }
      return parentKind;
    }
    if (i == JavaElementType.CLASS || i == JavaElementType.PARAMETER_LIST || i == TokenType.ERROR_ELEMENT) {
      return Kind.CLASS_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATIC_REFERENCE) {
      return Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaDocElementType.DOC_TAG ||
        i == JavaDocElementType.DOC_INLINE_TAG ||
        i == JavaDocElementType.DOC_REFERENCE_HOLDER ||
        i == JavaDocElementType.DOC_TYPE_HOLDER) {
      PsiDocComment docComment = PsiTreeUtil.getParentOfType(this, PsiDocComment.class);
      if (JavaDocUtil.isInsidePackageInfo(docComment)) {
        return Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND;
      }

      return Kind.CLASS_OR_PACKAGE_NAME_KIND;
    }
    if (isCodeFragmentType(i)) {
      PsiJavaCodeReferenceCodeFragment fragment = (PsiJavaCodeReferenceCodeFragment)treeParent.getPsi();
      return fragment.isClassesAccepted() ? Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND : Kind.PACKAGE_NAME_KIND;
    }

    diagnoseUnknownParent();
    return Kind.CLASS_NAME_KIND;
  }

  /**
   * @deprecated Use {@link #getKindEnum(PsiFile)} instead
   */
  @Deprecated
  public int getKind(@NotNull PsiFile containingFile) {
    return getKindEnum(containingFile).ordinal() + 1;
  }

  private void diagnoseUnknownParent() {
    CompositeElement parent = getTreeParent();
    IElementType i = parent.getElementType();
    StringBuilder msg = new StringBuilder("Unknown parent for java code reference: '").append(parent).append("'; Type: ").append(i).append(";\n");
    while (parent != null && parent.getPsi() instanceof PsiExpression) {
      parent = parent.getTreeParent();
      msg.append(" Parent: '").append(parent).append("'; \n");
    }
    if (parent != null) {
      msg.append(DebugUtil.treeToString(parent, false));
    }
    LOG.error(msg.toString());
  }

  private static boolean isCodeFragmentType(IElementType type) {
    return type == TokenType.CODE_FRAGMENT || type instanceof ICodeFragmentElementType;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      ASTNode dot = findChildByType(JavaTokenType.DOT, child);
      assert dot != null : this;
      deleteChildRange(child.getPsi(), dot.getPsi());

      ASTNode ref = findChildByRole(ChildRole.REFERENCE_NAME);
      assert ref != null : this;
      PsiElement lastChild = ref.getPsi().getPrevSibling();
      if (lastChild != null) {
        PsiElement modifierList = PsiImplUtil.findNeighbourModifierList(this);
        if (modifierList != null) {
          modifierList.addRange(getFirstChild(), lastChild);
        }
        else {
          getParent().addRangeBefore(getFirstChild(), lastChild, this);
        }
        // during previous operations, formatter support could have altered the children (if they're whitespace),
        // so we retrieve and check them again
        if (ref != getFirstChild()) {
          deleteChildRange(getFirstChild(), ref.getPsi().getPrevSibling());
        }
      }
    }
    else if (child.getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST) {
      replaceChildInternal(child, PsiReferenceExpressionImpl.createEmptyRefParameterList(getProject()));
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  @Override
  public final ASTNode findChildByRole(final int role) {
    LOG.assertTrue(ChildRole.isUnique(role), role);

    switch (role) {
      case ChildRole.REFERENCE_NAME:
        return TreeUtil.findChildBackward(this, JavaTokenType.IDENTIFIER);

      case ChildRole.REFERENCE_PARAMETER_LIST:
        TreeElement lastChild = getLastChildNode();
        return lastChild.getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST ? lastChild : null;

      case ChildRole.QUALIFIER:
        return findChildByType(JavaElementType.JAVA_CODE_REFERENCE);

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);
    }

    return null;
  }

  @Override
  public final int getChildRole(@NotNull final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType i = child.getElementType();
    if (i == JavaElementType.REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    }
    if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    if (i == JavaTokenType.IDENTIFIER) {
      return ChildRole.REFERENCE_NAME;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return getCanonicalText(false, null, getContainingFile());
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated, PsiAnnotation @Nullable [] annotations) {
    return getCanonicalText(annotated, annotations, getContainingFile());
  }

  @NotNull
  private String getCanonicalText(boolean annotated, PsiAnnotation @Nullable [] annotations, @NotNull PsiFile containingFile) {
    Kind kind = getKindEnum(containingFile);
    switch (kind) {
      case CLASS_NAME_KIND:
      case CLASS_OR_PACKAGE_NAME_KIND:
      case CLASS_IN_QUALIFIED_NEW_KIND:
        JavaResolveResult[] results = PsiImplUtil.multiResolveImpl(containingFile.getProject(), containingFile, this, false, OurGenericsResolver.INSTANCE);
        PsiElement target = results.length == 1 ? results[0].getElement() : null;
        if (target instanceof PsiClass) {
          StringBuilder buffer = new StringBuilder();

          PsiClass aClass = (PsiClass)target;
          PsiElement qualifier = getQualifier();

          String prefix = null;
          if (qualifier instanceof PsiJavaCodeReferenceElementImpl) {
            prefix = ((PsiJavaCodeReferenceElementImpl)qualifier).getCanonicalText(annotated, annotations, containingFile);
            annotations = null;
          }
          else {
            String fqn = aClass.getQualifiedName();
            if (fqn != null) {
              prefix = StringUtil.getPackageName(fqn);
            }
          }

          if (!StringUtil.isEmpty(prefix)) {
            buffer.append(prefix);
            buffer.append('.');
          }

          if (annotated) {
            List<PsiAnnotation> list = annotations != null ? Arrays.asList(annotations) : getAnnotations();
            PsiNameHelper.appendAnnotations(buffer, list, true);
          }

          buffer.append(aClass.getName());

          PsiNameHelper.appendTypeArgs(buffer, getTypeParameters(), true, annotated);

          return buffer.toString();
        }
        else if (target instanceof PsiPackage) {
          return ((PsiPackage)target).getQualifiedName();
        }
        else {
          LOG.assertTrue(target == null, target);
          return getNormalizedText();
        }

      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return getNormalizedText();

      default:
        LOG.error(kind);
        return null;
    }
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public final PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @NotNull
  public static TextRange calcRangeInElement(CompositePsiElement refElement) {
    TreeElement nameChild = (TreeElement)refElement.findChildByRole(ChildRole.REFERENCE_NAME);
    if (nameChild == null) {
      TreeElement dot = (TreeElement)refElement.findChildByRole(ChildRole.DOT);
      if (dot == null) {
        throw new IllegalStateException(refElement.toString());
      }
      return TextRange.from(dot.getStartOffsetInParent() + dot.getTextLength(), 0);
    }
    return TextRange.from(nameChild.getStartOffsetInParent(), nameChild.getTextLength());
  }

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantContextResolver<PsiJavaReference> {
    private static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull PsiJavaReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      PsiJavaCodeReferenceElementImpl referenceElement = (PsiJavaCodeReferenceElementImpl)ref;
      Kind kind = referenceElement.getKindEnum(containingFile);
      JavaResolveResult[] result = referenceElement.resolve(kind, containingFile);

      if (incompleteCode && result.length == 0 && kind != Kind.CLASS_FQ_NAME_KIND && kind != Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND) {
        VariableResolverProcessor processor = new VariableResolverProcessor(referenceElement, containingFile);
        PsiScopesUtil.resolveAndWalk(processor, referenceElement, null, true);
        result = processor.getResult();
        if (result.length == 0 && kind == Kind.CLASS_NAME_KIND) {
          result = referenceElement.resolve(Kind.PACKAGE_NAME_KIND, containingFile);
        }
      }

      if (result.length == 0 && (kind == Kind.CLASS_OR_PACKAGE_NAME_KIND || kind == Kind.CLASS_NAME_KIND)) {
        String qualifiedName = referenceElement.getClassNameText();
        if (qualifiedName != null) {
          result = tryClassResult(qualifiedName, referenceElement);
        }
      }

      JavaResolveUtil.substituteResults(referenceElement, result);

      return result;
    }
  }

  public static JavaResolveResult[] tryClassResult(String qualifiedName, PsiJavaCodeReferenceElement referenceElement) {
    PsiElement qualifier = referenceElement.getQualifier();
    Project project = referenceElement.getProject();
    if (qualifier instanceof PsiJavaCodeReferenceElement) {
      PsiClass referencedClass = ResolveClassUtil.resolveClass((PsiJavaCodeReferenceElement)qualifier, referenceElement.getContainingFile());
      //class is always preferred to package => when such a class exists, the qualified name can point to inner class only and that check must already have been failed
      if (referencedClass != null) {
        return JavaResolveResult.EMPTY_ARRAY;
      }
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, referenceElement.getResolveScope());
      if (aClass != null) {
        return new JavaResolveResult[] {new CandidateInfo(aClass, PsiSubstitutor.EMPTY, referenceElement, false)};
      }
    }
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(final boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return PsiImplUtil.multiResolveImpl(this, incompleteCode, OurGenericsResolver.INSTANCE);
  }

  @NotNull
  private PsiSubstitutor updateSubstitutor(@NotNull PsiClass psiClass) {
    @NotNull PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    final PsiType[] parameters = getTypeParameters();
    subst = subst.putAll(psiClass, parameters);
    return subst;
  }

  private JavaResolveResult @NotNull [] resolve(@NotNull Kind kind, @NotNull PsiFile containingFile) {
    ProgressManager.checkCanceled();
    switch (kind) {
      case CLASS_FQ_NAME_KIND:
        String text = getNormalizedText();
        if (!StringUtil.isEmptyOrSpaces(text)) {
          PsiClass aClass = JavaPsiFacade.getInstance(containingFile.getProject()).findClass(text, getResolveScope());
          if (aClass != null) {
            return new JavaResolveResult[]{new CandidateInfo(aClass, updateSubstitutor(aClass), this, false)};
          }
        }
        return JavaResolveResult.EMPTY_ARRAY;

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
          LOG.error("Invalid java reference: "+ parent);
          return JavaResolveResult.EMPTY_ARRAY;
        }

        final PsiType qualifierType = qualifier.getType();
        if (qualifierType == null) return JavaResolveResult.EMPTY_ARRAY;
        if (!(qualifierType instanceof PsiClassType)) return JavaResolveResult.EMPTY_ARRAY;
        final JavaResolveResult result = PsiUtil.resolveGenericsClassInType(qualifierType);
        final PsiElement resultElement = result.getElement();
        if (resultElement == null) return JavaResolveResult.EMPTY_ARRAY;
        final PsiElement classNameElement = getReferenceNameElement();
        if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
        final String className = classNameElement.getText();

        final ClassResolverProcessor processor = new ClassResolverProcessor(className, this, containingFile);
        resultElement.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), this, this);
        return processor.getResult();
      }

      case CLASS_NAME_KIND:
        final PsiElement classNameElement = getReferenceNameElement();
        if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
        final String className = classNameElement.getText();
        final ClassResolverProcessor processor = new ClassResolverProcessor(className, this, containingFile);
        PsiScopesUtil.resolveAndWalk(processor, this, null);
        return processor.getResult();

      case PACKAGE_NAME_KIND:
        String packageName = getNormalizedText();
        Project project = getManager().getProject();
        PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
        if (aPackage != null && aPackage.isValid()) {
          return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY, this, false)};
        }
        else if (JavaPsiFacade.getInstance(project).isPartOfPackagePrefix(packageName)) {
          return CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE;
        }
        else {
          return JavaResolveResult.EMPTY_ARRAY;
        }

      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
      case CLASS_OR_PACKAGE_NAME_KIND:
        Kind classKind = kind == Kind.CLASS_OR_PACKAGE_NAME_KIND ? Kind.CLASS_NAME_KIND : Kind.CLASS_FQ_NAME_KIND;
        JavaResolveResult[] result;

        // A single-type-import declaration D in a compilation unit C of package P
        // that imports a type named N shadows, throughout C, the declarations of
        // ... any top level type named N declared in another compilation unit of P.
        PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(this, PsiImportStatement.class);
        if (importStatement != null && (!importStatement.isOnDemand() || !isQualified())) {
          result = resolve(Kind.PACKAGE_NAME_KIND, containingFile);
          if (result.length == 0) {
            result = resolve(classKind, containingFile);
          }
        }
        else {
          result = resolve(classKind, containingFile);
          if (result.length == 1 && !result[0].isAccessible()) {
            JavaResolveResult[] packageResult = resolve(Kind.PACKAGE_NAME_KIND, containingFile);
            if (packageResult.length != 0) {
              result = packageResult;
            }
          }
          else if (result.length == 0) {
            result = resolve(Kind.PACKAGE_NAME_KIND, containingFile);
          }
        }

        return result;
    }

    LOG.error(this);
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public final PsiElement handleElementRename(@NotNull final String newElementName) throws IncorrectOperationException {
    final PsiElement oldIdentifier = getReferenceNameElement();
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final PsiElement identifier = JavaPsiFacade.getElementFactory(getProject()).createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    PsiFile containingFile = getContainingFile();
    CheckUtil.checkWritable(containingFile);
    if (isReferenceTo(element)) return this;

    Kind kind = getKindEnum(containingFile);
    switch (kind) {
      case CLASS_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
        if (!(element instanceof PsiClass)) {
          throw cannotBindError(element, kind);
        }
        return bindToClass((PsiClass)element, containingFile);

      case PACKAGE_NAME_KIND:
        if (!(element instanceof PsiPackage)) {
          throw cannotBindError(element, kind);
        }
        return bindToPackage((PsiPackage)element);

      case CLASS_OR_PACKAGE_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        if (element instanceof PsiClass) {
          return bindToClass((PsiClass)element, containingFile);
        }
        else if (element instanceof PsiPackage) {
          return bindToPackage((PsiPackage)element);
        }
        else {
          throw cannotBindError(element, kind);
        }
      case CLASS_IN_QUALIFIED_NEW_KIND:
        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          final String name = aClass.getName();
          if (name == null) {
            throw new IncorrectOperationException(aClass.toString());
          }
          final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(containingFile.getProject()).getParserFacade();
          final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(name, getParent());
          getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
          return ref;
        }
        else {
          throw cannotBindError(element, kind);
        }

      default:
        LOG.error(kind);
        return null;
    }
  }

  @NotNull
  private static IncorrectOperationException cannotBindError(@NotNull PsiElement element, @NotNull Kind kind) {
    return new IncorrectOperationException("Cannot bind to " + element+" of kind: "+kind);
  }

  private PsiElement bindToClass(@NotNull PsiClass aClass, @NotNull PsiFile containingFile) throws IncorrectOperationException {
    String qName = aClass.getQualifiedName();
    Project project = containingFile.getProject();
    boolean preserveQualification = JavaCodeStyleSettingsFacade.getInstance(project).useFQClassNames() && isFullyQualified(containingFile);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    if (qName == null) {
      qName = aClass.getName();
      assert qName != null : aClass;
      PsiClass psiClass = facade.getResolveHelper().resolveReferencedClass(qName, this);
      if (!getManager().areElementsEquivalent(psiClass, aClass)) {
        throw cannotBindError(aClass, getKindEnum(containingFile));
      }
    }
    else if (facade.findClass(qName, getResolveScope()) == null && !preserveQualification) {
      return this;
    }
    else if (facade.getResolveHelper().resolveReferencedClass(qName, this) == null &&
             facade.getResolveHelper().resolveReferencedClass(StringUtil.getPackageName(qName), this) != null) {
      qName = aClass.getName();
      assert qName != null : aClass;
    }

    StringBuilder text = new StringBuilder(qName);
    PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList != null) {
      PsiElement cur = getReferenceNameElement();
      while (cur != parameterList) {
        assert cur != null : getText();
        cur = cur.getNextSibling();
        text.append(cur.getText());
      }
    }

    PsiJavaCodeReferenceElement ref;
    try {
      ref = facade.getParserFacade().createReferenceFromText(text.toString(), getParent());
    }
    catch (IncorrectOperationException e) {
      throw new IncorrectOperationException(e.getMessage() + " [qname=" + qName + " class=" + aClass + ";" + aClass.getClass().getName() + "]");
    }

    PsiReferenceParameterList refParameterList = ref.getParameterList();
    if (parameterList != null && refParameterList != null) {
      refParameterList.replace(parameterList);
    }

    getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());

    if (!preserveQualification) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      ref = (PsiJavaCodeReferenceElement)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.INCOMPLETE_CODE);
    }

    return ref;
  }

  @NotNull
  private List<PsiAnnotation> getAnnotations() {
    List<PsiAnnotation> annotations = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiAnnotation.class);

    if (!isQualified()) {
      PsiModifierList modifierList = PsiImplUtil.findNeighbourModifierList(this);
      if (modifierList != null) {
        annotations = new ArrayList<>(annotations);
        PsiImplUtil.collectTypeUseAnnotations(modifierList, annotations);
      }
    }

    return annotations;
  }

  private boolean isFullyQualified(@NotNull PsiFile containingFile) {
    Kind kind = getKindEnum(containingFile);
    switch (kind) {
      case CLASS_OR_PACKAGE_NAME_KIND:
        if (resolve() instanceof PsiPackage) return true;
        break;
      case CLASS_NAME_KIND:
      case CLASS_IN_QUALIFIED_NEW_KIND:
        break;

      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return true;

      default:
        LOG.error(kind);
        return true;
    }

    ASTNode qualifier = findChildByRole(ChildRole.QUALIFIER);
    if (qualifier == null) return false;

    IElementType qualifierElementType = qualifier.getElementType();
    LOG.assertTrue(qualifierElementType == JavaElementType.JAVA_CODE_REFERENCE, qualifierElementType);
    PsiElement refElement = SourceTreeToPsiMap.<PsiJavaCodeReferenceElement>treeToPsiNotNull(qualifier).resolve();
    if (refElement instanceof PsiPackage) return true;

    return SourceTreeToPsiMap.<PsiJavaCodeReferenceElementImpl>treeToPsiNotNull(qualifier).isFullyQualified(containingFile);
  }

  private PsiElement bindToPackage(@NotNull PsiPackage aPackage) throws IncorrectOperationException {
    final String qName = aPackage.getQualifiedName();
    if (qName.isEmpty()) {
      throw new IncorrectOperationException("Cannot bind to default package: "+aPackage);
    }
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
    final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(qName, getParent());
    getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
    return ref;
  }

  @Override
  public boolean isReferenceTo(@NotNull final PsiElement element) {
    PsiFile containingFile = getContainingFile();
    return isReferenceTo(element, containingFile);
  }

  private boolean isReferenceTo(PsiElement element, @NotNull PsiFile containingFile) {
    Kind kind = getKindEnum(containingFile);
    switch (kind) {
      case CLASS_NAME_KIND:
      case CLASS_IN_QUALIFIED_NEW_KIND:
        if (!(element instanceof PsiClass)) return false;
        break;

      case CLASS_FQ_NAME_KIND: {
        if (!(element instanceof PsiClass)) return false;
        final String qName = ((PsiClass)element).getQualifiedName();
        return qName != null && qName.equals(getCanonicalText(false, null, containingFile));
      }

      case PACKAGE_NAME_KIND: {
        if (!(element instanceof PsiPackage)) return false;
        final String qName = ((PsiPackage)element).getQualifiedName();
        return qName.equals(getCanonicalText(false, null, containingFile));
      }

      case CLASS_OR_PACKAGE_NAME_KIND:
        if (element instanceof PsiPackage) {
          final String qName = ((PsiPackage)element).getQualifiedName();
          return qName.equals(getCanonicalText(false, null, containingFile));
        }
        if (element instanceof PsiClass) {
          final PsiElement nameElement = getReferenceNameElement();
          if (nameElement == null) return false;
          final String name = ((PsiClass)element).getName();
          if (name == null) return false;
          return nameElement.textMatches(name) && containingFile.getManager().areElementsEquivalent(resolve(), element);
        }
        return false;

      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        if (element instanceof PsiClass) {
          final String qName = ((PsiClass)element).getQualifiedName();
          if (qName != null && qName.equals(getCanonicalText(false, null, containingFile))) {
            return !PsiUtil.isFromDefaultPackage((PsiClass)element) ||
                   PsiTreeUtil.getParentOfType(this, PsiImportStatementBase.class) == null;
          }
        }
        if (element instanceof PsiPackage) {
          final String qName = ((PsiPackage)element).getQualifiedName();
          return qName.equals(getCanonicalText(false, null, containingFile));
        }
        return false;
      default:
        LOG.error(kind);
        return true;
    }

    final ASTNode referenceNameElement = getReferenceNameNode();
    if (referenceNameElement == null || referenceNameElement.getElementType() != JavaTokenType.IDENTIFIER) return false;
    final String name = ((PsiClass)element).getName();
    return name != null && referenceNameElement.getText().equals(name) && containingFile.getManager().areElementsEquivalent(resolve(), element);
  }

  private String getNormalizedText() {
    String whiteSpaceAndComments = myCachedNormalizedText;
    if (whiteSpaceAndComments == null) {
      myCachedNormalizedText = whiteSpaceAndComments = JavaSourceUtil.getReferenceText(this);
    }
    return whiteSpaceAndComments;
  }

  @Override
  public String getClassNameText() {
    String cachedQName = myCachedQName;
    if (cachedQName == null) {
      myCachedQName = cachedQName = PsiNameHelper.getQualifiedClassName(getNormalizedText(), false);
    }
    return cachedQName;
  }

  @Override
  public void fullyQualify(@NotNull final PsiClass targetClass) {
    final Kind kind = getKindEnum(getContainingFile());
    if (kind != Kind.CLASS_NAME_KIND && kind != Kind.CLASS_OR_PACKAGE_NAME_KIND && kind != Kind.CLASS_IN_QUALIFIED_NEW_KIND) {
      LOG.error("Wrong kind " + kind);
      return;
    }
    JavaSourceUtil.fullyQualifyReference(this, targetClass);
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public PsiElement getQualifier() {
    return SourceTreeToPsiMap.treeElementToPsi(findChildByRole(ChildRole.QUALIFIER));
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCachedQName = null;
    myCachedNormalizedText = null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final ElementFilter filter;
    switch (getKindEnum(getContainingFile())) {
      case CLASS_OR_PACKAGE_NAME_KIND:
        filter = new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE);
        break;
      case CLASS_NAME_KIND:
        filter = ElementClassFilter.CLASS;
        break;
      case PACKAGE_NAME_KIND:
        filter = ElementClassFilter.PACKAGE;
        break;
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        filter = isQualified() ? new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE) : ElementClassFilter.PACKAGE;
        break;
      case CLASS_IN_QUALIFIED_NEW_KIND:
        filter = ElementClassFilter.CLASS;
        break;
      default:
        throw new RuntimeException("Unknown reference type");
    }

    return PsiImplUtil.getReferenceVariantsByFilter(this, filter);
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    List<ElementFilter> filters = new ArrayList<>();

    if (isInCode() && !(getParent() instanceof PsiImportStatement) && !(getParent() instanceof PsiReferenceList)) {
      filters.add(new AndFilter(ElementClassFilter.METHOD, new NotFilter(new ConstructorFilter())));
      filters.add(ElementClassFilter.VARIABLE);
    }

    switch (getKindEnum(getContainingFile())) {
      case CLASS_OR_PACKAGE_NAME_KIND:
        filters.add(ElementClassFilter.CLASS);
        filters.add(ElementClassFilter.PACKAGE);
        break;
      case CLASS_NAME_KIND:
        filters.add(ElementClassFilter.CLASS);
        if (isQualified() || PsiTreeUtil.getParentOfType(this, PsiJavaModule.class) != null) {
          filters.add(ElementClassFilter.PACKAGE);
        }
        break;
      case PACKAGE_NAME_KIND:
        filters.add(ElementClassFilter.PACKAGE);
        break;
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        filters.add(ElementClassFilter.PACKAGE);
        if (isQualified() || isCodeFragmentType(getTreeParent().getElementType())) {
          filters.add(ElementClassFilter.CLASS);
        }
        break;
      case CLASS_IN_QUALIFIED_NEW_KIND:
        PsiElement parent = getParent();
        if (parent instanceof PsiNewExpression) {
          PsiExpression qualifier = ((PsiNewExpression)parent).getQualifier();
          assert qualifier != null : parent;
          PsiType type = qualifier.getType();
          PsiClass aClass = PsiUtil.resolveClassInType(type);
          if (aClass != null) {
            AndFilter filter = new AndFilter(ElementClassFilter.CLASS, new ModifierFilter(PsiModifier.STATIC, false));
            aClass.processDeclarations(new FilterScopeProcessor(filter, processor), ResolveState.initial(), null, this);
          }
        }
        return;
      default:
        throw new RuntimeException("Unknown reference type");
    }

    OrFilter filter = new OrFilter(filters.toArray(ElementFilter.EMPTY_ARRAY));
    FilterScopeProcessor proc = new FilterScopeProcessor(filter, processor);

    for (PsiTypeParameter typeParameter : getUnfinishedMethodTypeParameters()) {
      if (!proc.execute(typeParameter, ResolveState.initial())) {
        return;
      }
    }

    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  private PsiTypeParameter[] getUnfinishedMethodTypeParameters() {
    ProcessingContext context = new ProcessingContext();
    if (psiElement().inside(
      psiElement(PsiTypeElement.class).afterLeaf(
        psiElement().withText(">").withParent(
          psiElement(PsiTypeParameterList.class).withParent(PsiErrorElement.class).save("typeParameterList")))).accepts(this, context)) {
      PsiTypeParameterList list = (PsiTypeParameterList)context.get("typeParameterList");
      PsiElement current = list.getParent().getParent();
      if (current instanceof PsiField) {
        current = current.getParent();
      }
      if (current instanceof PsiClass) {
        return list.getTypeParameters();
      }
    }
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  private boolean isInCode() {
    if (isCodeFragmentType(getTreeParent().getElementType()) || getParent() instanceof PsiAnnotation) {
      return false;
    }

    PsiElement superParent = getParent();
    while (superParent != null) {
      if (superParent instanceof PsiCodeBlock || superParent instanceof PsiLocalVariable) {
        return true;
      }
      if (superParent instanceof PsiClass || superParent instanceof PsiCatchSection) {
        return false;
      }
      superParent = superParent.getParent();
    }
    return false;
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return SourceTreeToPsiMap.treeElementToPsi(getReferenceNameNode());
  }

  @Nullable
  private ASTNode getReferenceNameNode() {
    return findChildByRole(ChildRole.REFERENCE_NAME);
  }


  @Override
  public PsiReferenceParameterList getParameterList() {
    return (PsiReferenceParameterList)findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  @Override
  public String getQualifiedName() {
    Kind kind = getKindEnum(getContainingFile());
    switch (kind) {
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
          LOG.assertTrue(target == null, target);
          return getClassNameText();
        }

      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return getNormalizedText(); // there cannot be any <...>

      default:
        LOG.error(kind);
        return null;
    }
  }

  @Override
  public String getReferenceName() {
    final ASTNode childByRole = getReferenceNameNode();
    if (childByRole == null) return null;
    return childByRole.getText();
  }

  @NotNull
  @Override
  public final TextRange getRangeInElement() {
    return calcRangeInElement(this);
  }

  @Override
  public PsiType @NotNull [] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList == null) return PsiType.EMPTY_ARRAY;
    return parameterList.getTypeArguments();
  }

  @NotNull
  @Override
  public final PsiElement getElement() {
    return this;
  }

  @Override
  public final void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public final String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }
}