package com.intellij.history.utils;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ReversedTest {
  @Test
  public void testReversion() {
    List<String> ss = new ArrayList<String>();
    ss.add("a");
    ss.add("b");
    ss.add("c");

    String log = "";
    for (String s : ss) {
      log += s;
    }

    for (String s : Reversed.list(ss)) {
      log += s;
    }

    assertEquals("abccba", log);
  }
}
