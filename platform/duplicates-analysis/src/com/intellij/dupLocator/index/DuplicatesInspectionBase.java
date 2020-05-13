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
import com.intellij.dupLocator.*;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.FileBasedIndex;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DuplicatesInspectionBase extends LocalInspectionTool {
  public boolean myFilterOutGeneratedCode;
  private static final int MIN_FRAGMENT_SIZE = 3; // todo 3 statements constant

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull final PsiFile psiFile, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
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
    for (Int2ObjectMap.Entry<TextRange> entry : processor.reportedRanges.int2ObjectEntrySet()) {
      int offset = entry.getIntKey();
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
      String message = DupLocatorBundle.message("inspection.message.found.duplicated.code.in", path);

      PsiElement targetElement = processor.reportedPsi.get(offset);
      TextRange rangeInElement = entry.getValue();
      final int offsetInOtherFile = processor.reportedOffsetInOtherFiles.get(offset);

      LocalQuickFix fix = isOnTheFly ? createNavigateToDupeFix(file, offsetInOtherFile) : null;
      long hash = processor.fragmentHash.get(offset);

      int hash2 = (int)(hash >> 32);
      LocalQuickFix viewAllDupesFix = isOnTheFly && hash != 0 ? createShowOtherDupesFix(virtualFile, offset, (int)hash, hash2) : null;

      boolean onlyExtractable = Registry.is("duplicates.inspection.only.extractable");
      LocalQuickFix extractMethodFix =
        (isOnTheFly || onlyExtractable) && hash != 0 ? createExtractMethodFix(targetElement, rangeInElement, (int)hash, hash2) : null;
      if (onlyExtractable) {
        if (extractMethodFix == null) return null;
        if (!isOnTheFly) extractMethodFix = null;
      }

      ProblemDescriptor descriptor = manager
        .createProblemDescriptor(targetElement, rangeInElement, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, fix,
                                 viewAllDupesFix, extractMethodFix);
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

  protected LocalQuickFix createExtractMethodFix(@NotNull PsiElement targetElement,
                                                 @Nullable TextRange rangeInElement,
                                                 int hash,
                                                 int hash2) {
    return null;
  }

  private final class LightDuplicatedCodeProcessor extends DuplicatedCodeProcessor<LighterASTNode> {
    private final TreeBackedLighterAST myAst;

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

  final class OldDuplicatedCodeProcessor extends DuplicatedCodeProcessor<PsiFragment> {
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

  abstract static class DuplicatedCodeProcessor<T> implements FileBasedIndex.ValueProcessor<IntArrayList> {
    final Int2ObjectRBTreeMap<TextRange> reportedRanges = new Int2ObjectRBTreeMap<>();
    final Int2ObjectOpenHashMap<VirtualFile> reportedFiles = new Int2ObjectOpenHashMap<>();
    final Int2ObjectOpenHashMap<PsiElement> reportedPsi = new Int2ObjectOpenHashMap<>();
    final Int2IntOpenHashMap reportedOffsetInOtherFiles = new Int2IntOpenHashMap();
    final Int2IntOpenHashMap fragmentSize = new Int2IntOpenHashMap();
    final Int2LongOpenHashMap fragmentHash = new Int2LongOpenHashMap();
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
    public boolean process(@NotNull VirtualFile file, IntArrayList list) {
      for(int i = 0, len = list.size(); i < len; i+=2) {
        ProgressManager.checkCanceled();

        if (list.getInt(i + 1) != myHash2) continue;
        int offset = list.getInt(i);

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

        int fragmentStartOffsetInteger = startOffset;
        Int2ObjectSortedMap<TextRange> map = reportedRanges.subMap(fragmentStartOffsetInteger, endOffset);
        int newFragmentSize = !map.isEmpty() ? 0:1;

        IntBidirectionalIterator iterator = map.keySet().iterator();
        while(iterator.hasNext()) {
          int next = iterator.nextInt();
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
