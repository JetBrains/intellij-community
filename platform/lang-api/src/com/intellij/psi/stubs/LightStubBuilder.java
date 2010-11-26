/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.lang.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILightStubFileElementType;
import gnu.trove.TIntStack;

import java.util.List;
import java.util.Stack;


public class LightStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.LightStubBuilder");

  @Override
  public StubElement buildStubTree(final PsiFile file) {
    final FileType fileType = file.getFileType();
    if (!(fileType instanceof LanguageFileType)) {
      LOG.error("File is not of LanguageFileType: " + fileType + ", " + file);
      return null;
    }

    final Language language = ((LanguageFileType)fileType).getLanguage();
    final IFileElementType contentType = LanguageParserDefinitions.INSTANCE.forLanguage(language).getFileNodeType();
    if (!(contentType instanceof ILightStubFileElementType)) {
      LOG.error("File is not of ILightStubFileElementType: " + contentType + ", " + file);
      return null;
    }

    final FileASTNode node = file.getNode();
    assert node != null : file;

    final ILightStubFileElementType<?> type = (ILightStubFileElementType)contentType;
    final LighterAST tree = new LighterAST(node.getCharTable(), type.parseContentsLight(node));
    final StubElement rootStub = createStubForFile(file, tree);
    buildStubTree(tree, tree.getRoot(), rootStub);
    return rootStub;
  }

  protected StubElement createStubForFile(final PsiFile file, final LighterAST tree) {
    //noinspection unchecked
    return new PsiFileStubImpl(file);
  }

  protected void buildStubTree(final LighterAST tree, final LighterASTNode root, final StubElement rootStub) {
    final Stack<LighterASTNode> parents = new Stack<LighterASTNode>();
    final TIntStack childNumbers = new TIntStack();
    final Stack<List<LighterASTNode>> kinderGarden = new Stack<List<LighterASTNode>>();
    final Stack<StubElement> parentStubs = new Stack<StubElement>();

    LighterASTNode parent = null;
    LighterASTNode element = root;
    List<LighterASTNode> children = null;
    int childNumber = 0;
    StubElement parentStub = rootStub;

    nextElement:
    while (element != null) {
      final StubElement stub = createStub(tree, element, parentStub);

      final List<LighterASTNode> kids = tree.getChildren(element);
      if (kids.size() > 0) {
        if (parent != null) {
          parents.push(parent);
          childNumbers.push(childNumber);
          kinderGarden.push(children);
          parentStubs.push(parentStub);
        }
        parent = element;
        element = (children = kids).get(childNumber = 0);
        parentStub = stub;
        if (!skipChildProcessingWhenBuildingStubs(parent.getTokenType(), element.getTokenType())) continue nextElement;
      }

      while (children != null && ++childNumber < children.size()) {
        element = children.get(childNumber);
        if (!skipChildProcessingWhenBuildingStubs(parent.getTokenType(), element.getTokenType())) continue nextElement;
      }

      element = null;
      while (parents.size() > 0) {
        parent = parents.pop();
        childNumber = childNumbers.pop();
        children = kinderGarden.pop();
        parentStub = parentStubs.pop();
        while (++childNumber < children.size()) {
          element = children.get(childNumber);
          if (!skipChildProcessingWhenBuildingStubs(parent.getTokenType(), element.getTokenType())) continue nextElement;
        }
        element = null;
      }
    }
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  protected StubElement createStub(final LighterAST tree, final LighterASTNode element, final StubElement parentStub) {
    final IElementType elementType = element.getTokenType();
    if (elementType instanceof IStubElementType) {
      if (elementType instanceof ILightStubElementType) {
        final ILightStubElementType lightElementType = (ILightStubElementType)elementType;
        if (lightElementType.shouldCreateStub(tree, element, parentStub)) {
          return lightElementType.createStub(tree, element, parentStub);
        }
      }
      else {
        LOG.error("Element is not of ILighterStubElementType: " + elementType + ", " + element);
      }
    }

    return parentStub;
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(final IElementType nodeType, final IElementType childType) {
    return false;
  }
}