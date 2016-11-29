package org.jetbrains.jps.javac.ast;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

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
    if (symbol != null) {
      sink.sinkDeclaration(new JavacDef.JavacFunExprDef(JavacRef.JavacSymbolRefBase.fromSymbol(symbol)));
    }
    return super.visitLambdaExpression(node, sink);
  }

  @Override
  public Tree visitMemberReference(MemberReferenceTree node, JavacTreeScannerSink sink) {
    JCTree.JCMemberReference memberRef = (JCTree.JCMemberReference)node;
    final Symbol sym = memberRef.sym;
    if (sym != null) {
      sink.sinkReference(JavacRef.JavacSymbolRefBase.fromSymbol(sym));
      sink.sinkDeclaration(new JavacDef.JavacFunExprDef(JavacRef.JavacSymbolRefBase.fromSymbol(memberRef.type.asElement())));
    }
    return super.visitMemberReference(node, sink);
  }
}
