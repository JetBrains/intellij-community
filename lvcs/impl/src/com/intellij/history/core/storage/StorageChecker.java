package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageChecker {
  private static final Logger LOG = Logger.getInstance("#" + StorageChecker.class.getName());

  public static void main(String[] args) {
    final File dir = new File("C:/temp/local_history_mine");
    FileUtil.delete(new File(dir, ".broken"));

    Storage s = new Storage(dir);

    checkIntegrity(s);

    System.out.println("");
  }

  public static void checkIntegrity(Storage s) {
    LocalVcs.Memento memento = s.load();
    List<String> badContents = new ArrayList<String>();

    long before = System.currentTimeMillis();

    long entries = checkEntryContent(s, memento.myRoot, badContents);
    long changes = checkChangesContent(s, memento.myRoot, memento.myChangeList, badContents);

    long after = System.currentTimeMillis();

    LOG.info("Local history validated in " + (after - before) / 1000 + " seconds\n" +
             "  file contents: " + entries + "\n" +
             "  change contents: " + changes);

    if (badContents.isEmpty()) return;

    String formatted = StringUtil.join(badContents, "\n");
    LOG.warn("Invalid contents:\n" + formatted);
  }

  private static long checkEntryContent(Storage s, Entry e, List<String> badContents) {
    if (e.isDirectory()) {
      int result = 0;
      for (Entry child : e.getChildren()) {
        result += checkEntryContent(s, child, badContents);
      }
      return result;
    }

    checkContent(s, badContents, e.getContent(), " Path: " + e.getPath());
    return 1;
  }

  private static long checkChangesContent(Storage s, Entry root, ChangeList cl, List<String> badContents) {
    final Set<Content> allContents = new HashSet<Content>();

    try {
      cl.accept(root, new ChangeVisitor() {
        @Override
        public void visit(StructuralChange c) {
          allContents.addAll(c.getContentsToPurge());
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }


    for (Content c : allContents) {
      checkContent(s, badContents, c, "");
    }

    return allContents.size();
  }

  private static void checkContent(Storage s, List<String> badContents, Content c, String location) {
    if (!(c instanceof StoredContent)) return;

    StoredContent sc = (StoredContent)c;
    try {
      s.getContentStorage().load(sc.getId());
    }
    catch (BrokenStorageException ex) {
      badContents.add("ContentId: " + sc.getId() + location);
    }
  }
}
