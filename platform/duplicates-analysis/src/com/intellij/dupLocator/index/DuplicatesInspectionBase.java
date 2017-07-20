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
package com.intellij.dupLocator.index;

import com.intellij.codeInspection.*;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateVisitor;
import com.intellij.dupLocator.DuplocatorState;
import com.intellij.dupLocator.LightDuplicateProfile;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class DuplicatesInspectionBase extends LocalInspectionTool {
  public boolean myFilterOutGeneratedCode;
  private static final int MIN_FRAGMENT_SIZE = 3; // todo 3 statements constant

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile psiFile, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (!(virtualFile instanceof VirtualFileWithId) || /*!isOnTheFly || */!DuplicatesIndex.ourEnabled) return ProblemDescriptor.EMPTY_ARRAY;
    final DuplicatesProfile profile = DuplicatesIndex.findDuplicatesProfile(psiFile.getFileType());
    if (profile == null) return ProblemDescriptor.EMPTY_ARRAY;


    final FileASTNode node = psiFile.getNode();
    boolean usingLightProfile = profile instanceof LightDuplicateProfile &&
                                node.getElementType() instanceof ILightStubFileElementType &&
                                DuplicatesIndex.ourEnabledLightProfiles;
    final Project project = psiFile.getProject();
    DuplicatedCodeProcessor<?> processor;
    if (usingLightProfile) {
      processor = processLightDuplicates(node, virtualFile, (LightDuplicateProfile)profile, project);
    }
    else {
      processor = processPsiDuplicates(psiFile, virtualFile, profile, project);
    }
    if (processor == null) return null;

    final SmartList<ProblemDescriptor> descriptors = new SmartList<>();
    final VirtualFile baseDir = project.getBaseDir();
    for (Map.Entry<Integer, TextRange> entry : processor.reportedRanges.entrySet()) {
      final Integer offset = entry.getKey();
      if (!usingLightProfile && processor.fragmentSize.get(offset) < MIN_FRAGMENT_SIZE) continue;
      final VirtualFile file = processor.reportedFiles.get(offset);
      String path = null;

      if (file.equals(virtualFile)) {
        path = "this file";
      }
      else if (baseDir != null) {
        path = VfsUtilCore.getRelativePath(file, baseDir);
      }
      if (path == null) {
        path = file.getPath();
      }
      String message = "Found duplicated code in " + path;

      PsiElement targetElement = processor.reportedPsi.get(offset);
      TextRange rangeInElement = entry.getValue();
      final int offsetInOtherFile = processor.reportedOffsetInOtherFiles.get(offset);

      LocalQuickFix fix = createNavigateToDupeFix(file, offsetInOtherFile);
      long hash = processor.fragmentHash.get(offset);

      LocalQuickFix viewAllDupesFix = hash != 0 ? createShowOtherDupesFix(virtualFile, offset, (int)hash, (int)(hash >> 32)) : null;

      ProblemDescriptor descriptor = manager
        .createProblemDescriptor(targetElement, rangeInElement, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, fix,
                                 viewAllDupesFix);
      descriptors.add(descriptor);
    }

    return descriptors.isEmpty() ? null : descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private DuplicatedCodeProcessor<?> processLightDuplicates(FileASTNode node,
                                                            VirtualFile virtualFile,
                                                            LightDuplicateProfile profile,
                                                            Project project) {
    final Ref<DuplicatedCodeProcessor<LighterASTNode>> processorRef = new Ref<>();
    LighterAST lighterAST = node.getLighterAST();

    profile.process(lighterAST, (hash, hash2, ast, nodes) -> {
      DuplicatedCodeProcessor<LighterASTNode> processor = processorRef.get();
      if (processor == null) {
        processorRef.set(processor = new LightDuplicatedCodeProcessor((TreeBackedLighterAST)ast, virtualFile, project));
      }
      processor.process(hash, hash2, nodes[0]);
    });
    return processorRef.get();
  }

  private DuplicatedCodeProcessor<?> processPsiDuplicates(PsiFile psiFile,
                                                          VirtualFile virtualFile,
                                                          DuplicatesProfile profile,
                                                          Project project) {
    final DuplocatorState state = profile.getDuplocatorState(psiFile.getLanguage());
    final Ref<DuplicatedCodeProcessor<PsiFragment>> processorRef = new Ref<>();

    DuplocateVisitor visitor = profile.createVisitor((hash, cost, frag) -> {
      if (!DuplicatesIndex.isIndexedFragment(frag, cost, profile, state)) {
        return;
      }
      DuplicatedCodeProcessor<PsiFragment> processor = processorRef.get();
      if (processor == null) {
        processorRef.set(processor = new OldDuplicatedCodeProcessor(virtualFile, project));
      }
      processor.process(hash, 0, frag);
    }, true);

    visitor.visitNode(psiFile);
    return processorRef.get();
  }

  protected LocalQuickFix createNavigateToDupeFix(@NotNull VirtualFile file, int offsetInOtherFile) {
    return null;
  }

  protected LocalQuickFix createShowOtherDupesFix(VirtualFile file, int offset, int hash, int hash2) {
    return null;
  }

  private class LightDuplicatedCodeProcessor extends DuplicatedCodeProcessor<LighterASTNode> {
    private TreeBackedLighterAST myAst;

    private LightDuplicatedCodeProcessor(@NotNull TreeBackedLighterAST ast, VirtualFile file, Project project) {
      super(file, project, myFilterOutGeneratedCode);
      myAst = ast;
    }

    @Override
    protected TextRange getRangeInElement(LighterASTNode node) {
      return null;
    }

    @Override
    protected PsiElement getPsi(LighterASTNode node) {
      return myAst.unwrap(node).getPsi();
    }

    @Override
    protected int getStartOffset(LighterASTNode node) {
      return node.getStartOffset();
    }

    @Override
    protected int getEndOffset(LighterASTNode node) {
      return node.getEndOffset();
    }

    @Override
    protected boolean isLightProfile() {
      return true;
    }
  }

  class OldDuplicatedCodeProcessor extends DuplicatedCodeProcessor<PsiFragment> {
    private OldDuplicatedCodeProcessor(VirtualFile file, Project project) {
      super(file, project, myFilterOutGeneratedCode);
    }

    @Override
    protected TextRange getRangeInElement(PsiFragment node) {
      PsiElement[] elements = node.getElements();
      TextRange rangeInElement = null;
      if (elements.length > 1) {

        PsiElement lastElement = elements[elements.length - 1];
        rangeInElement = new TextRange(
          elements[0].getStartOffsetInParent(),
          lastElement.getStartOffsetInParent() + lastElement.getTextLength()
        );
      }
      return rangeInElement;
    }

    @Override
    protected PsiElement getPsi(PsiFragment node) {
      PsiElement[] elements = node.getElements();

      return elements.length > 1 ? elements[0].getParent() : elements[0];
    }

    @Override
    protected int getStartOffset(PsiFragment node) {
      return node.getStartOffset();
    }

    @Override
    protected int getEndOffset(PsiFragment node) {
      return node.getEndOffset();
    }

    @Override
    protected boolean isLightProfile() {
      return false;
    }
  }

  abstract static class DuplicatedCodeProcessor<T> implements FileBasedIndex.ValueProcessor<TIntArrayList> {
    final TreeMap<Integer, TextRange> reportedRanges = new TreeMap<>();
    final TIntObjectHashMap<VirtualFile> reportedFiles = new TIntObjectHashMap<>();
    final TIntObjectHashMap<PsiElement> reportedPsi = new TIntObjectHashMap<>();
    final TIntIntHashMap reportedOffsetInOtherFiles = new TIntIntHashMap();
    final TIntIntHashMap fragmentSize = new TIntIntHashMap();
    final TIntLongHashMap fragmentHash = new TIntLongHashMap();
    final VirtualFile virtualFile;
    final Project project;
    final FileIndex myFileIndex;
    final boolean mySkipGeneratedCode;
    final boolean myFileWithinGeneratedCode;
    T myNode;
    int myHash;
    int myHash2;

    DuplicatedCodeProcessor(VirtualFile file, Project project, boolean skipGeneratedCode) {
      virtualFile = file;
      this.project = project;
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      mySkipGeneratedCode = skipGeneratedCode;
      myFileWithinGeneratedCode = skipGeneratedCode && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project);
    }

    void process(int hash, int hash2, T node) {
      ProgressManager.checkCanceled();
      myNode = node;
      myHash = hash;
      myHash2 = hash2;
      FileBasedIndex.getInstance().processValues(DuplicatesIndex.NAME, hash, null, this, GlobalSearchScope.projectScope(project));
    }

    @Override
    public boolean process(@NotNull VirtualFile file, TIntArrayList list) {
      for(int i = 0, len = list.size(); i < len; i+=2) {
        ProgressManager.checkCanceled();

        if (list.getQuick(i + 1) != myHash2) continue;
        int offset = list.getQuick(i);

        if (myFileIndex.isInSourceContent(virtualFile)) {
          if (!myFileIndex.isInSourceContent(file)) return true;
          if (!TestSourcesFilter.isTestSources(virtualFile, project) && TestSourcesFilter.isTestSources(file, project)) return true;
          if (mySkipGeneratedCode) {
            if (!myFileWithinGeneratedCode && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)) return true;
          }
        } else if (myFileIndex.isInSourceContent(file)) {
          return true;
        }

        final int startOffset = getStartOffset(myNode);
        final int endOffset = getEndOffset(myNode);
        if (file.equals(virtualFile) && offset >= startOffset && offset < endOffset) continue;

        PsiElement target = getPsi(myNode);
        TextRange rangeInElement = getRangeInElement(myNode);

        Integer fragmentStartOffsetInteger = startOffset;
        SortedMap<Integer,TextRange> map = reportedRanges.subMap(fragmentStartOffsetInteger, endOffset);
        int newFragmentSize = !map.isEmpty() ? 0:1;

        Iterator<Integer> iterator = map.keySet().iterator();
        while(iterator.hasNext()) {
          Integer next = iterator.next();
          iterator.remove();
          reportedFiles.remove(next);
          reportedOffsetInOtherFiles.remove(next);
          reportedPsi.remove(next);
          newFragmentSize += fragmentSize.remove(next);
        }

        reportedRanges.put(fragmentStartOffsetInteger, rangeInElement);
        reportedFiles.put(fragmentStartOffsetInteger, file);
        reportedOffsetInOtherFiles.put(fragmentStartOffsetInteger, offset);
        reportedPsi.put(fragmentStartOffsetInteger, target);
        fragmentSize.put(fragmentStartOffsetInteger, newFragmentSize);
        if (newFragmentSize >= MIN_FRAGMENT_SIZE || isLightProfile()) {
          fragmentHash.put(fragmentStartOffsetInteger, (myHash & 0xFFFFFFFFL) | ((long)myHash2 << 32));
        }
        return false;
      }
      return true;
    }

    protected abstract TextRange getRangeInElement(T node);
    protected abstract PsiElement getPsi(T node);

    protected abstract int getStartOffset(T node);
    protected abstract int getEndOffset(T node);
    protected abstract boolean isLightProfile();
  }
}
