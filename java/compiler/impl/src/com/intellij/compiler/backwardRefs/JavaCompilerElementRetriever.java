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
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JavaCompilerElementRetriever {
  private static final Logger LOG = Logger.getInstance(JavaCompilerElementRetriever.class);

  private final static TokenSet FUN_EXPR = TokenSet.create(JavaElementType.LAMBDA_EXPRESSION, JavaElementType.METHOD_REF_EXPRESSION);

  @NotNull
  static PsiFunctionalExpression[] retrieveFunExpressionsByIndices(@NotNull TIntHashSet indices,
                                                                   @NotNull PsiFileWithStubSupport psiFile) {
    StubTree tree = psiFile.getStubTree();
    boolean foreign = tree == null;
    if (foreign) {
      tree = ((PsiFileImpl)psiFile).calcStubTree();
    }

    PsiFunctionalExpression[] result = new PsiFunctionalExpression[indices.size()];
    int resIdx = 0;
    int funExprIdx = 0;
    for (StubElement<?> element : tree.getPlainList()) {
      if (FUN_EXPR.contains(element.getStubType()) && indices.contains(funExprIdx++)) {
        result[resIdx++] = (PsiFunctionalExpression)element.getPsi();
      }
    }

    if (result.length != resIdx) {
      final CompilerReferenceServiceImpl compilerReferenceService =
        (CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(psiFile.getProject());
      final Set<Module> state = compilerReferenceService.getDirtyScopeHolder().getAllDirtyModules();
      final VirtualFile file = psiFile.getVirtualFile();
      final Module moduleForFile = ProjectFileIndex.getInstance(psiFile.getProject()).getModuleForFile(file);
      LOG.error("Compiler functional expression index doesn't match to stub index.\n" +
                "Functional expression indices: " + indices + "\n" +
                "Does the file belong to dirty scope?: " + state.contains(moduleForFile),
                new Attachment(psiFile.getName(), psiFile.getText()));

      return ContainerUtil.filter(result, Objects::nonNull).toArray(PsiFunctionalExpression.EMPTY_ARRAY);
    }

    return result;
  }

  @NotNull
  static PsiClass[] retrieveClassesByInternalNames(@NotNull String[] internalNames,
                                                   @NotNull PsiNamedElement baseClass,
                                                   @NotNull PsiFileWithStubSupport psiFile) {
    Collection<InternalClassMatcher> matchers = createClassMatcher(internalNames, baseClass);
    return ReadAction.compute(() -> retrieveMatchedClasses(psiFile, matchers).toArray(PsiClass.EMPTY_ARRAY));
  }

  private static List<PsiClass> retrieveMatchedClasses(PsiFileWithStubSupport psiFile, Collection<InternalClassMatcher> matchers) {
    final List<PsiClass> result = new ArrayList<>(matchers.size());
    StubTree tree = psiFile.getStubTree();
    boolean foreign = tree == null;
    if (foreign) {
      tree = ((PsiFileImpl)psiFile).calcStubTree();
    }

    for (StubElement<?> element : tree.getPlainList()) {
      if (element instanceof PsiClassStub && match((PsiClassStub)element, matchers)) {
        result.add(((StubBase<PsiClass>)element).getPsi());
      }
    }

    return result;
  }


  private static boolean match(PsiClassStub stub, Collection<InternalClassMatcher> matchers) {
    for (InternalClassMatcher matcher : matchers) {
      if (matcher.matches(stub)) {
        //qualified name is unique among file's classes
        if (matcher instanceof InternalClassMatcher.ByQualifiedName) {
          matchers.remove(matcher);
        }
        return true;
      }
    }
    return false;
  }

  private static Collection<InternalClassMatcher> createClassMatcher(@NotNull String[] internalNames, @NotNull PsiNamedElement baseClass) {
    boolean matcherBySuperNameAdded = false;
    final List<InternalClassMatcher> matchers = new ArrayList<>(internalNames.length);
    for (String internalName : internalNames) {
      int curLast = internalName.length() - 1;
      while (true) {
        int lastIndex = internalName.lastIndexOf('$', curLast);
        if (lastIndex > -1 && lastIndex < internalName.length() - 1) {
          final int followingIndex = lastIndex + 1;
          final boolean digit = Character.isDigit(internalName.charAt(followingIndex));
          if (digit) {
            if (curLast == internalName.length() - 1) {
              final int nextNonDigit = getNextNonDigitIndex(internalName, followingIndex);
              if (nextNonDigit == -1) {
                if (matcherBySuperNameAdded) {
                  break;
                }
                matcherBySuperNameAdded = true;
                //anonymous
                matchers.add(new InternalClassMatcher.BySuperName(ReadAction.compute(() -> baseClass.getName())));
                break;
              } else {
                //declared inside method
                matchers.add(new InternalClassMatcher.ByName(internalName.substring(nextNonDigit)));
              }
            }
            else {
              //declared in anonymous
              matchers.add(new InternalClassMatcher.ByName(StringUtil.getShortName(internalName, '$')));
              break;
            }
          }
        }
        else {
          matchers.add(new InternalClassMatcher.ByQualifiedName(StringUtil.replace(internalName, "$", ".")));
          break;
        }
        curLast = lastIndex - 1;
      }
    }
    return matchers;
  }

  private static int getNextNonDigitIndex(String name, int digitIndex) {
    for (int i = digitIndex + 1; i < name.length(); i++) {
      if (!Character.isDigit(name.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  private interface InternalClassMatcher {
    boolean matches(PsiClassStub stub);

    class BySuperName implements InternalClassMatcher {
      private final String mySuperName;

      public BySuperName(String name) {mySuperName = name;}

      @Override
      public boolean matches(PsiClassStub stub) {
        return stub.isAnonymous() && mySuperName.equals(PsiNameHelper.getShortClassName(stub.getBaseClassReferenceText()));
      }
    }

    class ByName implements InternalClassMatcher {
      private final String myName;

      public ByName(String name) {myName = name;}

      @Override
      public boolean matches(PsiClassStub stub) {
        return myName.equals(stub.getName());
      }
    }

    class ByQualifiedName implements InternalClassMatcher {
      private final String myQName;

      public ByQualifiedName(String name) {myQName = name;}

      @Override
      public boolean matches(PsiClassStub stub) {
        return myQName.equals(stub.getQualifiedName());
      }
    }
  }
}
