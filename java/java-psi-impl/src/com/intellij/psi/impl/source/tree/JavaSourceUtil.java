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

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.util.CharTable;

public class JavaSourceUtil {
  private JavaSourceUtil() { }

  public static void fullyQualifyReference(final CompositeElement reference, final PsiClass targetClass) {
    if (((SourceJavaCodeReference)reference).isQualified()) { // qualified reference
      final PsiClass parentClass = targetClass.getContainingClass();
      if (parentClass == null) return;
      final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
      if (qualifier instanceof SourceJavaCodeReference) {
        ((SourceJavaCodeReference)qualifier).fullyQualify(parentClass);
      }
    }
    else { // unqualified reference, need to qualify with package name
      final String qName = targetClass.getQualifiedName();
      if (qName == null) {
        return; // todo: local classes?
      }
      final int i = qName.lastIndexOf('.');
      if (i > 0) {
        final String prefix = qName.substring(0, i);
        final PsiManager manager = reference.getManager();
        final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject()).getParserFacade();

        final TreeElement qualifier;
        if (reference instanceof PsiReferenceExpression) {
          qualifier = (TreeElement)parserFacade.createExpressionFromText(prefix, null).getNode();
        }
        else {
          qualifier = (TreeElement)parserFacade.createReferenceFromText(prefix, null).getNode();
        }

        if (qualifier != null) {
          final CharTable systemCharTab = SharedImplUtil.findCharTableByTree(qualifier);
          final LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, systemCharTab, manager);
          qualifier.rawInsertAfterMe(dot);
          reference.addInternal(qualifier, dot, null, Boolean.FALSE);
        }
      }
    }
  }
}
