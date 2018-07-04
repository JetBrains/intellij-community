// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

public class JavaNullMethodArgumentIndex extends ScalarIndexExtension<JavaNullMethodArgumentIndex.MethodCallData> implements PsiDependentIndex {
  private static final Logger LOG = Logger.getInstance(JavaNullMethodArgumentIndex.class);

  public static final ID<MethodCallData, Void> INDEX_ID = ID.create("java.null.method.argument");
  private interface Lazy {
    TokenSet CALL_TYPES = TokenSet.create(METHOD_CALL_EXPRESSION, NEW_EXPRESSION, ANONYMOUS_CLASS);
  }
  private final boolean myOfflineMode = ApplicationManager.getApplication().isCommandLine() &&
                                        !ApplicationManager.getApplication().isUnitTestMode();

  @NotNull
  @Override
  public ID<MethodCallData, Void> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<MethodCallData, Void, FileContent> getIndexer() {
    return inputData -> {
      if (myOfflineMode) {
        return Collections.emptyMap();
      }

      int[] nullOffsets = new StringSearcher(PsiKeyword.NULL, true, true).findAllOccurrences(inputData.getContentAsText());
      if (nullOffsets.length == 0) return Collections.emptyMap();

      LighterAST lighterAst = ((FileContentImpl)inputData).getLighterASTForPsiDependentIndex();
      Set<LighterASTNode> calls = findCallsWithNulls(lighterAst, nullOffsets);
      if (calls.isEmpty()) return Collections.emptyMap();

      Map<MethodCallData, Void> result = new THashMap<>();
      for (LighterASTNode element : calls) {
        final IntArrayList indices = getNullParameterIndices(lighterAst, element);
        if (indices != null) {
          final String name = getMethodName(lighterAst, element, element.getTokenType());
          if (name != null) {
            for (int i = 0; i < indices.size(); i++) {
              result.put(new MethodCallData(name, indices.get(i)), null);
            }
          }
        }
      }
      return result;
    };
  }

  @NotNull
  private static Set<LighterASTNode> findCallsWithNulls(LighterAST lighterAst, int[] nullOffsets) {
    Set<LighterASTNode> calls = new HashSet<>();
    for (int offset : nullOffsets) {
      LighterASTNode leaf = LightTreeUtil.findLeafElementAt(lighterAst, offset);
      LighterASTNode literal = leaf == null ? null : lighterAst.getParent(leaf);
      if (isNullLiteral(lighterAst, literal)) {
        LighterASTNode exprList = lighterAst.getParent(literal);
        if (exprList != null && exprList.getTokenType() == EXPRESSION_LIST) {
          ContainerUtil.addIfNotNull(calls, LightTreeUtil.getParentOfType(lighterAst, exprList, Lazy.CALL_TYPES, ElementType.MEMBER_BIT_SET));
        }
      }
    }
    return calls;
  }

  @Nullable
  private static IntArrayList getNullParameterIndices(LighterAST lighterAst, @NotNull LighterASTNode methodCall) {
    final LighterASTNode node = LightTreeUtil.firstChildOfType(lighterAst, methodCall, EXPRESSION_LIST);
    if (node == null) return null;
    final List<LighterASTNode> parameters = JavaLightTreeUtil.getExpressionChildren(lighterAst, node);
    IntArrayList indices = new IntArrayList(1);
    for (int idx = 0; idx < parameters.size(); idx++) {
      if (isNullLiteral(lighterAst, parameters.get(idx))) {
        indices.add(idx);
      }
    }
    return indices;
  }

  private static boolean isNullLiteral(LighterAST lighterAst, @Nullable LighterASTNode expr) {
    return expr != null && expr.getTokenType() == LITERAL_EXPRESSION &&
           lighterAst.getChildren(expr).get(0).getTokenType() == JavaTokenType.NULL_KEYWORD;
  }

  @Nullable
  private static String getMethodName(LighterAST lighterAst, @NotNull LighterASTNode call, IElementType elementType) {
    if (elementType == NEW_EXPRESSION || elementType == ANONYMOUS_CLASS) {
      final List<LighterASTNode> refs = LightTreeUtil.getChildrenOfType(lighterAst, call, JAVA_CODE_REFERENCE);
      if (refs.isEmpty()) return null;
      final LighterASTNode lastRef = refs.get(refs.size() - 1);
      return JavaLightTreeUtil.getNameIdentifierText(lighterAst, lastRef);
    }

    LOG.assertTrue(elementType == METHOD_CALL_EXPRESSION);
    final LighterASTNode methodReference = lighterAst.getChildren(call).get(0);
    if (methodReference.getTokenType() == REFERENCE_EXPRESSION) {
      return JavaLightTreeUtil.getNameIdentifierText(lighterAst, methodReference);
    }
    return null;
  }

  @NotNull
  @Override
  public KeyDescriptor<MethodCallData> getKeyDescriptor() {
    return new KeyDescriptor<MethodCallData>() {
      @Override
      public int getHashCode(MethodCallData value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(MethodCallData val1, MethodCallData val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, MethodCallData value) throws IOException {
        EnumeratorStringDescriptor.INSTANCE.save(out, value.getMethodName());
        DataInputOutputUtil.writeINT(out, value.getNullParameterIndex());
      }

      @Override
      public MethodCallData read(@NotNull DataInput in) throws IOException {
        return new MethodCallData(EnumeratorStringDescriptor.INSTANCE.read(in),
                                  DataInputOutputUtil.readINT(in));
      }
    };
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return JavaStubElementTypes.JAVA_FILE.shouldBuildStubFor(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static final class MethodCallData {
    @NotNull
    private final String myMethodName;
    private final int myNullParameterIndex;

    public MethodCallData(@NotNull String name, int index) {
      myMethodName = name;
      myNullParameterIndex = index;
    }

    @NotNull
    public String getMethodName() {
      return myMethodName;
    }

    public int getNullParameterIndex() {
      return myNullParameterIndex;
    }


    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MethodCallData data = (MethodCallData)o;

      if (myNullParameterIndex != data.myNullParameterIndex) return false;
      if (!myMethodName.equals(data.myMethodName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myMethodName.hashCode();
      result = 31 * result + myNullParameterIndex;
      return result;
    }

    @Override
    public String toString() {
      return "MethodCallData{" +
             "myMethodName='" + myMethodName + '\'' +
             ", myNullParameterIndex=" + myNullParameterIndex +
             '}';
    }
  }
}
