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

import com.intellij.lang.FileASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILightStubFileElementType;


public class LightStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.LightStubBuilder");

  public StubElement buildStubTree(final PsiFile file) {
    final FileASTNode node = file.getNode();
    assert node != null;

    if (!(node.getElementType() instanceof ILightStubFileElementType)) {
      LOG.error("File is not of ILightStubFileElementType: " + file);
      return null;
    }

    final ILightStubFileElementType<?> type = (ILightStubFileElementType)node.getElementType();
    final LighterAST tree = new LighterAST(node.getCharTable(), type.parseContentsLight(node));
    return buildStubTreeFor(tree, tree.getRoot(), createStubForFile(file, tree));
  }

  protected StubElement createStubForFile(final PsiFile file, final LighterAST tree) {
    //noinspection unchecked
    return new PsiFileStubImpl(file);
  }

  protected StubElement buildStubTreeFor(final LighterAST tree, final LighterASTNode element, final StubElement parentStub) {
    StubElement stub = parentStub;

    final IElementType elementType = element.getTokenType();
    if (elementType instanceof IStubElementType) {
      if (elementType instanceof ILightStubElementType) {
        final ILightStubElementType lightElementType = (ILightStubElementType)elementType;
        if (lightElementType.shouldCreateStub(tree, element, parentStub)) {
          stub = lightElementType.createStub(tree, element, parentStub);
        }
      }
      else {
        LOG.error("Element is not of ILighterStubElementType: " + elementType + ", " + element);
      }
    }

    for (final LighterASTNode child : tree.getChildren(element)) {
      if (!skipChildProcessingWhenBuildingStubs(elementType, child.getTokenType())) {
        buildStubTreeFor(tree, child, stub);
      }
    }

    return stub;
  }

  public boolean skipChildProcessingWhenBuildingStubs(IElementType nodeType, IElementType childType) {
    return false;
  }
}