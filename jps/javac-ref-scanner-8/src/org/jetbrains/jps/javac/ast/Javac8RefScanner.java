package org.jetbrains.jps.javac.ast;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import org.jetbrains.jps.javac.ast.api.JavacDefSymbol;

/**
 * Used via reflection in {@link JavacTreeRefScanner#createASTScanner()}
 */
@SuppressWarnings("unused")
public class Javac8RefScanner extends JavacTreeRefScanner {
  @Override
  public Tree visitLambdaExpression(LambdaExpressionTree node, JavacTreeScannerSink sink) {
    JCTree.JCLambda lambda = (JCTree.JCLambda)node;
    final Type type = lambda.type;
    final Symbol.TypeSymbol symbol = type.asElement();
    sink.sinkDeclaration(new JavacDefSymbol(symbol, Tree.Kind.LAMBDA_EXPRESSION, lambda.pos));
    return super.visitLambdaExpression(node, sink);
  }

  @Override
  public Tree visitMemberReference(MemberReferenceTree node, JavacTreeScannerSink sink) {
    JCTree.JCMemberReference memberRef = (JCTree.JCMemberReference)node;
    final Symbol methodSymbol = memberRef.sym;
    sink.sinkDeclaration(new JavacDefSymbol(methodSymbol, Tree.Kind.MEMBER_REFERENCE, memberRef.pos));
    return super.visitMemberReference(node, sink);
  }
}
