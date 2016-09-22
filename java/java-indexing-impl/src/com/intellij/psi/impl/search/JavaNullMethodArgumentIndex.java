/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JavaNullMethodArgumentIndex extends ScalarIndexExtension<JavaNullMethodArgumentIndex.MethodCallData> implements PsiDependentIndex {
  private static final Logger LOG = Logger.getInstance(JavaNullMethodArgumentIndex.class);

  public static final ID<MethodCallData, Void> INDEX_ID = ID.create("java.null.method.argument");
  private boolean myOfflineMode = ApplicationManager.getApplication().isCommandLine() &&
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
      final CharSequence contentAsText = inputData.getContentAsText();
      if (!JavaStubElementTypes.JAVA_FILE.shouldBuildStubFor(inputData.getFile())) {
        return Collections.emptyMap();
      }
      if (myOfflineMode) {
        return Collections.emptyMap();
      }
      if (!StringUtil.contains(contentAsText, PsiKeyword.NULL)) {
        return Collections.emptyMap();
      }

      Map<MethodCallData, Void> result = new THashMap<>();
      final LighterAST lighterAst = ((FileContentImpl)inputData).getLighterASTForPsiDependentIndex();

      new RecursiveLighterASTNodeWalkingVisitor(lighterAst) {
        @Override
        public void visitNode(@NotNull LighterASTNode element) {
          if (element.getTokenType() == JavaElementType.METHOD_CALL_EXPRESSION ||
              element.getTokenType() == JavaElementType.NEW_EXPRESSION ||
              element.getTokenType() == JavaElementType.ANONYMOUS_CLASS) {
            final IntArrayList indices = getNullParameterIndices(element);
            if (indices != null) {
              final String name = getMethodName(element, element.getTokenType());
              if (name != null) {
                for (int i = 0; i < indices.size(); i++) {
                  final int nullParameterIndex = indices.get(i);
                  result.put(new MethodCallData(name, nullParameterIndex), null);
                }
              }
            }
          }
          super.visitNode(element);
        }

        @Nullable
        private IntArrayList getNullParameterIndices(@NotNull LighterASTNode methodCall) {
          final LighterASTNode node = LightTreeUtil.firstChildOfType(lighterAst, methodCall, JavaElementType.EXPRESSION_LIST);
          if (node == null) return null;
          final List<LighterASTNode> parameters = LightTreeUtil.getChildrenOfType(lighterAst, node, ElementType.EXPRESSION_BIT_SET);
          IntArrayList indices = null;
          for (int idx = 0; idx < parameters.size(); idx++) {
            LighterASTNode parameter = parameters.get(idx);
            if (parameter.getTokenType() == JavaElementType.LITERAL_EXPRESSION) {
              final CharSequence literal = ((LighterASTTokenNode) lighterAst.getChildren(parameter).get(0)).getText();
              if (StringUtil.equals(literal, PsiKeyword.NULL)) {
                if (indices == null) {
                  indices = new IntArrayList(1);
                }
                indices.add(idx);
              }
            }
          }
          return indices;
        }

        @Nullable
        private String getMethodName(@NotNull LighterASTNode call, IElementType elementType) {
          if (elementType == JavaElementType.NEW_EXPRESSION || elementType == JavaElementType.ANONYMOUS_CLASS) {
            final List<LighterASTNode> refs = LightTreeUtil.getChildrenOfType(lighterAst, call, JavaElementType.JAVA_CODE_REFERENCE);
            if (refs.isEmpty()) return null;
            final LighterASTNode lastRef = refs.get(refs.size() - 1);
            return getLastIdentifierText(lastRef);
          } else {
            LOG.assertTrue(elementType == JavaElementType.METHOD_CALL_EXPRESSION);
            final LighterASTNode methodReference = lighterAst.getChildren(call).get(0);
            if (methodReference.getTokenType() == JavaElementType.REFERENCE_EXPRESSION) {
              return getLastIdentifierText(methodReference);
            }
          }
          return null;
        }

        @Nullable
        private String getLastIdentifierText(LighterASTNode lastRef) {
          final List<LighterASTNode> identifiers = LightTreeUtil.getChildrenOfType(lighterAst, lastRef, JavaTokenType.IDENTIFIER);
          if (identifiers.isEmpty()) return null;
          final LighterASTNode methodNameIdentifier = identifiers.get(identifiers.size() - 1);
          return RecordUtil.intern(lighterAst.getCharTable(), methodNameIdentifier);
        }
      }.visitNode(lighterAst.getRoot());

      return result;
    };
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
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE);
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
