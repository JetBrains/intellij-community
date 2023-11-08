package com.siyeh.igtest.bugs.mismatched_string_builder_query_update;

import java.util.*;
import java.util.function.*;

public class RepeatAbstractStringBuilder {
  static String testRepeat() {
    StringBuilder2 codes = new StringBuilder2();
    StringBuilder2 repeat = codes.repeat("1", 0);
    return repeat.toString();
  }

  static String testRepeat2() {
    StringBuilder2 <warning descr="Contents of 'StringBuilder2 codes' are updated, but never queried">codes</warning> = new StringBuilder2();
    codes.repeat("1", 0);
    return "";
  }
}