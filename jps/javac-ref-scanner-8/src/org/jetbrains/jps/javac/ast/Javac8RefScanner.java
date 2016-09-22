package org.jetbrains.jps.javac.ast;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

/**
 * Used via reflection in {@link JavacTreeRefScanner#createASTScanner()}
 */
@SuppressWarnings("unused")
public class Javac8RefScanner extends JavacTreeRefScanner {
  @Override
  public Tree visitLambdaExpression(LambdaExpressionTree node, JavacTreeScannerSink sink) {
    final Type type = ((JCTree.JCLambda)node).type;
    final Symbol.TypeSymbol symbol = type.asElement();
    sink.sinkReference(new JavacRefSymbol(symbol, Tree.Kind.LAMBDA_EXPRESSION));
    //for (Symbol member : symbol.members().getElements()) {
    //  sink.mySymbols.add(new JavacRefSymbol(member, Tree.Kind.LAMBDA_EXPRESSION));
    //}
    return super.visitLambdaExpression(node, sink);
  }

  @Override
  public Tree visitMemberReference(MemberReferenceTree node, JavacTreeScannerSink sink) {
    final Symbol methodSymbol = ((JCTree.JCMemberReference)node).sym;
    sink.sinkReference(new JavacRefSymbol(methodSymbol, Tree.Kind.MEMBER_REFERENCE));
    return super.visitMemberReference(node, sink);
  }
}
