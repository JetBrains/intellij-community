package org.jetbrains.jps.javac.ast;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import org.jetbrains.jps.javac.ast.api.JavacDef;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * Used via reflection in {@link JavacTreeRefScanner#createASTScanner()}
 */
@SuppressWarnings("unused")
public class Javac8RefScanner extends JavacTreeRefScanner {
  @Override
  public Tree visitLambdaExpression(LambdaExpressionTree node, JavacTreeScannerSink sink) {
    final TypeMirror type = sink.getType(node);
    final Element element = sink.getTypeUtility().asElement(type);
    if (element != null) {
      sink.sinkDeclaration(new JavacDef.JavacFunExprDef(sink.asJavacRef(element)));
    }
    return super.visitLambdaExpression(node, sink);
  }

  @Override
  public Tree visitMemberReference(MemberReferenceTree node, JavacTreeScannerSink sink) {
    final Element element = sink.getReferencedElement(node);
    if (element != null) {
      sink.sinkReference(sink.asJavacRef(element));
    }
    final TypeMirror type = sink.getType(node);
    if (type != null) {
      sink.sinkDeclaration(new JavacDef.JavacFunExprDef(sink.asJavacRef(sink.getTypeUtility().asElement(type))));
    }
    return super.visitMemberReference(node, sink);
  }
}
