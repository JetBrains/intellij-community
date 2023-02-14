package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.*;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// used by TeamCity plugin
@ApiStatus.NonExtendable
public class DuplocatorHashCallback implements FragmentsCollector {
  private static final Logger LOG = Logger.getInstance(DuplocatorHashCallback.class);

  private Int2ObjectMap<List<List<PsiFragment>>> myDuplicates = new Int2ObjectOpenHashMap<>();
  private final int myBound;
  private boolean myReadOnly = false;
  private final int myDiscardCost;

  public DuplocatorHashCallback(int bound, int discardCost) {
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
      // do not add new hash codes
      if (!myReadOnly) {
        List<List<PsiFragment>> list = new ArrayList<>();
        List<PsiFragment> listF = new ArrayList<>();
        listF.add(frag);
        list.add(listF);
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
    Object2IntMap<PsiFragment[]> duplicateList = new Object2IntOpenHashMap<>();
    for (Int2ObjectMap.Entry<List<List<PsiFragment>>> entry : myDuplicates.int2ObjectEntrySet()) {
      for (List<PsiFragment> list : entry.getValue()) {
        int len = list.size();
        if (len > 1) {
          PsiFragment[] filtered = new PsiFragment[len];
          int idx = 0;
          for (PsiFragment fragment : list) {
            fragment.markDuplicate();
            filtered[idx++] = fragment;
          }
          duplicateList.put(filtered, entry.getIntKey());
        }
      }
    }

    myDuplicates = null;

    for (ObjectIterator<Object2IntMap.Entry<PsiFragment[]>> iterator = duplicateList.object2IntEntrySet().iterator(); iterator.hasNext(); ) {
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
      private final Int2ObjectMap<GroupNodeDescription> myPattern2Description = new Int2ObjectOpenHashMap<>();

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
        PsiFragment[] occurrences = getFragmentOccurences(pattern);
        UsageInfo[] infos = new UsageInfo[occurrences.length];
        for (int i = 0; i < infos.length; i++) {
          infos[i] = occurrences[i].getUsageInfo();
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
        final PsiFragment[] occurrences = getFragmentOccurences(pattern);
        for (PsiFragment occurrence : occurrences) {
          final PsiFile file = occurrence.getFile();
          if (file != null) {
            files.add(file);
          }
        }
        final int fileCount = files.size();
        final PsiFile psiFile = occurrences[0].getFile();
        DuplicatesProfile profile = DuplicatesProfile.findProfileForDuplicate(this, pattern);
        String comment = profile != null ? profile.getComment(this, pattern) : "";
        String filename = psiFile != null ? psiFile.getName() : DupLocatorBundle.message("duplicates.unknown.file.node.title");
        final GroupNodeDescription description = new GroupNodeDescription(fileCount, filename, comment);
        myPattern2Description.put(pattern, description);
        return description;
      }

      @Override
      public @Nullable @Nls String getTitle(int pattern) {
        if (getFileCount(pattern) == 1) {
          if (myPattern2Description.containsKey(pattern)) {
            return myPattern2Description.get(pattern).getTitle();
          }
          return cacheGroupNodeDescription(pattern).getTitle();
        }
        return null;
      }

      @Override
      public @Nullable @Nls String getComment(int pattern) {
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

  public void report(@NotNull Path dir, @NotNull Project project) throws IOException, XMLStreamException {
    int[] hashCodes = myDuplicates.keySet().toIntArray();
    //fragments
    try (BufferedWriter fileWriter = Files.newBufferedWriter(dir.resolve("fragments.xml"))) {
      XMLStreamWriter writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(fileWriter);
      writer.writeStartElement("root");
      for (int hash : hashCodes) {
        List<List<PsiFragment>> dupList = myDuplicates.get(hash);
        writer.writeStartElement("hash");
        writer.writeAttribute("val", String.valueOf(hash));
        for (final List<PsiFragment> psiFragments : dupList) {
          writeFragments(psiFragments, writer, project, false);
        }
        writer.writeEndElement();
      }
      writer.writeEndElement(); //root node
      writer.flush();
    }

    writeDuplicates(dir, project, getInfo());
  }

  //duplicates
  public static void writeDuplicates(@NotNull Path dir, @NotNull Project project, DupInfo info) throws IOException, XMLStreamException {
    try (BufferedWriter fileWriter = Files.newBufferedWriter(dir.resolve("duplicates.xml"))) {
      XMLStreamWriter writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(fileWriter);
      writer.writeStartElement("root");
      final int patterns = info.getPatterns();
      for (int i = 0; i < patterns; i++) {
        writer.writeStartElement("duplicate");
        writer.writeAttribute("cost", String.valueOf(info.getPatternCost(i)));
        writer.writeAttribute("hash", String.valueOf(info.getHash(i)));
        writeFragments(Arrays.asList(info.getFragmentOccurences(i)), writer, project, true);
        writer.writeEndElement();
      }
      writer.writeEndElement(); //root node
      writer.flush();
    }
  }

  private static void writeFragments(List<? extends PsiFragment> psiFragments,
                                     XMLStreamWriter writer,
                                     @NotNull Project project,
                                     boolean shouldWriteOffsets) throws XMLStreamException {
    final PathMacroManager macroManager = PathMacroManager.getInstance(project);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

    for (PsiFragment fragment : psiFragments) {
      final PsiFile psiFile = fragment.getFile();
      final VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
      if (virtualFile != null) {
        writer.writeStartElement("fragment");
        writer.writeAttribute("file", macroManager.collapsePath(virtualFile.getUrl()));
        if (shouldWriteOffsets) {
          final Document document = documentManager.getDocument(psiFile);
          LOG.assertTrue(document != null);
          int startOffset = fragment.getStartOffset();
          final int line = document.getLineNumber(startOffset);
          writer.writeAttribute("line", String.valueOf(line));
          final int lineStartOffset = document.getLineStartOffset(line);
          if (Strings.isEmptyOrSpaces(document.getText().substring(lineStartOffset, startOffset))) {
            startOffset = lineStartOffset;
          }
          writer.writeAttribute("start", String.valueOf(startOffset));
          writer.writeAttribute("end", String.valueOf(fragment.getEndOffset()));
          if (fragment.containsMultipleFragments()) {
            final int[][] offsets = fragment.getOffsets();
            for (int[] offset : offsets) {
              writer.writeStartElement("offset");
              writer.writeAttribute("start", String.valueOf(offset[0]));
              writer.writeAttribute("end", String.valueOf(offset[1]));
              writer.writeEndElement();
            }
          }
        }
        writer.writeEndElement();
      }
    }
  }
}
