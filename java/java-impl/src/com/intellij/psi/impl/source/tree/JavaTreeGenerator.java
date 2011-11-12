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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ParseUtilBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class JavaTreeGenerator implements TreeGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.JavaTreeGenerator");

  private static final JavaParserUtil.ParserWrapper MOD_LIST = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      DeclarationParser.INSTANCE.parseModifierList(builder);
    }
  };

  @Override
  @Nullable
  public TreeElement generateTreeFor(PsiElement original, final CharTable table, final PsiManager manager) {
    if (original instanceof PsiKeyword || original instanceof PsiIdentifier) {
      final String text = original.getText();
      return createLeafFromText(text, table, manager, original, ((PsiJavaToken)original).getTokenType());
    }
    if (original instanceof PsiModifierList) {
      final String text = original.getText();
      assert text != null : "Text is null for " + original + "; " + original.getClass();
      final LanguageLevel level = PsiUtil.getLanguageLevel(original);
      final DummyHolder holder = DummyHolderFactory.createHolder(original.getManager(), new JavaDummyElement(text, MOD_LIST, level), null);
      final TreeElement modifierListElement = holder.getTreeElement().getFirstChildNode();
      if (CodeEditUtil.isNodeGenerated(original.getNode())) modifierListElement.acceptTree(new GeneratedMarkerVisitor());
      return modifierListElement;
    }
    if (original instanceof PsiReferenceExpression) {
      TreeElement element = createReferenceExpression(original.getProject(), original.getText(), original);
      PsiElement refElement = ((PsiJavaCodeReferenceElement)original).resolve();
      if (refElement instanceof PsiClass) {
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
      }
      return element;
    }
    if (original instanceof PsiJavaCodeReferenceElement) {
      PsiElement refElement = ((PsiJavaCodeReferenceElement)original).resolve();
      final boolean generated = refElement != null && CodeEditUtil.isNodeGenerated(refElement.getNode());
      if (refElement instanceof PsiClass) {
        if (refElement instanceof PsiAnonymousClass) {
          PsiJavaCodeReferenceElement ref = ((PsiAnonymousClass)refElement).getBaseClassReference();
          original = ref;
          refElement = ref.resolve();
        }

        boolean isFQ = false;
        if (original instanceof PsiJavaCodeReferenceElementImpl) {
          int kind = ((PsiJavaCodeReferenceElementImpl)original).getKind();
          switch (kind) {
            case PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND:
            case PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND:
            case PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND:
              isFQ = false;
              break;

            case PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND:
            case PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND:
              isFQ = true;
              break;

            default:
              LOG.assertTrue(false);
          }
        }

        final String text = isFQ ? ((PsiClass)refElement).getQualifiedName() : original.getText();
        final TreeElement element = createReference(original.getProject(), text, generated);
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
        return element;
      }
      return createReference(original.getProject(), original.getText(), generated);
    }
    if (original instanceof PsiCompiledElement) {
      PsiElement sourceVersion = original.getNavigationElement();
      if (sourceVersion != original) {
        return ChangeUtil.generateTreeElement(sourceVersion, table,manager);
      }
      ASTNode mirror = SourceTreeToPsiMap.psiElementToTree(((PsiCompiledElement)original).getMirror());
      return ChangeUtil.generateTreeElement(SourceTreeToPsiMap.treeElementToPsi(mirror), table,manager);
    }
    if (original instanceof PsiTypeElement) {
      final boolean generated = CodeEditUtil.isNodeGenerated(original.getNode());
      PsiTypeElement typeElement = (PsiTypeElement)original;
      PsiType type = typeElement.getType();
      if (type instanceof PsiEllipsisType) {
        TreeElement componentTypeCopy = ChangeUtil.generateTreeElement(
          new LightTypeElement(original.getManager(), ((PsiEllipsisType)type).getComponentType()),
          table,
          manager);
        if (componentTypeCopy == null) return null;
        CompositeElement element = ASTFactory.composite(JavaElementType.TYPE);
        CodeEditUtil.setNodeGenerated(element, generated);
        element.rawAddChildren(componentTypeCopy);
        element.rawAddChildren(createLeafFromText("...", table, manager, original, JavaTokenType.ELLIPSIS));
        return element;
      }
      if (type instanceof PsiArrayType) {
        TreeElement componentTypeCopy = ChangeUtil.generateTreeElement(
          new LightTypeElement(original.getManager(), ((PsiArrayType)type).getComponentType()),
          table,
          manager);
        if (componentTypeCopy == null) return null;
        CompositeElement element = ASTFactory.composite(JavaElementType.TYPE);
        CodeEditUtil.setNodeGenerated(element, generated);
        element.rawAddChildren(componentTypeCopy);
        element.rawAddChildren(createLeafFromText("[", table, manager, original, JavaTokenType.LBRACKET));
        element.rawAddChildren(createLeafFromText("]", table, manager, original, JavaTokenType.RBRACKET));
        return element;
      }
      if (type instanceof PsiPrimitiveType) {
        @NonNls String text = typeElement.getText();
        if (text.equals("null")) return null;
        Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
        lexer.start(text);
        TreeElement keyword = ParseUtilBase.createTokenElement(lexer, table);
        CodeEditUtil.setNodeGenerated(keyword, generated);
        CompositeElement element = ASTFactory.composite(JavaElementType.TYPE);
        CodeEditUtil.setNodeGenerated(element, generated);
        element.rawAddChildren(keyword);
        return element;
      }
      if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType || type instanceof PsiDisjunctionType) {
        final String originalText = original.getText();
        return createType(original.getProject(), originalText, null, generated);
      }
      if (type instanceof PsiIntersectionType) {
        LightTypeElement te = new LightTypeElement(original.getManager(), ((PsiIntersectionType)type).getRepresentative());
        return ChangeUtil.generateTreeElement(te, table, manager);
      }

      PsiClassType classType = (PsiClassType)type;

      String text = classType.getPresentableText();
      final TreeElement element = createType(original.getProject(), text, original, false);
      PsiTypeElementImpl result = SourceTreeToPsiMap.treeToPsiNotNull(element);

      CodeEditUtil.setNodeGenerated(result, generated);
      if (generated) {
        PsiJavaCodeReferenceElement ref = result.getInnermostComponentReferenceElement();
        if (ref != null) ((CompositeElement)ref.getNode()).acceptTree(new GeneratedMarkerVisitor());
      }
      encodeInfoInTypeElement(result, classType);
      return result;
    }
    return null;
  }

  private static LeafElement createLeafFromText(final String text,
                                                final CharTable table,
                                                final PsiManager manager,
                                                final PsiElement original,
                                                final IElementType type) {
    return Factory.createSingleLeafElement(type, text, 0, text.length(), table, manager, CodeEditUtil.isNodeGenerated(original.getNode()));
  }

  private static TreeElement createReference(final Project project, final String text, boolean mark) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
    final TreeElement element = (TreeElement)parserFacade.createReferenceFromText(text, null).getNode();
    if (mark) element.acceptTree(new GeneratedMarkerVisitor());
    return element;
  }

  private static TreeElement createReferenceExpression(final Project project, final String text, final PsiElement context) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
    final PsiExpression expression = parserFacade.createExpressionFromText(text, context);
    return (TreeElement)expression.getNode();
  }

  private static TreeElement createType(final Project project, final String text, final PsiElement context, final boolean mark) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
    final TreeElement element = (TreeElement)parserFacade.createTypeElementFromText(text, context).getNode();
    if (mark) element.acceptTree(new GeneratedMarkerVisitor());
    return element;
  }

  private static void encodeInfoInTypeElement(ASTNode typeElement, PsiType type) {
    if (type instanceof PsiPrimitiveType) return;
    LOG.assertTrue(typeElement.getElementType() == JavaElementType.TYPE);
    if (type instanceof PsiArrayType) {
      final ASTNode firstChild = typeElement.getFirstChildNode();
      LOG.assertTrue(firstChild.getElementType() == JavaElementType.TYPE);
      encodeInfoInTypeElement(firstChild, ((PsiArrayType)type).getComponentType());
    }
    else if (type instanceof PsiWildcardType) {
      final PsiType bound = ((PsiWildcardType)type).getBound();
      if (bound == null) return;
      final ASTNode lastChild = typeElement.getLastChildNode();
      if (lastChild.getElementType() != JavaElementType.TYPE) return;
      encodeInfoInTypeElement(lastChild, bound);
    }
    else if (type instanceof PsiCapturedWildcardType) {
      final PsiType bound = ((PsiCapturedWildcardType)type).getWildcard().getBound();
      if (bound == null) return;
      final ASTNode lastChild = typeElement.getLastChildNode();
      if (lastChild.getElementType() != JavaElementType.TYPE) return;
      encodeInfoInTypeElement(lastChild, bound);
    }
    else if (type instanceof PsiIntersectionType) {
      encodeInfoInTypeElement(typeElement, ((PsiIntersectionType)type).getRepresentative());
    }
    else {
      LOG.assertTrue(type instanceof PsiClassType);
      final PsiClassType classType = (PsiClassType)type;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass referencedClass = resolveResult.getElement();
      if (referencedClass == null) return;
      if (referencedClass instanceof PsiAnonymousClass) {
        encodeInfoInTypeElement(typeElement, ((PsiAnonymousClass)referencedClass).getBaseClassType());
      }
      else {
        final ASTNode reference = typeElement.findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
        // can be not the case for "? name"
        if (reference instanceof CompositeElement) {
          encodeClassTypeInfoInReference((CompositeElement)reference, resolveResult.getElement(), resolveResult.getSubstitutor());
        }
      }
    }
  }

  private static void encodeClassTypeInfoInReference(@NotNull CompositeElement reference, PsiClass referencedClass, PsiSubstitutor substitutor) {
    reference.putCopyableUserData(REFERENCED_CLASS_KEY, referencedClass);

    final PsiTypeParameter[] typeParameters = referencedClass.getTypeParameters();
    if (typeParameters.length == 0) return;

    final ASTNode referenceParameterList = reference.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    int index = 0;
    for (ASTNode child = referenceParameterList.getFirstChildNode(); child != null && index < typeParameters.length; child = child.getTreeNext()) {
      if (child.getElementType() == JavaElementType.TYPE) {
        final PsiType substitutedType = substitutor.substitute(typeParameters[index]);
        if (substitutedType != null) {
          encodeInfoInTypeElement(child, substitutedType);
        }
        index++;
      }
    }

    final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier != null) {
      if (referencedClass.hasModifierProperty(PsiModifier.STATIC)) return;
      final PsiClass outerClass = referencedClass.getContainingClass();
      if (outerClass != null) {
        encodeClassTypeInfoInReference((CompositeElement)qualifier, outerClass, substitutor);
      }
    }
  }

  static final Key<PsiClass> REFERENCED_CLASS_KEY = Key.create("REFERENCED_CLASS_KEY");
  static final Key<PsiMember> REFERENCED_MEMBER_KEY = Key.create("REFERENCED_MEMBER_KEY");
}
