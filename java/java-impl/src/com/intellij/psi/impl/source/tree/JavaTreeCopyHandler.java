/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

public class JavaTreeCopyHandler implements TreeCopyHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.JavaTreeCopyHandler");

  private static final Key<Boolean> ALREADY_ESCAPED = new Key<>("ALREADY_ESCAPED");
  private static final Key<Boolean> ESCAPEMENT_ENGAGED = new Key<>("ESCAPEMENT_ENGAGED");
  private static final Key<Boolean> INTERFACE_MODIFIERS_FLAG_KEY = Key.create("INTERFACE_MODIFIERS_FLAG_KEY");

  @Override
  public TreeElement decodeInformation(TreeElement element, Map<Object, Object> decodingState) {
    boolean shallDecodeEscapedTexts = shallEncodeEscapedTexts(element, decodingState);
    if (element instanceof CompositeElement) {
      IElementType elementType = element.getElementType();
      if (elementType == JavaElementType.JAVA_CODE_REFERENCE ||
          elementType == JavaElementType.REFERENCE_EXPRESSION ||
          elementType == JavaElementType.METHOD_REF_EXPRESSION) {
        PsiJavaCodeReferenceElement ref = SourceTreeToPsiMap.treeToPsiNotNull(element);
        PsiClass refClass = element.getCopyableUserData(JavaTreeGenerator.REFERENCED_CLASS_KEY);
        if (refClass != null) {
          element.putCopyableUserData(JavaTreeGenerator.REFERENCED_CLASS_KEY, null);

          PsiManager manager = refClass.getManager();
          JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(refClass.getProject());
          PsiElement refElement = ref.resolve();
          try {
            if (refClass != refElement && !manager.areElementsEquivalent(refClass, refElement)) {
              if (((CompositeElement)element).findChildByRole(ChildRole.QUALIFIER) == null) {
                // can restore only if short (otherwise qualifier should be already restored)
                ref = (PsiJavaCodeReferenceElement)ref.bindToElement(refClass);
              }
            }
            else {
              // shorten references to the same package and to inner classes that can be accessed by short name
              ref = (PsiJavaCodeReferenceElement)codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.DO_NOT_ADD_IMPORTS);
            }
            return (TreeElement)SourceTreeToPsiMap.psiElementToTree(ref);
          }
          catch (IncorrectOperationException e) {
            ((PsiImportHolder)ref.getContainingFile()).importClass(refClass);
          }
        }
        else {
          PsiMember refMember = element.getCopyableUserData(JavaTreeGenerator.REFERENCED_MEMBER_KEY);
          if (refMember != null) {
            LOG.assertTrue(ref instanceof PsiReferenceExpression);
            element.putCopyableUserData(JavaTreeGenerator.REFERENCED_MEMBER_KEY, null);
            PsiElement refElement = ref.resolve();
            if (refMember != refElement && !refMember.getManager().areElementsEquivalent(refMember, refElement)) {
              PsiClass containingClass = refMember.getContainingClass();
              if (containingClass != null) {
                try {
                  ref = (PsiJavaCodeReferenceElement)((PsiReferenceExpression)ref).bindToElementViaStaticImport(containingClass);
                }
                catch (IncorrectOperationException e) {
                  // TODO[yole] ignore?
                }
              }
              return SourceTreeToPsiMap.psiToTreeNotNull(ref);
            }
          }
        }
      }
      else if (element.getElementType() == JavaElementType.MODIFIER_LIST) {
        if (element.getUserData(INTERFACE_MODIFIERS_FLAG_KEY) != null) {
          element.putUserData(INTERFACE_MODIFIERS_FLAG_KEY, null);
          try {
            PsiModifierList modifierList = SourceTreeToPsiMap.treeToPsiNotNull(element);
            if (element.getTreeParent().getElementType() == JavaElementType.FIELD) {
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
              modifierList.setModifierProperty(PsiModifier.STATIC, true);
              modifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
            else if (element.getTreeParent().getElementType() == JavaElementType.METHOD ||
                     element.getTreeParent().getElementType() == JavaElementType.ANNOTATION_METHOD) {
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
        String original = element.getText();
        String escaped = StringUtil.escapeXml(original);
        if (!Comparing.equal(original, escaped) && element.getCopyableUserData(ALREADY_ESCAPED) == null) {
          LeafElement copy = ((LeafElement)element).replaceWithText(escaped);
          copy.putCopyableUserData(ALREADY_ESCAPED, Boolean.TRUE);
          return copy;
        }
      }
    }

    return null;
  }

  private static boolean conversionMayApply(ASTNode element) {
    PsiElement psi = element.getPsi();
    if (psi == null || !psi.isValid()) return false;

    PsiFile file = psi.getContainingFile();
    Language baseLanguage = file.getViewProvider().getBaseLanguage();
    return baseLanguage == StdLanguages.JSPX && file.getLanguage() != baseLanguage;
  }

  @Override
  public void encodeInformation(TreeElement element, ASTNode original, Map<Object, Object> encodingState) {
    boolean shallEncodeEscapedTexts = shallEncodeEscapedTexts(original, encodingState);

    if (original instanceof CompositeElement) {
      IElementType originalType = original.getElementType();
      if (originalType == JavaElementType.JAVA_CODE_REFERENCE || originalType == JavaElementType.REFERENCE_EXPRESSION) {
        encodeInformationInRef(element, original);
      }
      else if (originalType == JavaElementType.MODIFIER_LIST) {
        ASTNode parent = original.getTreeParent();
        IElementType parentType = parent.getElementType();
        if (parentType == JavaElementType.FIELD ||
            parentType == JavaElementType.METHOD ||
            parentType == JavaElementType.ANNOTATION_METHOD) {
          ASTNode grand = parent.getTreeParent();
          if (grand.getElementType() == JavaElementType.CLASS &&
              (SourceTreeToPsiMap.<PsiClass>treeToPsiNotNull(grand).isInterface() ||
               SourceTreeToPsiMap.<PsiClass>treeToPsiNotNull(grand).isAnnotationType())) {
            element.putUserData(INTERFACE_MODIFIERS_FLAG_KEY, Boolean.TRUE);
          }
        }
      }
    }
    else if (shallEncodeEscapedTexts &&
             original instanceof LeafElement &&
             !(original instanceof OuterLanguageElement) &&
             !isInCData(original)) {
      String originalText = element.getText();
      String unescapedText = StringUtil.unescapeXml(originalText);
      if (!Comparing.equal(originalText, unescapedText)) {
        LeafElement replaced = ((LeafElement)element).rawReplaceWithText(unescapedText);
        element.putCopyableUserData(ALREADY_ESCAPED, null);
        replaced.putCopyableUserData(ALREADY_ESCAPED, null);
      }
    }
  }

  private static Boolean shallEncodeEscapedTexts(ASTNode original, Map<Object, Object> encodingState) {
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
        return leaf.getText().contains("<![CDATA[");
      }

      leaf = TreeUtil.prevLeaf(leaf);
    }

    return false;
  }

  private static void encodeInformationInRef(TreeElement ref, ASTNode original) {
    IElementType originalType = original.getElementType();
    if (originalType == JavaElementType.REFERENCE_EXPRESSION) {
      PsiJavaCodeReferenceElement javaRefElement = SourceTreeToPsiMap.treeToPsiNotNull(original);
      JavaResolveResult resolveResult = javaRefElement.advancedResolve(false);
      PsiElement target = resolveResult.getElement();
      if (target instanceof PsiClass &&
          (original.getTreeParent().getElementType() == JavaElementType.REFERENCE_EXPRESSION ||
           original.getTreeParent().getElementType() == JavaElementType.METHOD_REF_EXPRESSION)) {
        ref.putCopyableUserData(JavaTreeGenerator.REFERENCED_CLASS_KEY, (PsiClass)target);
      }
      else if ((target instanceof PsiMethod || target instanceof PsiField) &&
               ((PsiMember)target).hasModifierProperty(PsiModifier.STATIC) &&
               resolveResult.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        ref.putCopyableUserData(JavaTreeGenerator.REFERENCED_MEMBER_KEY, (PsiMember)target);
      }
    }
    else if (originalType == JavaElementType.JAVA_CODE_REFERENCE) {
      int kind = ((PsiJavaCodeReferenceElementImpl)original).getKind(((PsiJavaCodeReferenceElementImpl)original).getContainingFile());
      switch (kind) {
        case PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND:
          PsiElement target = SourceTreeToPsiMap.<PsiJavaCodeReferenceElement>treeToPsiNotNull(original).resolve();
          if (target instanceof PsiClass) {
            ref.putCopyableUserData(JavaTreeGenerator.REFERENCED_CLASS_KEY, (PsiClass)target);
          }
          break;

        case PsiJavaCodeReferenceElementImpl.PACKAGE_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND:
        case PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND:
          break;

        default:
          LOG.error("Unknown kind: " + kind);
      }
    }
    else {
      LOG.error("Wrong element type: " + originalType);
    }
  }
}