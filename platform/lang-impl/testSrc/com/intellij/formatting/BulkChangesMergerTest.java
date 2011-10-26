/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.editor.impl.BulkChangesMerger;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 12/22/2010
 */
public class BulkChangesMergerTest {

  @Rule
  public TestWatcher configReader = new TestWatcher() {

    @Override
    protected void starting(Description description) {
      Config config = description.getAnnotation(Config.class);
      if (config != null) {
        myConfig = config;
      }
      else {
        try {
          myConfig = BulkChangesMergerTest.class.getMethod("dummy").getAnnotation(Config.class);
        }
        catch (NoSuchMethodException e) {
          throw new RuntimeException(e);
        }
      }
    }
  };

  @Config
  public static void dummy() {
  }
  
  private Config myConfig;
  private BulkChangesMerger myMerger;  
    
  @Before
  public void setUp() {
    myMerger = new BulkChangesMerger();
  }
  
  @Test
  public void simpleReplaceInTheMiddle() {
    doTest("abcd", "a123d", c("123", 1, 3));
  }
  
  @Test
  public void disjointInserts() {
    doTest("abcd", "a1b2c3d45", c("1", 1), c("2", 2), c("3", 3), c("45", 4));
  }
  
  @Config(initialTextLength = 4)
  @Test
  public void interestedSymbolsNumberLessThanAvailable() {
    doTest("abcdefg", "a12bc3d", c("12", 1), c("3", 3));
  }

  @Config(inplace = true)
  @Test
  public void inplaceZeroGroups() {
    doTest("0123456789", "a2bc4defg8", c("a", 0, 2), c("bc", 3, 4), c("defg", 5, 8), c("", 9, 10));
  }

  @Config(inplace = true)
  @Test
  public void inplaceGrowingGroupsWithLastPositive() {
    doTest("abcdefghijklmnopqrst", "acABdCjkDEFGHIJKLMpqrst", c("", 1, 2), c("AB", 3), c("C", 4, 9), c("DEFGHIJKLM", 11, 15));
  }

  @Config(inplace = true)
  @Test
  public void inplaceGrowingGroupsWithLastNegative() {
    doTest("abcdefghijk", "acABdCjk", c("", 1, 2), c("AB", 3), c("C", 4, 9));
  }

  @Config(inplace = true)
  @Test
  public void onlyGrowing() {
    doTest("0123456789", "0ab2c3defg67hijk9", c("ab", 1, 2), c("c", 3), c("defg", 4, 6), c("hijk", 8, 9));
  }

  @Config(inplace = true)
  @Test
  public void onlyShrinking() {
    doTest("0123456789", "0a35b9", c("a", 1, 3), c("", 4, 5), c("b", 6, 9));
  }

  @Config(inplace = true, dataArrayLength = 4)
  @Test(expected = IllegalArgumentException.class)
  public void insufficientLengthForInplaceMerge() {
    doTest("0123", "", c("", 1, 3), c("abc", 4));
  }

  @Config(inplace = true)
  @Test
  public void overlapWithPositiveGroupOnStart() {
    doTest("0123456789ABC", "0abc1358d9eBC", c("abc", 1), c("", 2, 3), c("", 4, 5), c("", 6, 8), c("d", 9), c("e", 10, 11));
  }

  //@Config(inplace = true)
  //@Test
  //public void client() {
  //  int arrayLength = 31486;
  //  int dataLength = 9552;
  //  char[] initial = new char[arrayLength];
  //  List<int[]> rawChanges = new ArrayList<int[]>();
  //  rawChanges.add(new int[] {6609, 8209, 14634});
  //  
  //  List<TextChangeImpl> changes = new ArrayList<TextChangeImpl>();
  //  for (int[] rawChange : rawChanges) {
  //    changes.add(new TextChangeImpl(StringUtil.repeatSymbol('a', rawChange[0]), rawChange[1], rawChange[2]));
  //  }
  //  myMerger.mergeInPlace(initial, dataLength, changes);
  //}
  
  private static TextChangeImpl c(String text, int offset) {
    return c(text, offset, offset);
  }
  
  private static TextChangeImpl c(String text, int start, int end) {
    return new TextChangeImpl(text, start, end);
  }
  
  private void doTest(String initial, String expected, TextChangeImpl ... changes) {
    if (myConfig.inplace()) {
      int diff = 0;
      for (TextChangeImpl change : changes) {
        diff += change.getDiff();
      }
      int outputUsefulLength = initial.length() + diff;
      int dataLength = myConfig.dataArrayLength();
      if (dataLength < 0) {
        dataLength = Math.max(outputUsefulLength, initial.length());
      }
      char[] data = new char[dataLength];
      System.arraycopy(initial.toCharArray(), 0, data, 0, initial.length());
      myMerger.mergeInPlace(data, initial.length(), Arrays.asList(changes));
      assertEquals(expected, new String(data, 0, outputUsefulLength));
    }
    else {
      int interestedSymbolsNumber = myConfig.initialTextLength();
      if (interestedSymbolsNumber < 0) {
        interestedSymbolsNumber = initial.length();
      }
      CharSequence actual = myMerger.mergeToCharSequence(initial.toCharArray(), interestedSymbolsNumber, Arrays.asList(changes));
      assertEquals(expected, actual.toString());
    }
  }
  
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  private @interface Config {
    boolean inplace() default false;
    int dataArrayLength() default -1;
    int initialTextLength() default -1;
  }
}
