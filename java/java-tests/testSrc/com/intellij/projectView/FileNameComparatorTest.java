// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectView;

import com.intellij.ide.util.treeView.FileNameComparator;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileNameComparatorTest extends TestCase {
  private static final List<String> PATHS = Arrays.asList(
    "",
    ".txt",
    "/",
    "/A/Test1.txt",
    "/a/Test1.txt",
    "/A/Test1.txt_1",
    "/a/Test1.txt_1",
    "/A/Test1.txt_12",
    "/a/Test1.txt_12",
    "/aaa/",
    "/aaa-qwe/",
    "/B/Test1.txt_2",
    "/b/Test1.txt_2",
    "/B/Test1.txt_22",
    "/b/Test1.txt_22",
    "/folder/AAA",
    "/folder/aAa",
    "/folder/aaa",
    "/folder/aAa/",
    "/folder/aaa/",
    "/folder/aaa/",
    "/folder/aaA/.gitignore",
    "/folder/aaa/.gitignore",
    "/folder/aAa/qwerty/",
    "/folder/aaa/qwerty/",
    "/folder/Aaa-qwerty/",
    "/folder/aaa-qwerty/",
    "/folder/aAa-qwerty/qwerty",
    "/folder/aaa-qwerty/qwerty",
    "/folder/abd",
    "/folder/abd/",
    "/folder/abx",
    "/folder/abx/",
    "/TEST1",
    "/Test1",
    "/Test1",
    "/Test1!12.txt",
    "/Test1+12.txt",
    "/Test1.1.txt",
    "/Test1.1.txt/A/",
    "/Test1.1.txt/a/",
    "/Test1.1.txt/B/",
    "/Test1.12.txt",
    "/Test1.txt",
    "/Test1.TXT 1",
    "/Test1.txt 2",
    "/Test1.txt 12",
    "/Test1.TXT 22",
    "/Test1.txt-1",
    "/Test1.TXT-2",
    "/Test1.txt-12",
    "/Test1.TXT-22",
    "/Test1.TXT_1",
    "/Test1.txt_2",
    "/Test1.txt_12",
    "/Test1.TXT_22",
    "/Test1/12.txt",
    "/TEST1 1.txt",
    "/TEST1 2.txt",
    "/Test1 12.txt",
    "/Test1 12.txt/A/",
    "/Test1 12.txt/a/",
    "/Test1 12.txt/b/",
    "/Test1-2.txt",
    "/Test1-2.txt/A/",
    "/Test1-2.txt/a/",
    "/Test1-2.txt/b/",
    "/Test1-12.txt",
    "/Test1-12.txt",
    "/Test1-12.txt/",
    "/Test1-12.txt/A/",
    "/Test1-12.txt/a/",
    "/Test1-12.txt/B/",
    "/Test1_1.txt",
    "/Test1_2.txt",
    "/Test1_12.txt",
    "/Test1A12.txt",
    "/ZZ.java",
    "/~",
    "/~/",
    " b.java",
    "a",
    "a.",
    "A.java",
    "a.java",
    "a-",
    "a-a.java",
    "a-b.java",
    "a_",
    "ab.java",
    "b",
    "b.",
    "b-",
    "b_",
    "xxxxx",
    "xxxxx.yyyyy",
    "xxxxx.yyyyyzzzzz",
    "xxxxx ",
    "xxxxx yyyyy",
    "xxxxx yyyyyzzzzz",
    "xxxxx0",
    "xxxxx1",
    "xxxxx01",
    "xxxxx2",
    "xxxxx10",
    "xxxxx12",
    "xxxxx-",
    "xxxxx-yyyyy",
    "xxxxx-yyyyyzzzzz",
    "xxxxx_",
    "xxxxx_yyyyy",
    "xxxxx_yyyyyzzzzz",
    "xxxxxAyyyyy",
    "xxxxxAyyyyyzzzzz",
    "xxxxxByyyyy",
    "xxxxxByyyyyzzzzz",
    "~",
    "~/project/A.java",
    "~/project/a.java",
    "~/project/aaa",
    "~/project/aaa",
    "~/project/aaa/",
    "~/project/aaa a",
    "~/project/aaa a/",
    "~/project/aaa a/bb",
    "~/project/aaa-qWe",
    "~/project/aaa-qwE",
    "~/project/aaa-qwe",
    "~/project/aaa-qWe/",
    "~/project/aaa-qwE/",
    "~/project/aaa-qwe/",
    "~/project/aaa-qwe/A.java",
    "~/project/B.java",
    "~/project/B.java",
    "~/project/b.java",
    "~/project/dir/A.java",
    "~/project/dir/B.java",
    "~/project/dir/subdir/A.java",
    "~/project/Z.java",
    "~/project/z.java",
    "~/project/zzz",
    "~/project/zzz/",
    "~/project/zzz-qwe.java",
    "~/project/zzz-qwe.java/",
    "~/project/zzz-qwe.java/test"
  );

  public void testOrder() {
    ArrayList<String> shuffled = new ArrayList<>(PATHS);
    Collections.shuffle(shuffled);
    List<String> sortedPaths = ContainerUtil.sorted(shuffled, FileNameComparator.INSTANCE);
    UsefulTestCase.assertOrderedEquals(sortedPaths, PATHS);
  }

  public void testTransitive1() {
    PlatformTestUtil.assertComparisonContractNotViolated(PATHS,
                                                         FileNameComparator.INSTANCE,
                                                         (path1, path2) -> path1.equals(path2));
  }

  public void testTransitive2() {
    List<String> tokens = Arrays.asList("a", "b", "-", "_", "!", " ", ".", "~" /*, "1", "2", "0"*/);
    List<String> names = new ArrayList<>();

    for (String t1 : tokens) {
      names.add(t1);
      for (String t2 : tokens) {
        names.add(t1 + t2);
        for (String t3 : tokens) {
          names.add(t1 + t2 + t3);
          //for (String t4 : tokens) {
          //  names.add(t1 + t2 + t3 + t4);
          //}
        }
      }
    }
    //System.out.println("Inputs: " + names.size());
    //System.out.println("Pairs to check: " + names.size() * names.size() * names.size());

    PlatformTestUtil.assertComparisonContractNotViolated(names,
                                                         FileNameComparator.INSTANCE,
                                                         (path1, path2) -> path1.equals(path2));
  }
}
