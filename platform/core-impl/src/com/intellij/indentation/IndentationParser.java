// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class IndentationParser implements PsiParser {
  private final @NotNull IElementType myEolTokenType;
  private final @NotNull IElementType myIndentTokenType;
  private final @NotNull IElementType myBlockElementType;
  private final @NotNull IElementType myDocumentType;
  private final List<? extends IElementType> myContainerTypes;

  public IndentationParser(
    @NotNull IElementType documentType,
    final @NotNull IElementType blockElementType,
    final @NotNull IElementType eolTokenType,
    final @NotNull IElementType indentTokenType,
    final List<? extends IElementType> containerTypes)
  {
    myDocumentType = documentType;
    myBlockElementType = blockElementType;
    myEolTokenType = eolTokenType;
    myIndentTokenType = indentTokenType;
    myContainerTypes = containerTypes;
  }

  public IndentationParser(
    @NotNull IElementType documentType,
    final @NotNull IElementType blockElementType,
    final @NotNull IElementType eolTokenType,
    final @NotNull IElementType indentTokenType)
  {
    this(documentType, blockElementType, eolTokenType, indentTokenType, null);
  }

  @Override
  public final @NotNull ASTNode parse(final IElementType root, final PsiBuilder builder) {
    final PsiBuilder.Marker fileMarker = builder.mark();
    final ArrayList<PsiBuilder.Marker> containerMarkers = new ArrayList<>();
    if (myContainerTypes != null) {
      for (IElementType ignored : myContainerTypes) {
        final PsiBuilder.Marker containerMarker = builder.mark();
        containerMarkers.add(containerMarker);
      }
    }
    final PsiBuilder.Marker documentMarker = builder.mark();
    while (builder.getTokenType() == myEolTokenType) {
      advanceLexer(builder);
    }

    int currentIndent = 0;
    boolean eolSeen = false;

    if (builder.getTokenType() == myIndentTokenType) {
      currentIndent = builder.getTokenText().length();
      advanceLexer(builder);
    }

    final Stack<BlockInfo> stack = new Stack<>();
    stack.push(new BlockInfo(currentIndent, builder.mark(), builder.getTokenType()));

    PsiBuilder.Marker startLineMarker = null;

    while (!builder.eof()) {
      final IElementType type = builder.getTokenType();
      // EOL
      if (type == myEolTokenType) {
        // Handle variant with several EOLs
        if (startLineMarker == null) {
          startLineMarker = builder.mark();
        }
        eolSeen = true;
        currentIndent = 0;
      }
      else {
        if (type == myIndentTokenType) {
          //noinspection ConstantConditions
          currentIndent = builder.getTokenText().length();
        }
        else {
          if (!eolSeen && !stack.isEmpty() && currentIndent > 0 && currentIndent < stack.peek().getIndent()) {
            // sometimes we do not have EOL between indents
            eolSeen = true;
          }
          if (isCustomTagDelimiter(type)) {
            builder.advanceLexer();
            stack.push(new BlockInfo(currentIndent, builder.mark(), type));
          }
          if (eolSeen) {
            if (startLineMarker != null) {
              startLineMarker.rollbackTo();
              startLineMarker = null;
            }
            // Close indentation blocks
            while (!stack.isEmpty() && currentIndent < stack.peek().getIndent()) {
              final BlockInfo blockInfo = stack.pop();
              closeBlock(builder, blockInfo.getMarker(), blockInfo.getStartTokenType());
            }

            if (!stack.isEmpty()) {
              final BlockInfo blockInfo = stack.peek();
              if (currentIndent >= blockInfo.getIndent()) {
                if (currentIndent == blockInfo.getIndent()) {
                  final BlockInfo info = stack.pop();
                  closeBlock(builder, info.getMarker(), info.getStartTokenType());
                }
                passEOLsAndIndents(builder);
                stack.push(new BlockInfo(currentIndent, builder.mark(), type));
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
      final BlockInfo blockInfo = stack.pop();
      closeBlock(builder, blockInfo.getMarker(), blockInfo.getStartTokenType());
    }

    documentMarker.done(myDocumentType);

    if (myContainerTypes != null) {
      for (int i = containerMarkers.size() - 1; i >= 0; i--) {
        final PsiBuilder.Marker marker = containerMarkers.get(i);
        marker.done(myContainerTypes.get(i));
      }
    }

    fileMarker.done(root);
    return builder.getTreeBuilt();
  }

  protected boolean isCustomTagDelimiter(IElementType type) {
    return false;
  }

  protected void closeBlock(final @NotNull PsiBuilder builder,
                            final @NotNull PsiBuilder.Marker marker,
                            final @Nullable IElementType startTokenType)
  {
    marker.done(myBlockElementType);
  }

  protected void advanceLexer(@NotNull PsiBuilder builder) {
    builder.advanceLexer();
  }

  private void passEOLsAndIndents(final @NotNull PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    while (tokenType == myEolTokenType || tokenType == myIndentTokenType) {
      builder.advanceLexer();
      tokenType = builder.getTokenType();
    }
  }

  private static final class BlockInfo {
    private final int myIndent;
    private final @NotNull PsiBuilder.Marker myMarker;
    private final @Nullable IElementType myStartTokenType;

    private BlockInfo(final int indent, final @NotNull PsiBuilder.Marker marker, final @Nullable IElementType type) {
      myIndent = indent;
      myMarker = marker;
      myStartTokenType = type;
    }

    public int getIndent() {
      return myIndent;
    }

    public @NotNull PsiBuilder.Marker getMarker() {
      return myMarker;
    }

    public @Nullable IElementType getStartTokenType() {
      return myStartTokenType;
    }
  }
}
