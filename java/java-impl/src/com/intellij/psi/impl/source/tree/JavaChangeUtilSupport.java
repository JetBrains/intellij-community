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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JavaChangeUtilSupport implements TreeGenerator, TreeCopyHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.JavaTreeGenerator");

  static {
    JavaChangeUtilSupport instance = new JavaChangeUtilSupport();
    ChangeUtil.registerCopyHandler(instance);
    ChangeUtil.registerTreeGenerator(instance);
  }

  public static void setup() {
    // Does nothing, initialization is done in class initializer.
  }

  @Nullable
  public TreeElement generateTreeFor(PsiElement original, final CharTable table, final PsiManager manager) {
    if (original instanceof PsiKeyword || original instanceof PsiIdentifier) {
      final String text = original.getText();
      return createLeafFromText(text, table, manager, original, ((PsiJavaToken)original).getTokenType());
    }
    if (original instanceof PsiModifierList) {
      final String text = original.getText();
      assert text != null : "Text is null for " + original + "; " + original.getClass();
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(original);
      JavaLexer lexer = new JavaLexer(languageLevel);
      lexer.start(text);
      TreeElement modifierListElement = new JavaParsingContext(table, languageLevel).getDeclarationParsing().parseModifierList(lexer);
      if (CodeEditUtil.isNodeGenerated(original.getNode())) modifierListElement.acceptTree(new GeneratedMarkerVisitor());
      return modifierListElement;
    }
    if (original instanceof PsiReferenceExpression) {
      TreeElement element = createReferenceExpression(original.getManager(), original.getText(), table);
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

        String text = isFQ ? ((PsiClass)refElement).getQualifiedName() : original.getText();
        TreeElement element = createReference(original.getManager(), text, table, generated);
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
        return element;
      }
      return createReference(original.getManager(), original.getText(), table, generated);
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
        TreeElement keyword = ParseUtil.createTokenElement(lexer, table);
        CodeEditUtil.setNodeGenerated(keyword, generated);
        CompositeElement element = ASTFactory.composite(JavaElementType.TYPE);
        CodeEditUtil.setNodeGenerated(element, generated);
        element.rawAddChildren(keyword);
        return element;
      }
      if (type instanceof PsiWildcardType) {
        String originalText = original.getText();
        final CompositeElement element = Parsing.parseTypeText(original.getManager(), originalText, 0, originalText.length(), table);
        if(generated) element.getTreeParent().acceptTree(new GeneratedMarkerVisitor());
        return element;
      }
      if (type instanceof PsiIntersectionType) {
        PsiIntersectionType intersectionType = (PsiIntersectionType)type;
        LightTypeElement te = new LightTypeElement(original.getManager(), intersectionType.getConjuncts()[0]);
        return ChangeUtil.generateTreeElement(te, table, manager);
      }
      PsiClassType classType = (PsiClassType)type;

      final FileElement holderElement = DummyHolderFactory.createHolder(manager, original).getTreeElement();
      String text = classType.getPresentableText();
      CompositeElement fromT = Parsing.parseTypeText(manager, text, 0, text.length(), holderElement.getCharTable());
      holderElement.rawAddChildren(fromT);
      PsiTypeElementImpl result = (PsiTypeElementImpl)SourceTreeToPsiMap.treeElementToPsi(fromT);

      CodeEditUtil.setNodeGenerated(result, generated);
      if(generated) {
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

  private static CompositeElement createReference(PsiManager manager, String text, CharTable table, boolean generatedFlag) {
    final CompositeElement element = Parsing.parseJavaCodeReferenceText(manager, text, table);
    if (element == null) {
      throw new IllegalArgumentException("Failed to create reference element from text '" + text + "'");
    }
    if(generatedFlag) element.acceptTree(new GeneratedMarkerVisitor());
    return element;
  }

  private static TreeElement createReferenceExpression(PsiManager manager, String text, CharTable table) {
    return ExpressionParsing.parseExpressionText(manager, text, 0, text.length(), table);
  }

  public TreeElement decodeInformation(TreeElement element, final Map<Object, Object> decodingState) {
    boolean shallDecodeEscapedTexts = shallEncodeEscapedTexts(element, decodingState);
    if (element instanceof CompositeElement) {
      if (element.getElementType() == JavaElementType.JAVA_CODE_REFERENCE ||
          element.getElementType() == JavaElementType.REFERENCE_EXPRESSION) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(element);
        final PsiClass refClass = element.getCopyableUserData(REFERENCED_CLASS_KEY);
        if (refClass != null) {
          element.putCopyableUserData(REFERENCED_CLASS_KEY, null);

          PsiManager manager = refClass.getManager();
          JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(refClass.getProject());
          PsiElement refElement1 = ref.resolve();
          try {
            if (refClass != refElement1 && !manager.areElementsEquivalent(refClass, refElement1)) {
              if (((CompositeElement)element).findChildByRole(ChildRole.QUALIFIER) == null) {
                // can restore only if short (otherwise qualifier should be already restored)
                ref = (PsiJavaCodeReferenceElement)ref.bindToElement(refClass);
              }
            }
            else {
              // shorten references to the same package and to inner classes that can be accessed by short name
              ref = (PsiJavaCodeReferenceElement)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.DO_NOT_ADD_IMPORTS);
            }
            element = (TreeElement)SourceTreeToPsiMap.psiElementToTree(ref);
          }
          catch (IncorrectOperationException e) {
            ((PsiImportHolder) ref.getContainingFile()).importClass(refClass);
          }
        }
        else {
          final PsiMember refMember = element.getCopyableUserData(REFERENCED_MEMBER_KEY);
          if (refMember != null) {
            LOG.assertTrue(ref instanceof PsiReferenceExpression);
            element.putCopyableUserData(REFERENCED_MEMBER_KEY, null);
            PsiElement refElement1 = ref.resolve();
            if (refMember != refElement1 && !refMember.getManager().areElementsEquivalent(refMember, refElement1)) {
              try {
                ref = (PsiJavaCodeReferenceElement) ((PsiReferenceExpression)ref).bindToElementViaStaticImport(refMember.getContainingClass());
              }
              catch (IncorrectOperationException e) {
                // TODO[yole] ignore?
              }
              element = (TreeElement)SourceTreeToPsiMap.psiElementToTree(ref);
            }
          }
        }
      }
      else if (element.getElementType() == JavaElementType.MODIFIER_LIST) {
        if (element.getUserData(INTERFACE_MODIFIERS_FLAG_KEY) != null) {
          element.putUserData(INTERFACE_MODIFIERS_FLAG_KEY, null);
          try {
            PsiModifierList modifierList = (PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(element);
            if (element.getTreeParent().getElementType() == JavaElementType.FIELD) {
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
              modifierList.setModifierProperty(PsiModifier.STATIC, true);
              modifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
            else if (element.getTreeParent().getElementType() == JavaElementType.METHOD) {
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
              modifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
            }
            else if (element.getTreeParent().getElementType() == JavaElementType.ANNOTATION_METHOD) {
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
              modifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }
    else if (shallDecodeEscapedTexts && element instanceof LeafElement && !(element instanceof OuterLanguageElement)) {
      if (!isInCData(element)) {
        final String original = element.getText();
        final String escaped = StringUtil.escapeXml(original);
        if (!Comparing.equal(original, escaped) && element.getCopyableUserData(ALREADY_ESCAPED) == null) {
          LeafElement copy = ((LeafElement)element).replaceWithText(escaped);
          copy.putCopyableUserData(ALREADY_ESCAPED, Boolean.TRUE);
          return copy;
        }
      }
    }

    return element;
  }

  private static final Key<Boolean> ALREADY_ESCAPED = new Key<Boolean>("ALREADY_ESCAPED");
  private static final Key<Boolean> ESCAPEMENT_ENGAGED = new Key<Boolean>("ESCAPEMENT_ENGAGED");
  private static boolean conversionMayApply(ASTNode element) {
    PsiElement psi = element.getPsi();
    if (psi == null || !psi.isValid()) return false;

    final PsiFile file = psi.getContainingFile();
    final Language baseLanguage = file.getViewProvider().getBaseLanguage();
    return baseLanguage == StdLanguages.JSPX && file.getLanguage() != baseLanguage;
  }


  public void encodeInformation(final TreeElement element, final ASTNode original, final Map<Object, Object> encodingState) {
    boolean shallEncodeEscapedTexts = shallEncodeEscapedTexts(original, encodingState);

    if (original instanceof CompositeElement) {
      if (original.getElementType() == JavaElementType.JAVA_CODE_REFERENCE || original.getElementType() == JavaElementType.REFERENCE_EXPRESSION) {
        encodeInformationInRef(element, original);
      }
      else if (original.getElementType() == JavaElementType.MODIFIER_LIST
               && (original.getTreeParent().getElementType() == JavaElementType.FIELD || original.getTreeParent().getElementType() == JavaElementType.METHOD || original.getTreeParent().getElementType() == JavaElementType.ANNOTATION_METHOD)
               && original.getTreeParent().getTreeParent().getElementType() == JavaElementType.CLASS
               && (((PsiClass)SourceTreeToPsiMap.treeElementToPsi(original.getTreeParent().getTreeParent())).isInterface()
                   || ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(original.getTreeParent().getTreeParent())).isAnnotationType())) {
        element.putUserData(INTERFACE_MODIFIERS_FLAG_KEY, Boolean.TRUE);
      }
    }
    else if (shallEncodeEscapedTexts && original instanceof LeafElement && !(original instanceof OuterLanguageElement)) {
      if (!isInCData(original)) {
        final String originalText = element.getText();
        final String unescapedText = StringUtil.unescapeXml(originalText);
        if (!Comparing.equal(originalText, unescapedText)) {
          LeafElement replaced = ((LeafElement)element).rawReplaceWithText(unescapedText);
          element.putCopyableUserData(ALREADY_ESCAPED, null);
          replaced.putCopyableUserData(ALREADY_ESCAPED, null);
        }
      }
    }
  }

  private static Boolean shallEncodeEscapedTexts(final ASTNode original, final Map<Object, Object> encodingState) {
    Boolean shallEncodeEscapedTexts = (Boolean)encodingState.get(ESCAPEMENT_ENGAGED);
    if (shallEncodeEscapedTexts == null) {
      shallEncodeEscapedTexts = conversionMayApply(original);
      encodingState.put(ESCAPEMENT_ENGAGED, shallEncodeEscapedTexts);
    }
    return shallEncodeEscapedTexts;
  }

  private static boolean isInCData(ASTNode element) {
    ASTNode leaf = element;
    while (leaf != null) {
      if (leaf instanceof OuterLanguageElement) {
        return leaf.getText().indexOf("<![CDATA[") >= 0;
      }

      leaf = TreeUtil.prevLeaf(leaf);
    }

    return false;
  }

  private static void encodeInformationInRef(TreeElement ref, ASTNode original) {
    if (original.getElementType() == JavaElementType.REFERENCE_EXPRESSION) {
      final PsiJavaCodeReferenceElement javaRefElement = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(original);
      assert javaRefElement != null;
      final JavaResolveResult resolveResult = javaRefElement.advancedResolve(false);
      final PsiElement target = resolveResult.getElement();
      if (target instanceof PsiClass &&
          original.getTreeParent().getElementType() == JavaElementType.REFERENCE_EXPRESSION) {
        ref.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)target);
      }
      else if ((target instanceof PsiMethod || target instanceof PsiField) &&
               ((PsiMember) target).hasModifierProperty(PsiModifier.STATIC) &&
                resolveResult.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        ref.putCopyableUserData(REFERENCED_MEMBER_KEY, (PsiMember) target);
      }
    }
    else if (original.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
      switch (((PsiJavaCodeReferenceElementImpl)original).getKind()) {
      case PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND:
      case PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND:
      case PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND:
        final PsiElement target = ((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(original)).resolve();
        if (target instanceof PsiClass) {
          ref.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)target);
        }
        break;

      case PsiJavaCodeReferenceElementImpl.PACKAGE_NAME_KIND:
      case PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND:
      case PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND:
             break;

      default:
             LOG.assertTrue(false);
      }
    }
    else {
      LOG.error("Wrong element type: " + original.getElementType());
    }
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

  private static final Key<PsiClass> REFERENCED_CLASS_KEY = Key.create("REFERENCED_CLASS_KEY");
  private static final Key<PsiMember> REFERENCED_MEMBER_KEY = Key.create("REFERENCED_MEMBER_KEY");
  private static final Key<Boolean> INTERFACE_MODIFIERS_FLAG_KEY = Key.create("INTERFACE_MODIFIERS_FLAG_KEY");
}
