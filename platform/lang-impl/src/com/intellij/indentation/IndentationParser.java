package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public abstract class IndentationParser implements PsiParser {
  @NotNull
  private final IElementType myEolTokenType;
  @NotNull
  private final IElementType myIndentTokenType;
  @NotNull
  private final IElementType myBlockElementType;
  @NotNull
  private final IElementType myDocumentType;

  public IndentationParser(
    @NotNull IElementType documentType,
    @NotNull final IElementType blockElementType,
    @NotNull final IElementType eolTokenType,
    @NotNull final IElementType indentTokenType)
  {
    myDocumentType = documentType;
    myBlockElementType = blockElementType;
    myEolTokenType = eolTokenType;
    myIndentTokenType = indentTokenType;
  }

  @NotNull
  public final ASTNode parse(final IElementType root, final PsiBuilder builder) {
    final PsiBuilder.Marker fileMarker = builder.mark();
    final PsiBuilder.Marker documentMarker = builder.mark();

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
        if (startLineMarker == null) {
          startLineMarker = builder.mark();
        }
        eolSeen = true;
      }
      else {
        if (type == myIndentTokenType) {
          //noinspection ConstantConditions
          currentIndent = builder.getTokenText().length();
        }
        else {
          if (!eolSeen && !stack.isEmpty() && currentIndent > 0 && currentIndent < stack.peek().first) {
            // sometimes we do not have EOL between indents
            eolSeen = true;
          }
          if (eolSeen) {
            if (startLineMarker != null) {
              startLineMarker.rollbackTo();
              startLineMarker = null;
            }
            // Close indentation blocks
            while (!stack.isEmpty() && currentIndent < stack.peek().first) {
              closeBlock(builder, stack.pop().second);
            }

            if (!stack.isEmpty()) {
              final Pair<Integer, PsiBuilder.Marker> pair = stack.peek();
              if (currentIndent >= pair.first) {
                if (currentIndent == pair.first) {
                  closeBlock(builder, stack.pop().second);
                }
                passEOLsAndIndents(builder);
                stack.push(Pair.create(currentIndent, builder.mark()));
              }
            }
            eolSeen = false;
            currentIndent = 0;
          }
        }
      }
      advanceLexer(builder);
    }

    // Close all left opened markers
    if (startLineMarker != null){
      startLineMarker.drop();
    }
    while (!stack.isEmpty()){
      closeBlock(builder, stack.pop().second);
    }

    documentMarker.done(myDocumentType);
    fileMarker.done(root);
    return builder.getTreeBuilt();
  }

  protected void closeBlock(@NotNull PsiBuilder builder, @NotNull PsiBuilder.Marker marker) {
    marker.done(myBlockElementType);
  }

  protected void advanceLexer(@NotNull PsiBuilder builder) {
    builder.advanceLexer();
  }

  private void passEOLsAndIndents(@NotNull final PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    while (tokenType == myEolTokenType || tokenType == myIndentTokenType) {
      builder.advanceLexer();
      tokenType = builder.getTokenType();
    }
  }
}
