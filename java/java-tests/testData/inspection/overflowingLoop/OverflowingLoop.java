public class OverflowingLoop {
  void increment(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; i > 12; i++) {
      System.out.println(i + 23);
    }
  }

  void incrementWithInvertedCondition(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; 12 < i; i++) {
      System.out.println(i + 23);
    }
  }

  void decrement(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; i < 12; i--) {
      System.out.println(i + 23);
    }
  }

  void plusEq(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; i > 12; i += 12) {
      System.out.println(i + 23);
    }
  }

  void minusEq(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; i < 12; i -= 12) {
      System.out.println(i + 23);
    }
  }

  void minusEqAddNegative(int s) {
    for (int i = s; i > 12; i += -12) {
      System.out.println(i + 23);
    }
  }

  void minusEqAddPositive(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; i > 12; i += 12) {
      System.out.println(i + 23);
    }
  }

  void minusEqAddPositiveInsideLoop(int s) {
    <warning descr="Loop executes zero or billions of times">for</warning> (int i = s; i > 12; i += 12) {
      i++;
      System.out.println(i + 23);
    }
  }

  void updateVariableDiffers(int s) {
    int x = 0;
    for (int i = s; i > x; x ++) {
      System.out.println(i + 23);
    }
  }

  void updateInsideLoop(int s) {
    for (int i = s; i < 12; i--) {
      System.out.println(i + 23);
      i+= 100;
    }
  }

  void minusEqMinus(int s) {
    for (int i = 0; i < 10; i -=- 1) {
      System.out.println("asdsakdj");
    }
  }

  void byteUsage() {
    for (byte i = 0; i >= 0; i++) {
      System.out.println(i);
    }
  }
}
