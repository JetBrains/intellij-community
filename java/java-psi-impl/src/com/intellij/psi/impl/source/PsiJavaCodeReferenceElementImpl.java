/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.VariableResolverProcessor;
import com.intellij.psi.impl.source.tree.*;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiJavaCodeReferenceElementImpl extends CompositePsiElement implements PsiJavaCodeReferenceElement, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl");

  private volatile String myCachedQName = null;
  private volatile String myCachedNormalizedText;
  private int myKindWhenDummy = CLASS_NAME_KIND;

  public static final int CLASS_NAME_KIND = 1;
  public static final int PACKAGE_NAME_KIND = 2;
  public static final int CLASS_OR_PACKAGE_NAME_KIND = 3;
  public static final int CLASS_FQ_NAME_KIND = 4;
  public static final int CLASS_FQ_OR_PACKAGE_NAME_KIND = 5;
  public static final int CLASS_IN_QUALIFIED_NEW_KIND = 6;

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

  public void setKindWhenDummy(final int kind) {
    LOG.assertTrue(isDummy(getTreeParent().getElementType()));
    myKindWhenDummy = kind;
  }

  private static boolean isDummy(final IElementType type) {
    return type == TokenType.DUMMY_HOLDER || type == JavaElementType.DUMMY_ELEMENT;
  }

  public int getKind() {
    PsiUtilCore.ensureValid(this);
    CompositeElement treeParent = getTreeParent();
    IElementType i = treeParent.getElementType();
    if (isDummy(i)) {
      return myKindWhenDummy;
    }
    if (i == JavaElementType.TYPE) {
      return treeParent.getTreeParent().getPsi() instanceof PsiTypeCodeFragment ? CLASS_OR_PACKAGE_NAME_KIND : CLASS_NAME_KIND;
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
        i == JavaElementType.ANNOTATION) {
      if (isQualified()) {
        return CLASS_OR_PACKAGE_NAME_KIND;
      }
      return CLASS_NAME_KIND;
    }
    if (i == JavaElementType.NEW_EXPRESSION) {
      final ASTNode qualifier = treeParent.findChildByRole(ChildRole.QUALIFIER);
      return qualifier != null ? CLASS_IN_QUALIFIED_NEW_KIND : CLASS_NAME_KIND;
    }
    if (i == JavaElementType.ANONYMOUS_CLASS) {
      if (treeParent.getChildRole(this) == ChildRole.BASE_CLASS_REFERENCE) {
        LOG.assertTrue(treeParent.getTreeParent().getElementType() == JavaElementType.NEW_EXPRESSION);
        final ASTNode qualifier = treeParent.getTreeParent().findChildByRole(ChildRole.QUALIFIER);
        return qualifier != null ? CLASS_IN_QUALIFIED_NEW_KIND : CLASS_NAME_KIND;
      }
      else {
        return CLASS_OR_PACKAGE_NAME_KIND; // incomplete code
      }
    }
    if (i == JavaElementType.PACKAGE_STATEMENT) {
      return PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATEMENT) {
      boolean isOnDemand = SourceTreeToPsiMap.<PsiImportStatement>treeToPsiNotNull(treeParent).isOnDemand();
      return isOnDemand ? CLASS_FQ_OR_PACKAGE_NAME_KIND : CLASS_FQ_NAME_KIND;
    }
    if (i == JavaElementType.IMPORT_STATIC_STATEMENT) {
      return CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      int parentKind = ((PsiJavaCodeReferenceElementImpl)treeParent).getKind();
      if (parentKind == CLASS_NAME_KIND) {
        return CLASS_OR_PACKAGE_NAME_KIND;
      }
      if (parentKind == CLASS_FQ_NAME_KIND) {
        return CLASS_FQ_OR_PACKAGE_NAME_KIND;
      }
      return parentKind;
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
      PsiDocComment docComment = PsiTreeUtil.getParentOfType(this, PsiDocComment.class);
      if (docComment != null && docComment.getOwner() == null && docComment.getParent() instanceof PsiJavaFile) {
        return CLASS_FQ_OR_PACKAGE_NAME_KIND;
      }

      return CLASS_OR_PACKAGE_NAME_KIND;
    }
    if (isCodeFragmentType(i)) {
      PsiJavaCodeReferenceCodeFragment fragment = (PsiJavaCodeReferenceCodeFragment)treeParent.getPsi();
      return fragment.isClassesAccepted() ? CLASS_FQ_OR_PACKAGE_NAME_KIND : PACKAGE_NAME_KIND;
    }

    diagnoseUnknownParent();
    return CLASS_NAME_KIND;
  }

  private void diagnoseUnknownParent() {
    CompositeElement parent = getTreeParent();
    IElementType i = parent.getElementType();
    String message = "Unknown parent for java code reference: '" + parent + "'; Type: " + i + ";\n";
    while (parent != null && parent.getPsi() instanceof PsiExpression) {
      parent = parent.getTreeParent();
      message += " Parent: '" + parent+"'; \n";
    }
    if (parent != null) {
      message += DebugUtil.treeToString(parent, false);
    }
    LOG.error(message);
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

      List<PsiAnnotation> annotations = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiAnnotation.class);
      setAnnotations(annotations);

      return;
    }

    super.deleteChildInternal(child);
  }

  @Override
  public final ASTNode findChildByRole(final int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      case ChildRole.REFERENCE_NAME:
        return TreeUtil.findChildBackward(this, JavaTokenType.IDENTIFIER);

      case ChildRole.REFERENCE_PARAMETER_LIST: {
        TreeElement lastChild = getLastChildNode();
        return lastChild.getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST ? lastChild : null;
      }

      case ChildRole.QUALIFIER:
        return findChildByType(JavaElementType.JAVA_CODE_REFERENCE);

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);
    }

    return null;
  }

  @Override
  public final int getChildRole(final ASTNode child) {
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
          if (types.length == 0) {
            final PsiElement qualifier = getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement) {
              return StringUtil.getQualifiedName(((PsiJavaCodeReferenceElement)qualifier).getCanonicalText(), aClass.getName());
            }
            return name;
          }

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
          LOG.assertTrue(target == null, target);
          return getNormalizedText();
        }
      case PACKAGE_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        return getNormalizedText();

      default:
        LOG.assertTrue(false);
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

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantResolver<PsiJavaReference> {
    private static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    @NotNull
    @Override
    public JavaResolveResult[] resolve(@NotNull PsiJavaReference ref, boolean incompleteCode) {
      PsiJavaCodeReferenceElementImpl referenceElement = (PsiJavaCodeReferenceElementImpl)ref;
      int kind = referenceElement.getKind();
      PsiFile containingFile = referenceElement.getContainingFile();
      JavaResolveResult[] result = referenceElement.resolve(kind, containingFile);

      if (incompleteCode && result.length == 0 && kind != CLASS_FQ_NAME_KIND && kind != CLASS_FQ_OR_PACKAGE_NAME_KIND) {
        VariableResolverProcessor processor = new VariableResolverProcessor(referenceElement, containingFile);
        PsiScopesUtil.resolveAndWalk(processor, referenceElement, null, incompleteCode);
        result = processor.getResult();
        if (result.length == 0 && kind == CLASS_NAME_KIND) {
          result = referenceElement.resolve(PACKAGE_NAME_KIND, containingFile);
        }
      }

      JavaResolveUtil.substituteResults((PsiJavaCodeReferenceElement)ref, result);

      return result;
    }
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(final boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolve(final boolean incompleteCode) {
    FileElement fileElement = SharedImplUtil.findFileElement(this);
    if (fileElement == null) {
      LOG.error("fileElement == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    final PsiManagerEx manager = fileElement.getManager();
    if (manager == null) {
      LOG.error("getManager() == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    PsiFile file = SharedImplUtil.getContainingFile(fileElement);
    boolean valid = file != null && file.isValid();
    if (!valid) {
      LOG.error("invalid!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    Project project = manager.getProject();

    final ResolveCache resolveCache = ResolveCache.getInstance(project);
    final ResolveResult[] results = resolveCache.resolveWithCaching(this, OurGenericsResolver.INSTANCE, true, incompleteCode, file);
    return results.length == 0 ? JavaResolveResult.EMPTY_ARRAY : (JavaResolveResult[])results;
  }

  private PsiSubstitutor updateSubstitutor(PsiSubstitutor subst, final PsiClass psiClass) {
    final PsiType[] parameters = getTypeParameters();
    if (psiClass != null) {
      subst = subst.putAll(psiClass, parameters);
    }
    return subst;
  }

  private JavaResolveResult[] resolve(final int kind, PsiFile containingFile) {
    switch (kind) {
      case CLASS_FQ_NAME_KIND: {
        // TODO: support type parameters in FQ names
        String text = getNormalizedText();
        if (StringUtil.isEmptyOrSpaces(text)) return JavaResolveResult.EMPTY_ARRAY;

        PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(text, getResolveScope());
        if (aClass == null) return JavaResolveResult.EMPTY_ARRAY;

        if (!isQualified() && text.equals(aClass.getQualifiedName())) {
          if (containingFile instanceof PsiJavaFile && !((PsiJavaFile)containingFile).getPackageName().isEmpty()) {
            // classes in default (unnamed) package cannot be referenced from other packages
            return JavaResolveResult.EMPTY_ARRAY;
          }
        }

        return new JavaResolveResult[]{new CandidateInfo(aClass, updateSubstitutor(PsiSubstitutor.EMPTY, aClass), this, false)};
      }
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
          LOG.error("Invalid java reference!");
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
      case CLASS_NAME_KIND: {
        final PsiElement classNameElement = getReferenceNameElement();
        if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
        final String className = classNameElement.getText();
        final ClassResolverProcessor processor = new ClassResolverProcessor(className, this, containingFile);
        PsiScopesUtil.resolveAndWalk(processor, this, null);
        return processor.getResult();
      }
      case PACKAGE_NAME_KIND: {
        String packageName = getNormalizedText();
        Project project = getManager().getProject();
        PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
        if (aPackage == null || !aPackage.isValid()) {
          return JavaPsiFacade.getInstance(project).isPartOfPackagePrefix(packageName) ?
                 CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE : JavaResolveResult.EMPTY_ARRAY;
        }
        return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY)};
      }
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
      case CLASS_OR_PACKAGE_NAME_KIND: {
        int classKind = kind == CLASS_OR_PACKAGE_NAME_KIND ? CLASS_NAME_KIND : CLASS_FQ_NAME_KIND;
        JavaResolveResult[] result = resolve(classKind,containingFile);

        if (result.length == 1 && !result[0].isAccessible()) {
          JavaResolveResult[] packageResult = resolve(PACKAGE_NAME_KIND,containingFile);
          if (packageResult.length != 0) {
            result = packageResult;
          }
        }
        else if (result.length == 0) {
          result = resolve(PACKAGE_NAME_KIND,containingFile);
        }

        return result;
      }
    }

    LOG.error(this);
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public final PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    final PsiElement oldIdentifier = getReferenceNameElement();
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final PsiElement identifier = JavaPsiFacade.getInstance(getProject()).getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
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
          final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
          final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(name, getParent());
          getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
          return ref;
        }
        else {
          throw cannotBindError(element);
        }

      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  private static IncorrectOperationException cannotBindError(PsiElement element) {
    return new IncorrectOperationException("Cannot bind to " + element);
  }

  private PsiElement bindToClass(PsiClass aClass) throws IncorrectOperationException {
    String qName = aClass.getQualifiedName();
    boolean preserveQualification = JavaCodeStyleSettingsFacade.getInstance(getProject()).useFQClassNames() && isFullyQualified();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    if (qName == null) {
      qName = aClass.getName();
      PsiClass psiClass = facade.getResolveHelper().resolveReferencedClass(qName, this);
      if (!getManager().areElementsEquivalent(psiClass, aClass)) {
        throw cannotBindError(aClass);
      }
    }
    else if (facade.findClass(qName, getResolveScope()) == null && !preserveQualification) {
      return this;
    }

    List<PsiAnnotation> annotations = getAnnotations();
    String text = qName;
    PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList != null) {
      text += parameterList.getText();
    }
    PsiJavaCodeReferenceElement ref = facade.getParserFacade().createReferenceFromText(text, getParent());
    getTreeParent().replaceChildInternal(this, (TreeElement)ref.getNode());
    ((PsiJavaCodeReferenceElementImpl)ref).setAnnotations(annotations);

    if (!preserveQualification) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(aClass.getProject());
      ref = (PsiJavaCodeReferenceElement)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.UNCOMPLETE_CODE);
    }

    return ref;
  }

  private List<PsiAnnotation> getAnnotations() {
    List<PsiAnnotation> annotations = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiAnnotation.class);

    if (!isQualified()) {
      PsiModifierList modifierList = PsiImplUtil.findNeighbourModifierList(this);
      if (modifierList != null) {
        List<PsiAnnotation> typeAnnotations = PsiImplUtil.getTypeUseAnnotations(modifierList);
        if (typeAnnotations != null && typeAnnotations.size() > 0) {
          annotations.addAll(typeAnnotations);
        }
      }
    }

    return annotations;
  }

  private void setAnnotations(List<PsiAnnotation> annotations) {
    if (annotations.isEmpty()) return;

    PsiElement newParent = this;
    PsiElement anchor = SourceTreeToPsiMap.treeElementToPsi(findChildByType(JavaTokenType.DOT));
    if (anchor == null) {
      PsiModifierList modifierList = PsiImplUtil.findNeighbourModifierList(this);
      if (modifierList != null) {
        newParent = modifierList;
      }
    }

    for (PsiAnnotation annotation : annotations) {
      if (annotation.getParent() != newParent) {
        newParent.addAfter(annotation, anchor);
        annotation.delete();
      }
    }
  }

  private boolean isFullyQualified() {
    switch (getKind()) {
      case CLASS_OR_PACKAGE_NAME_KIND:
        if (resolve() instanceof PsiPackage) return true;
        //noinspection fallthrough
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
    final PsiElement refElement = SourceTreeToPsiMap.<PsiJavaCodeReferenceElement>treeToPsiNotNull(qualifier).resolve();
    if (refElement instanceof PsiPackage) return true;

    return SourceTreeToPsiMap.<PsiJavaCodeReferenceElementImpl>treeToPsiNotNull(qualifier).isFullyQualified();
  }

  private PsiElement bindToPackage(final PsiPackage aPackage) throws IncorrectOperationException {
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

  private String getNormalizedText() {
    String whiteSpaceAndComments = myCachedNormalizedText;
    if (whiteSpaceAndComments == null) {
      myCachedNormalizedText = whiteSpaceAndComments = SourceUtil.getReferenceText(this);
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
  public void fullyQualify(final PsiClass targetClass) {
    final int kind = getKind();
    if (kind != CLASS_NAME_KIND && kind != CLASS_OR_PACKAGE_NAME_KIND && kind != CLASS_IN_QUALIFIED_NEW_KIND) {
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
  @NotNull
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

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void processVariants(@NotNull final PsiScopeProcessor processor) {
    final OrFilter filter = new OrFilter();
    if (isInCode() && !(getParent() instanceof PsiImportStatement) && !(getParent() instanceof PsiReferenceList)) {
      filter.addFilter(new AndFilter(ElementClassFilter.METHOD, new NotFilter(new ConstructorFilter())));
      filter.addFilter(ElementClassFilter.VARIABLE);
    }
    switch (getKind()) {
      case CLASS_OR_PACKAGE_NAME_KIND:
        filter.addFilter(ElementClassFilter.CLASS);
        filter.addFilter(ElementClassFilter.PACKAGE_FILTER);
        break;
      case CLASS_NAME_KIND:
        filter.addFilter(ElementClassFilter.CLASS);
        if (isQualified()) {
          filter.addFilter(ElementClassFilter.PACKAGE_FILTER);
        }
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
          PsiExpression qualifier = ((PsiNewExpression)parent).getQualifier();
          assert qualifier != null : parent;
          PsiType type = qualifier.getType();
          PsiClass aClass = PsiUtil.resolveClassInType(type);
          if (aClass != null) {
            aClass.processDeclarations(
              new FilterScopeProcessor(new AndFilter(ElementClassFilter.CLASS, new ModifierFilter(PsiModifier.STATIC, false)),
                                       processor), ResolveState.initial(), null, this);
          }
        }
        return;
      default:
        throw new RuntimeException("Unknown reference type");
    }
    final FilterScopeProcessor proc = new FilterScopeProcessor(filter, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  private boolean isInCode() {
    if (isCodeFragmentType(getTreeParent().getElementType()) || getParent() instanceof PsiAnnotation) {
      return false;
    }

    if (isQualified()) {
      return true;
    }

    PsiElement superParent = getParent();
    while (superParent != null) {
      if (superParent instanceof PsiCodeBlock || superParent instanceof PsiLocalVariable) {
        return true;
      }
      if (superParent instanceof PsiClass) {
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
        return getNormalizedText(); // there cannot be any <...>

      default:
        LOG.assertTrue(false);
        return null;
    }

  }

  @Override
  public String getReferenceName() {
    final ASTNode childByRole = getReferenceNameNode();
    if (childByRole == null) return null;
    return childByRole.getText();
  }

  @Override
  public final TextRange getRangeInElement() {
    final TreeElement nameChild = (TreeElement)getReferenceNameNode();
    if (nameChild == null) return new TextRange(0, getTextLength());
    final int startOffset = nameChild.getStartOffsetInParent();
    return new TextRange(startOffset, startOffset + nameChild.getTextLength());
  }

  @Override
  @NotNull
  public PsiType[] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList == null) return PsiType.EMPTY_ARRAY;
    return parameterList.getTypeArguments();

  }

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

  public final String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }
}
