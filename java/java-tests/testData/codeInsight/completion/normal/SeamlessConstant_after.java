interface Bar {
  boolean ABC = 2;
}

class BarImpl implements Bar {
  {
    boolean a = ABC<caret>zzz;
  }
}