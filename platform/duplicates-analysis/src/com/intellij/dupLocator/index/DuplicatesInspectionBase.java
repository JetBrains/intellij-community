package com.intellij.dupLocator.index;

import com.intellij.codeInspection.*;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocatorState;
import com.intellij.dupLocator.treeHash.FragmentsCollector;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class DuplicatesInspectionBase extends LocalInspectionTool {
  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile psiFile, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (!(virtualFile instanceof VirtualFileWithId) || /*!isOnTheFly || */!DuplicatesIndex.ourEnabled) return ProblemDescriptor.EMPTY_ARRAY;
    final DuplicatesProfile profile = DuplicatesIndex.findDuplicatesProfile(psiFile.getFileType());
    if (profile == null) return ProblemDescriptor.EMPTY_ARRAY;

    final DuplocatorState state = profile.getDuplocatorState(psiFile.getLanguage());
    final SmartList<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();

    final TreeMap<Integer, TextRange> reportedRanges = new TreeMap<Integer, TextRange>();
    final TIntObjectHashMap<VirtualFile> reportedFiles = new TIntObjectHashMap<VirtualFile>();
    final TIntObjectHashMap<PsiElement> reportedPsi = new TIntObjectHashMap<PsiElement>();
    final TIntIntHashMap reportedOffsetInOtherFiles = new TIntIntHashMap();
    final TIntIntHashMap fragmentSize = new TIntIntHashMap();

    profile.createVisitor(new FragmentsCollector() {
      @Override
      public void add(int hash, final int cost, @Nullable final PsiFragment frag) {
        if (!DuplicatesIndex.isIndexedFragment(frag, cost, profile, state)) {
          return;
        }

        ProgressManager.checkCanceled();
        FileBasedIndex.getInstance().processValues(DuplicatesIndex.NAME, hash, null, new FileBasedIndex.ValueProcessor<TIntArrayList>() {
          final ProjectFileIndex myProjectFileIndex = ProjectFileIndex.SERVICE.getInstance(psiFile.getProject());
          @Override
          public boolean process(final VirtualFile file, final TIntArrayList list) {
            for(int i = 0, len = list.size(); i < len; ++i) {
              ProgressManager.checkCanceled();

              int value = list.getQuick(i);

              if (myProjectFileIndex.isInSource(virtualFile) && !myProjectFileIndex.isInSource(file)) return true;
              if (!myProjectFileIndex.isInSource(virtualFile) && myProjectFileIndex.isInSource(file)) return true;
              final int startOffset = frag.getStartOffset();
              final int endOffset = frag.getEndOffset();
              if (file.equals(virtualFile) && value >= startOffset && value < endOffset) continue;

              PsiElement[] elements = frag.getElements();
              PsiElement target = elements[0];
              TextRange rangeInElement = null;
              if (elements.length > 1) {
                PsiElement firstElement = elements[0];
                target = firstElement.getParent();
                PsiElement lastElement = elements[elements.length - 1];
                rangeInElement = new TextRange(
                  elements[0].getStartOffsetInParent(),
                  lastElement.getStartOffsetInParent() + lastElement.getTextLength()
                );
              }

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
              reportedOffsetInOtherFiles.put(fragmentStartOffsetInteger, value);
              reportedPsi.put(fragmentStartOffsetInteger, target);
              fragmentSize.put(fragmentStartOffsetInteger, newFragmentSize);

              return false;
            }
            return true;
          }
        }, GlobalSearchScope.projectScope(psiFile.getProject()));
      }
    }, true).visitNode(psiFile);

    for(Map.Entry<Integer, TextRange> entry:reportedRanges.entrySet()) {
      final Integer offset = entry.getKey();
      // todo 3 statements constant
      if (fragmentSize.get(offset) < 3) continue;
      final VirtualFile file = reportedFiles.get(offset);
      String message = "Found duplicated code in " + file.getPath();

      PsiElement targetElement = reportedPsi.get(offset);
      TextRange rangeInElement = entry.getValue();
      final int offsetInOtherFile = reportedOffsetInOtherFiles.get(offset);

      LocalQuickFix fix = createNavigateToDupeFix(file, offsetInOtherFile);
      ProblemDescriptor descriptor = manager
        .createProblemDescriptor(targetElement, rangeInElement, message, ProblemHighlightType.WEAK_WARNING, isOnTheFly, fix);
      descriptors.add(descriptor);
    }

    return descriptors.isEmpty() ? null : descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  protected LocalQuickFix createNavigateToDupeFix(@NotNull VirtualFile file, int offsetInOtherFile) {
    return null;
  }
}
