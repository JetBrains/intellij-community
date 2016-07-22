package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.*;
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
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 26, 2004
 * Time: 7:11:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class DuplocatorHashCallback implements FragmentsCollector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.dupLocator.treeHash.DuplocatorHashCallback");

  private TIntObjectHashMap<List<List<PsiFragment>>> myDuplicates;
  private final int myBound;
  private boolean myReadOnly = false;
  private final int myDiscardCost;

  public DuplocatorHashCallback(int bound, int discardCost) {
    myDuplicates = new TIntObjectHashMap<>();
    myBound = bound;
    myDiscardCost = discardCost;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public DuplocatorHashCallback(final int bound, final int discardCost, final boolean readOnly) {
    this(bound, discardCost);
    myReadOnly = readOnly;
  }

  public DuplocatorHashCallback(int lowerBound) {
    this(lowerBound, 0);
  }

  @SuppressWarnings({"UnusedDeclaration"})
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
      if (!myReadOnly) { //do not add new hashcodes
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
    final TObjectIntHashMap<PsiFragment[]> duplicateList = new TObjectIntHashMap<>();

    myDuplicates.forEachEntry(new TIntObjectProcedure<List<List<PsiFragment>>>() {
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

    for (TObjectIntIterator<PsiFragment[]> dups = duplicateList.iterator(); dups.hasNext(); ) {
      dups.advance();
      PsiFragment[] fragments = dups.key();
      LOG.assertTrue(fragments.length > 1);
      boolean nested = false;
      for (PsiFragment fragment : fragments) {
        if (fragment.isNested()) {
          nested = true;
          break;
        }
      }

      if (nested) {
        dups.remove();
      }
    }

    final Object[] duplicates = duplicateList.keys();

    Arrays.sort(duplicates, (x, y) -> ((PsiFragment[])y)[0].getCost() - ((PsiFragment[])x)[0].getCost());

    return new DupInfo() {
      private final TIntObjectHashMap<GroupNodeDescription> myPattern2Description = new TIntObjectHashMap<>();

      public int getPatterns() {
        return duplicates.length;
      }

      public int getPatternCost(int number) {
        return ((PsiFragment[])duplicates[number])[0].getCost();
      }

      public int getPatternDensity(int number) {
        return ((PsiFragment[])duplicates[number]).length;
      }

      public PsiFragment[] getFragmentOccurences(int pattern) {
        return (PsiFragment[])duplicates[pattern];
      }

      public UsageInfo[] getUsageOccurences(int pattern) {
        PsiFragment[] occs = getFragmentOccurences(pattern);
        UsageInfo[] infos = new UsageInfo[occs.length];

        for (int i = 0; i < infos.length; i++) {
          infos[i] = occs[i].getUsageInfo();
        }

        return infos;
      }

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
        DuplicatesProfile profile = DuplicatesProfileCache.getProfile(this, pattern);
        String comment = profile != null ? profile.getComment(this, pattern) : "";
        final GroupNodeDescription description = new GroupNodeDescription(fileCount, psiFile != null ? psiFile.getName() : "unknown", comment);
        myPattern2Description.put(pattern, description);
        return description;
      }

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

      public int getHash(final int i) {
        return duplicateList.get((PsiFragment[])duplicates[i]);
      }
    };
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void report(String path, final Project project) throws IOException {
    int[] hashCodes = myDuplicates.keys();
    FileWriter fileWriter = null;
    //fragments
    try {
      fileWriter = new FileWriter(path + File.separator + "fragments.xml");
      PrettyPrintWriter writer = new PrettyPrintWriter(fileWriter);
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
    finally {
      if (fileWriter != null) {
        fileWriter.close();
      }
    }

    fileWriter = null;
    //duplicates
    try {
      fileWriter = new FileWriter(path + File.separator + "duplicates.xml");
      PrettyPrintWriter writer = new PrettyPrintWriter(fileWriter);
      writer.startNode("root");
      final DupInfo info = getInfo();
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
    finally {
      if (fileWriter != null) {
        fileWriter.close();
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void writeFragments(final List<PsiFragment> psiFragments,
                                     final PrettyPrintWriter writer,
                                     Project project,
                                     final boolean shouldWriteOffsets) {
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
