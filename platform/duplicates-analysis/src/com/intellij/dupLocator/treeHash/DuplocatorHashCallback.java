package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.DupInfo;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocatorState;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DuplocatorHashCallback implements FragmentsCollector {
  private static final Logger LOG = Logger.getInstance(DuplocatorHashCallback.class);

  private TIntObjectHashMap<List<List<PsiFragment>>> myDuplicates;
  private final int myBound;
  private boolean myReadOnly = false;
  private final int myDiscardCost;

  public DuplocatorHashCallback(int bound, int discardCost) {
    myDuplicates = new TIntObjectHashMap<>();
    myBound = bound;
    myDiscardCost = discardCost;
  }

  @SuppressWarnings("UnusedDeclaration")
  public DuplocatorHashCallback(final int bound, final int discardCost, final boolean readOnly) {
    this(bound, discardCost);
    myReadOnly = readOnly;
  }

  public DuplocatorHashCallback(int lowerBound) {
    this(lowerBound, 0);
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setReadOnly(final boolean readOnly) {
    myReadOnly = readOnly;
  }

  // used in TeamCity
  @SuppressWarnings("UnusedParameters")
  public void add(int hash, int cost, PsiFragment frag, NodeSpecificHasher visitor) {
    forceAdd(hash, cost, frag);
  }

  private void forceAdd(int hash, int cost, PsiFragment frag) {
    if (frag == null) { //fake fragment
      myDuplicates.put(hash, new ArrayList<>());
      return;
    }

    frag.setCost(cost);

    List<List<PsiFragment>> fragments = myDuplicates.get(hash);

    if (fragments == null) {
      //do not add new hashcodes
      if (!myReadOnly) {
        List<List<PsiFragment>> list = new ArrayList<>();
        List<PsiFragment> listf = new ArrayList<>();

        listf.add(frag);
        list.add(listf);

        myDuplicates.put(hash, list);
      }

      return;
    }

    boolean found = false;

    final PsiElement[] elements = frag.getElements();

    int discardCost = 0;

    if (myDiscardCost >= 0) {
      discardCost = myDiscardCost;
    }
    else {
      final DuplocatorState state = DuplocatorUtil.getDuplocatorState(frag);
      if (state != null) {
        discardCost = state.getDiscardCost();
      }
    }

    for (Iterator<List<PsiFragment>> i = fragments.iterator(); i.hasNext() && !found; ) {
      List<PsiFragment> fi = i.next();
      PsiFragment aFrag = fi.get(0);

      if (aFrag.isEqual(elements, discardCost)) {
        boolean skipNew = false;

        for (Iterator<PsiFragment> frags = fi.iterator(); frags.hasNext() && !skipNew; ) {
          final PsiFragment old = frags.next();
          if (frag.intersectsWith(old)) {
            if (old.getCost() < frag.getCost() || frag.contains(old)) {
              frags.remove();
            } else {
              skipNew = true;
            }
          }
        }

        if (!skipNew) fi.add(frag);

        found = true;
      }
    }

    if (!found) {
      List<PsiFragment> newFrags = new ArrayList<>();
      newFrags.add(frag);

      fragments.add(newFrags);
    }
  }

  @Override
  public void add(int hash, int cost, PsiFragment frag) {
    int bound;

    if (myBound >= 0) {
      bound = myBound;
    }
    else {
      final DuplocatorState duplocatorState = DuplocatorUtil.getDuplocatorState(frag);
      if (duplocatorState == null) {
        return;
      }
      bound = duplocatorState.getLowerBound();
    }

    if (cost >= bound) {
      forceAdd(hash, cost, frag);
    }
  }

  public DupInfo getInfo() {
    Object2IntOpenHashMap<PsiFragment[]> duplicateList = new Object2IntOpenHashMap<>();
    myDuplicates.forEachEntry(new TIntObjectProcedure<List<List<PsiFragment>>>() {
      @Override
      public boolean execute(final int hash, final List<List<PsiFragment>> listList) {
        for (List<PsiFragment> list : listList) {
          final int len = list.size();
          if (len > 1) {
            PsiFragment[] filtered = new PsiFragment[len];
            int idx = 0;
            for (final PsiFragment fragment : list) {
              fragment.markDuplicate();
              filtered[idx++] = fragment;
            }
            duplicateList.put(filtered, hash);
          }
        }

        return true;
      }
    });

    myDuplicates = null;

    for (ObjectIterator<Object2IntMap.Entry<PsiFragment[]>> iterator = duplicateList.object2IntEntrySet().fastIterator(); iterator.hasNext(); ) {
      Object2IntMap.Entry<PsiFragment[]> entry = iterator.next();
      PsiFragment[] fragments = entry.getKey();
      LOG.assertTrue(fragments.length > 1);
      boolean nested = false;
      for (PsiFragment fragment : fragments) {
        if (fragment.isNested()) {
          nested = true;
          break;
        }
      }

      if (nested) {
        iterator.remove();
      }
    }

    PsiFragment[][] duplicates = duplicateList.keySet().toArray(new PsiFragment[][]{});
    Arrays.sort(duplicates, (x, y) -> y[0].getCost() - x[0].getCost());

    return new DupInfo() {
      private final TIntObjectHashMap<GroupNodeDescription> myPattern2Description = new TIntObjectHashMap<>();

      @Override
      public int getPatterns() {
        return duplicates.length;
      }

      @Override
      public int getPatternCost(int number) {
        return ((PsiFragment[])duplicates[number])[0].getCost();
      }

      @Override
      public int getPatternDensity(int number) {
        return duplicates[number].length;
      }

      @Override
      public PsiFragment[] getFragmentOccurences(int pattern) {
        return duplicates[pattern];
      }

      @Override
      public UsageInfo[] getUsageOccurences(int pattern) {
        PsiFragment[] occs = getFragmentOccurences(pattern);
        UsageInfo[] infos = new UsageInfo[occs.length];

        for (int i = 0; i < infos.length; i++) {
          infos[i] = occs[i].getUsageInfo();
        }

        return infos;
      }

      @Override
      public int getFileCount(final int pattern) {
        if (myPattern2Description.containsKey(pattern)) {
          return myPattern2Description.get(pattern).getFilesCount();
        }
        return cacheGroupNodeDescription(pattern).getFilesCount();
      }

      private GroupNodeDescription cacheGroupNodeDescription(final int pattern) {
        final Set<PsiFile> files = new HashSet<>();
        final PsiFragment[] occurencies = getFragmentOccurences(pattern);
        for (PsiFragment occurency : occurencies) {
          final PsiFile file = occurency.getFile();
          if (file != null) {
            files.add(file);
          }
        }
        final int fileCount = files.size();
        final PsiFile psiFile = occurencies[0].getFile();
        DuplicatesProfile profile = DuplicatesProfile.findProfileForDuplicate(this, pattern);
        String comment = profile != null ? profile.getComment(this, pattern) : "";
        final GroupNodeDescription description = new GroupNodeDescription(fileCount, psiFile != null ? psiFile.getName() : "unknown", comment);
        myPattern2Description.put(pattern, description);
        return description;
      }

      @Override
      @Nullable
      public String getTitle(int pattern) {
        if (getFileCount(pattern) == 1) {
          if (myPattern2Description.containsKey(pattern)) {
            return myPattern2Description.get(pattern).getTitle();
          }
          return cacheGroupNodeDescription(pattern).getTitle();
        }
        return null;
      }

      @Override
      @Nullable
      public String getComment(int pattern) {
        if (getFileCount(pattern) == 1) {
          if (myPattern2Description.containsKey(pattern)) {
            return myPattern2Description.get(pattern).getComment();
          }
          return cacheGroupNodeDescription(pattern).getComment();
        }
        return null;
      }

      @Override
      public int getHash(final int i) {
        return duplicateList.getInt(duplicates[i]);
      }
    };
  }

  public void report(@NotNull Path dir, @NotNull Project project) throws IOException {
    int[] hashCodes = myDuplicates.keys();
    //fragments
    try (BufferedWriter fileWriter = Files.newBufferedWriter(dir.resolve("fragments.xml"))) {
      HierarchicalStreamWriter writer = new PrettyPrintWriter(fileWriter);
      writer.startNode("root");
      for (int hash : hashCodes) {
        List<List<PsiFragment>> dupList = myDuplicates.get(hash);
        writer.startNode("hash");
        writer.addAttribute("val", String.valueOf(hash));
        for (final List<PsiFragment> psiFragments : dupList) {
          writeFragments(psiFragments, writer, project, false);
        }
        writer.endNode();
      }
      writer.endNode(); //root node
      writer.flush();
    }

    writeDuplicates(dir, project, getInfo());
  }

  //duplicates
  public static void writeDuplicates(@NotNull Path dir, @NotNull Project project, DupInfo info) throws IOException {
    try (BufferedWriter fileWriter = Files.newBufferedWriter(dir.resolve("duplicates.xml"))) {
      HierarchicalStreamWriter writer = new PrettyPrintWriter(fileWriter);
      writer.startNode("root");
      final int patterns = info.getPatterns();
      for (int i = 0; i < patterns; i++) {
        writer.startNode("duplicate");
        writer.addAttribute("cost", String.valueOf(info.getPatternCost(i)));
        writer.addAttribute("hash", String.valueOf(info.getHash(i)));
        writeFragments(Arrays.asList(info.getFragmentOccurences(i)), writer, project, true);
        writer.endNode();
      }
      writer.endNode(); //root node
      writer.flush();
    }
  }

  private static void writeFragments(List<? extends PsiFragment> psiFragments,
                                     HierarchicalStreamWriter writer,
                                     @NotNull Project project,
                                     boolean shouldWriteOffsets) {
    final PathMacroManager macroManager = PathMacroManager.getInstance(project);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

    for (PsiFragment fragment : psiFragments) {
      final PsiFile psiFile = fragment.getFile();
      final VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
      if (virtualFile != null) {
        writer.startNode("fragment");
        writer.addAttribute("file", macroManager.collapsePath(virtualFile.getUrl()));
        if (shouldWriteOffsets) {
          final Document document = documentManager.getDocument(psiFile);
          LOG.assertTrue(document != null);
          int startOffset = fragment.getStartOffset();
          final int line = document.getLineNumber(startOffset);
          writer.addAttribute("line", String.valueOf(line));
          final int lineStartOffset = document.getLineStartOffset(line);
          if (StringUtil.isEmptyOrSpaces(document.getText().substring(lineStartOffset, startOffset))) {
            startOffset = lineStartOffset;
          }
          writer.addAttribute("start", String.valueOf(startOffset));
          writer.addAttribute("end", String.valueOf(fragment.getEndOffset()));
          if (fragment.containsMultipleFragments()) {
            final int[][] offsets = fragment.getOffsets();
            for (int[] offset : offsets) {
              writer.startNode("offset");
              writer.addAttribute("start", String.valueOf(offset[0]));
              writer.addAttribute("end", String.valueOf(offset[1]));
              writer.endNode();
            }
          }
        }
        writer.endNode();
      }
    }
  }
}
