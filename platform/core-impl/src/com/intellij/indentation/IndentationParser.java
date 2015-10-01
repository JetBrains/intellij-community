/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
  private final List<IElementType> myContainerTypes;

  public IndentationParser(
    @NotNull IElementType documentType,
    @NotNull final IElementType blockElementType,
    @NotNull final IElementType eolTokenType,
    @NotNull final IElementType indentTokenType,
    final List<IElementType> containerTypes)
  {
    myDocumentType = documentType;
    myBlockElementType = blockElementType;
    myEolTokenType = eolTokenType;
    myIndentTokenType = indentTokenType;
    myContainerTypes = containerTypes;
  }

  public IndentationParser(
    @NotNull IElementType documentType,
    @NotNull final IElementType blockElementType,
    @NotNull final IElementType eolTokenType,
    @NotNull final IElementType indentTokenType)
  {
    this(documentType, blockElementType, eolTokenType, indentTokenType, null);
  }

  @Override
  @NotNull
  public final ASTNode parse(final IElementType root, final PsiBuilder builder) {
    final PsiBuilder.Marker fileMarker = builder.mark();
    final ArrayList<PsiBuilder.Marker> containerMarkers = new ArrayList<PsiBuilder.Marker>();
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

    final Stack<BlockInfo> stack = new Stack<BlockInfo>();
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

  protected void closeBlock(final @NotNull PsiBuilder builder,
                            final @NotNull PsiBuilder.Marker marker,
                            final @Nullable IElementType startTokenType)
  {
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

  private static final class BlockInfo {
    private final int myIndent;
    @NotNull
    private final PsiBuilder.Marker myMarker;
    @Nullable
    private final IElementType myStartTokenType;

    private BlockInfo(final int indent, final @NotNull PsiBuilder.Marker marker, final @Nullable IElementType type) {
      myIndent = indent;
      myMarker = marker;
      myStartTokenType = type;
    }

    public int getIndent() {
      return myIndent;
    }

    @NotNull
    public PsiBuilder.Marker getMarker() {
      return myMarker;
    }

    @Nullable
    public IElementType getStartTokenType() {
      return myStartTokenType;
    }
  }
}
