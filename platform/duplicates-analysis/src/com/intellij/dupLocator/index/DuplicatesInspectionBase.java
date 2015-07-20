package com.intellij.dupLocator.index;

import com.intellij.codeInspection.*;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocatorState;
import com.intellij.dupLocator.LightDuplicateProfile;
import com.intellij.dupLocator.treeHash.FragmentsCollector;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
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
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class DuplicatesInspectionBase extends LocalInspectionTool {
  private static final int MIN_FRAGMENT_SIZE = 3;

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile psiFile, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (!(virtualFile instanceof VirtualFileWithId) || /*!isOnTheFly || */!DuplicatesIndex.ourEnabled) return ProblemDescriptor.EMPTY_ARRAY;
    final DuplicatesProfile profile = DuplicatesIndex.findDuplicatesProfile(psiFile.getFileType());
    if (profile == null) return ProblemDescriptor.EMPTY_ARRAY;

    final Ref<DuplicatedCodeProcessor> myProcessorRef = new Ref<DuplicatedCodeProcessor>();

    final FileASTNode node = psiFile.getNode();
    boolean usingLightProfile = profile instanceof LightDuplicateProfile &&
                                node.getElementType() instanceof ILightStubFileElementType &&
                                DuplicatesIndex.ourEnabledLightProfiles;
    if (usingLightProfile) {
      LighterAST ast = node.getLighterAST();
      assert ast != null;
      ((LightDuplicateProfile)profile).process(ast, new LightDuplicateProfile.Callback() {
        DuplicatedCodeProcessor<LighterASTNode> myProcessor;
        @Override
        public void process(@NotNull final LighterAST ast, @NotNull final LighterASTNode node, int hash) {
          class LightDuplicatedCodeProcessor extends DuplicatedCodeProcessor<LighterASTNode> {

            LightDuplicatedCodeProcessor(VirtualFile file, Project project) {
              super(file, project);
            }

            @Override
            protected TextRange getRangeInElement(LighterASTNode node) {
              return null;
            }

            @Override
            protected PsiElement getPsi(LighterASTNode node) {
              return ((TreeBackedLighterAST)ast).unwrap(node).getPsi();
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
          if (myProcessor == null) {
            myProcessor = new LightDuplicatedCodeProcessor(virtualFile, psiFile.getProject());
            myProcessorRef.set(myProcessor);
          }
          myProcessor.process(hash, node);
        }
      });
    } else {
      final DuplocatorState state = profile.getDuplocatorState(psiFile.getLanguage());
      profile.createVisitor(new FragmentsCollector() {
        DuplicatedCodeProcessor<PsiFragment> myProcessor;
        @Override
        public void add(int hash, final int cost, @Nullable final PsiFragment frag) {
          if (!DuplicatesIndex.isIndexedFragment(frag, cost, profile, state)) {
            return;
          }

          class OldDuplicatedCodeProcessor extends DuplicatedCodeProcessor<PsiFragment> {

            OldDuplicatedCodeProcessor(VirtualFile file, Project project) {
              super(file, project);
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
          if (myProcessor == null) {
            myProcessor = new OldDuplicatedCodeProcessor(virtualFile, psiFile.getProject());
            myProcessorRef.set(myProcessor);
          }
          myProcessor.process(hash, frag);
        }
      }, true).visitNode(psiFile);
    }

    DuplicatedCodeProcessor<?> processor = myProcessorRef.get();

    final SmartList<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();

    if (processor != null) {
      for(Map.Entry<Integer, TextRange> entry:processor.reportedRanges.entrySet()) {
        final Integer offset = entry.getKey();
        // todo 3 statements constant
        if (!usingLightProfile && processor.fragmentSize.get(offset) < MIN_FRAGMENT_SIZE) continue;
        final VirtualFile file = processor.reportedFiles.get(offset);
        String message = "Found duplicated code in " + file.getPath();

        PsiElement targetElement = processor.reportedPsi.get(offset);
        TextRange rangeInElement = entry.getValue();
        final int offsetInOtherFile = processor.reportedOffsetInOtherFiles.get(offset);

        LocalQuickFix fix = createNavigateToDupeFix(file, offsetInOtherFile);
        int hash = processor.fragmentHash.get(offset);

        LocalQuickFix viewAllDupesFix = hash != 0 ? createShowOtherDupesFix(virtualFile, offset, hash, psiFile.getProject()) : null;

        ProblemDescriptor descriptor = manager
          .createProblemDescriptor(targetElement, rangeInElement, message, ProblemHighlightType.WEAK_WARNING, isOnTheFly, fix, viewAllDupesFix);
        descriptors.add(descriptor);
      }
    }

    return descriptors.isEmpty() ? null : descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  protected LocalQuickFix createNavigateToDupeFix(@NotNull VirtualFile file, int offsetInOtherFile) {
    return null;
  }
  protected LocalQuickFix createShowOtherDupesFix(VirtualFile file, int offset, int hash, Project project) {
    return null;
  }

  static abstract class DuplicatedCodeProcessor<T> implements FileBasedIndex.ValueProcessor<TIntArrayList> {
    final TreeMap<Integer, TextRange> reportedRanges = new TreeMap<Integer, TextRange>();
    final TIntObjectHashMap<VirtualFile> reportedFiles = new TIntObjectHashMap<VirtualFile>();
    final TIntObjectHashMap<PsiElement> reportedPsi = new TIntObjectHashMap<PsiElement>();
    final TIntIntHashMap reportedOffsetInOtherFiles = new TIntIntHashMap();
    final TIntIntHashMap fragmentSize = new TIntIntHashMap();
    final TIntIntHashMap fragmentHash = new TIntIntHashMap();
    final VirtualFile virtualFile;
    final Project project;
    final ProjectFileIndex myProjectFileIndex;
    T myNode;
    int myHash;

    DuplicatedCodeProcessor(VirtualFile file, Project project) {
      virtualFile = file;
      this.project = project;
      myProjectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    }

    void process(int hash, T node) {
      ProgressManager.checkCanceled();
      myNode = node;
      myHash = hash;
      FileBasedIndex.getInstance().processValues(DuplicatesIndex.NAME, hash, null, this, GlobalSearchScope.projectScope(project));
    }

    @Override
    public boolean process(VirtualFile file, TIntArrayList list) {
      for(int i = 0, len = list.size(); i < len; ++i) {
        ProgressManager.checkCanceled();

        int value = list.getQuick(i);

        if (myProjectFileIndex.isInSource(virtualFile) && !myProjectFileIndex.isInSource(file)) return true;
        if (!myProjectFileIndex.isInSource(virtualFile) && myProjectFileIndex.isInSource(file)) return true;
        final int startOffset = getStartOffset(myNode);
        final int endOffset = getEndOffset(myNode);
        if (file.equals(virtualFile) && value >= startOffset && value < endOffset) continue;

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
        reportedOffsetInOtherFiles.put(fragmentStartOffsetInteger, value);
        reportedPsi.put(fragmentStartOffsetInteger, target);
        fragmentSize.put(fragmentStartOffsetInteger, newFragmentSize);
        if (newFragmentSize >= MIN_FRAGMENT_SIZE || isLightProfile()) fragmentHash.put(fragmentStartOffsetInteger, myHash);
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
