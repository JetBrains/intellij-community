package org.jetbrains.jps.javac.ast;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * Used via reflection in {@link JavacTreeRefScanner#createASTScanner()}
 */
@SuppressWarnings("unused")
public class Javac8RefScanner extends JavacTreeRefScanner {
  @Override
  public Tree visitLambdaExpression(LambdaExpressionTree node, JavacReferenceCollectorListener.ReferenceCollector refCollector) {
    final TypeMirror type = refCollector.getType(node);
    final Element element = refCollector.getTypeUtility().asElement(type);
    if (element != null) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
      if (ref != null) {
        refCollector.sinkDeclaration(new JavacDef.JavacFunExprDef(ref));
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
