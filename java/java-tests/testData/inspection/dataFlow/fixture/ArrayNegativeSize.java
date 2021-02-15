class ArrayNegativeSize {
  private static final int VAL1 = -10;
  private static int VAL2 = -10;
  private static final int VAL3 = -10 * 2;
  private static final short VAL4 = -10 * 2;

  public static void main(String[] args) {
    if (Math.random() > 0.5) {
      int[] array1 = new int[<warning descr="Negative array size">-10</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array2 = createArray();
    }
    if (Math.random() > 0.5) {
      int[] array3 = new int[]{};
    }
    if (Math.random() > 0.5) {
      int[] array4 = new int[<warning descr="Negative array size">10 - 100</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array5 = new int[((int) (Integer.valueOf(Integer.MAX_VALUE).longValue() - 100)) + 100 - 10];
    }
    if (Math.random() > 0.5) {
      int[] array5 = new int[<warning descr="Negative array size">((int) (Integer.valueOf(Integer.MAX_VALUE).longValue() - 100L)) + 100 + 10</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array6 = new int[((int) (Integer.valueOf(Integer.MAX_VALUE).longValue() - 100)) + 99];
    }
    if (Math.random() > 0.5) {
      int[] array7 = new int[<warning descr="Negative array size">(int) (((long) ((Integer.MAX_VALUE - 100)) + 100) * 2)</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array8 = new int[num()];
    }
    if (Math.random() > 0.5) {
      int[] array9 = new int[<warning descr="Negative array size">VAL1</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array10 = new int[VAL2];
    }
    if (Math.random() > 0.5) {
      int[] array11 = new int[<warning descr="Negative array size">VAL3</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array12 = new int[<warning descr="Negative array size">VAL4</warning>];
    }
    if (Math.random() > 0.5) {
      int[][] array13 = new int[0][<warning descr="Negative array size">-1</warning>];
    }
    if (Math.random() > 0.5) {
      int[][][] array14 = new int[0][0][<warning descr="Negative array size">-1</warning>];
    }
    if (Math.random() > 0.5) {
      int[][][] array15 = new int[-1][-2][<warning descr="Negative array size">-3</warning>];
    }
    if (Math.random() > 0.5) {
      int[][][] array15 = new int[-1][<warning descr="Negative array size">-2</warning>][3];
    }
    if (Math.random() > 0.5) {
      int[][][] array15 = new int[<warning descr="Negative array size">-1</warning>][2][3];
    }
    if (Math.random() > 0.5) {
      int[] array16 = new int[<warning descr="Negative array size">-07</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array17 = new int[<warning descr="Negative array size">100 * -077</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array18 = new int[0x7fffffff];
    }
    if (Math.random() > 0.5) {
      int[] array19 = new int[<warning descr="Negative array size">0x7fffffff + 1</warning>];
    }
    if (Math.random() > 0.5) {
      int[] array20 = new int[0b1111111111111111111111111111111];
    }
    if (Math.random() > 0.5) {
      int[] array21 = new int[<warning descr="Negative array size">0b1111111111111111111111111111111 + 1</warning>];
    }
    if (Math.random() > 0.5) {
      action(new int[<warning descr="Negative array size">-1000000000</warning>]);
    }
    if (Math.random() > 0.5) {
      action(new int[<warning descr="Negative array size">-0xcafe</warning>]);
    }
    if (Math.random() > 0.5) {
      action(new int[<warning descr="Negative array size">(int) -10000000000000L</warning>]);
    }
    if (Math.random() > 0.5) {
      action(new int[<warning descr="Negative array size">2147483647 + 1</warning>]);
    }
    if (Math.random() > 0.5) {
      action(new int["".length() + 456]);
    }
    if (Math.random() > 0.5) {
      action(new int[<warning descr="Negative array size">"".length() - 456</warning>]);
    }
    VAL2++;
  }

  private static int[] createArray() {
    return new int[0];
  }

  private static void action(Object obj) {
  }

  private static int num() {
    return -123;
  }

  final int[] array1 = new int[<warning descr="Negative array size">-10</warning>];

  void foo(int size) {
    int[] data = new int[size];
    if (<warning descr="Condition 'size < 0' is always 'false'">size < 0</warning>) {
      System.out.println("Impossible");
    }
  }

  void tryCatch(int size) {
    try {
      int[] arr = new int[size];
    } catch (NegativeArraySizeException e) {
      if (<warning descr="Condition 'size >= 0' is always 'false'">size >= 0</warning>) {
        System.out.println("impossible");
      }
    }
  }
  void testUnboxing(Integer len) {
    int[] arr = new int[len];
    long l = len.longValue();
    if (<warning descr="Condition 'l < 0' is always 'false'">l < 0</warning>) {}
  }
}