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
package org.jetbrains.jps.javac.ast;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Used via reflection in {@link JavacTreeRefScanner#createASTScanner()}
 */
@SuppressWarnings("unused")
public class Javac8RefScanner extends JavacTreeRefScanner {
  @Override
  public Tree visitLambdaExpression(LambdaExpressionTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final TypeMirror type = refCollector.getType(node);
    Types types = refCollector.getTypeUtility();
    if (types != null) {
      final Element element = types.asElement(type);
      if (element != null) {
        final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
        if (ref != null) {
          refCollector.sinkDeclaration(new JavacDef.JavacFunExprDef(ref));
        }
      }
    }
    return super.visitLambdaExpression(node, refCollector);
  }

  @Override
  public Tree visitMemberReference(MemberReferenceTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element != null) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
      if (ref != null) {
        refCollector.sinkReference(ref);
      }
    }
    final TypeMirror type = refCollector.getType(node);
    if (type != null) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(refCollector.getTypeUtility().asElement(type));
      if (ref != null) {
        refCollector.sinkDeclaration(new JavacDef.JavacFunExprDef(ref));
      }
    }
    return super.visitMemberReference(node, refCollector);
  }
}
