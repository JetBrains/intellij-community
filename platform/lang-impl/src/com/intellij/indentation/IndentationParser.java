package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Stack;

/**
 * @author oleg
 */
public abstract class IndentationParser implements PsiParser {
  private final IElementType myEolTokenType;
  private final IElementType myIndentTokenType;
  private final IElementType myBlockElementType;

  public IndentationParser(final IElementType blockElementType,
                           final IElementType eolTokenType,
                           final IElementType indentTokenType) {
    myBlockElementType = blockElementType;
    myEolTokenType = eolTokenType;
    myIndentTokenType = indentTokenType;
  }

  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    final PsiBuilder.Marker fileMarker = builder.mark();

    final Stack<Pair<Integer, PsiBuilder.Marker>> stack = new Stack<Pair<Integer, PsiBuilder.Marker>>();
    stack.push(Pair.create(0, builder.mark()));

    PsiBuilder.Marker startLineMarker = null;
    int currentIndent = 0;
    boolean eolSeen = false;

    while (!builder.eof()) {
      final IElementType type = builder.getTokenType();
      // EOL
      if (type == myEolTokenType) {
        // Handle variant with several EOLs
        if (startLineMarker == null){
          startLineMarker = builder.mark();
        }
        eolSeen = true;
      } else

      // Indent
      {
        if (type == myIndentTokenType){
          //noinspection ConstantConditions
          currentIndent = builder.getTokenText().length();
        } else

        if (eolSeen) {
          if (startLineMarker != null){
            startLineMarker.rollbackTo();
            startLineMarker = null;
          }
          // Close indentation blocks
          while (!stack.isEmpty() && currentIndent < stack.peek().first){
            stack.pop().second.done(myBlockElementType);
          }

          if (!stack.isEmpty()) {
            final Pair<Integer, PsiBuilder.Marker> pair = stack.peek();
            if (currentIndent == pair.first) {
              stack.pop().second.done(myBlockElementType);
              passEOLsAndIndents(builder);
              stack.push(Pair.create(currentIndent, builder.mark()));
            }
            if (currentIndent > pair.first) {
              passEOLsAndIndents(builder);
              stack.push(Pair.create(currentIndent, builder.mark()));
            }
          }
          eolSeen = false;
          currentIndent = 0;
        }
      }
      builder.advanceLexer();
    }

    // Close all left opened markers
    if (startLineMarker != null){
      startLineMarker.drop();
    }
    while (!stack.isEmpty()){
      stack.pop().second.done(myBlockElementType);
    }

    fileMarker.done(root);
    return builder.getTreeBuilt();
  }

  private void passEOLsAndIndents(final PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    while (tokenType == myEolTokenType || tokenType == myIndentTokenType){
      builder.advanceLexer();
      tokenType = builder.getTokenType();
    }
  }
}