package com.siyeh.igtest.bugs.castConflictingInstanceof.andAnd;

class Test {

  public void test(Object o)
  {

    if (!(o instanceof Number) ||
        ((Number)o).intValue() == 0 ||
        !(o instanceof Integer) ||
        ((Integer) o).byteValue() == 9) {
      System.out.println();
    }

    if (!(o instanceof Number) ||
        ((Number)o).intValue() == 0 ||
        !(o instanceof Integer) ||
        !(o instanceof Number) ||
        ((Integer) o).byteValue() == 9) {
      System.out.println();
    }

    if (!(o instanceof Integer) ||
         ((Number)o).intValue() == 0 ||
        !(o instanceof Number) ||
        ((Integer) o).byteValue() == 9) {
      System.out.println();
    }
  }
}
