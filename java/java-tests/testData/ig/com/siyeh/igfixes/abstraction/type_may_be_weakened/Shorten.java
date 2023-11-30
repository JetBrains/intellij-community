package com.siyeh.igfixes.abstraction.type_may_be_weakened;

import java.util.ArrayList;
import java.util.List;

public class Shorten {

  private static void m() {
    List<String> <caret>players = new ArrayList<String>();
    players.add("new Player()");
  }
}