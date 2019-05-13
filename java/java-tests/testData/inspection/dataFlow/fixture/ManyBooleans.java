import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class ManyBooleans {
  void test(boolean b1, boolean b2, boolean b3,
            boolean b4, boolean b5, boolean b6,
            boolean b7, boolean b8, boolean b9,
            boolean b10, boolean b11, boolean b12,
            boolean b13, boolean b14, boolean b15,
            boolean b16, boolean b17, boolean b18) {
    if(b1 && b2 && b3 && b4 && b5 && b6 && b7 && b8 && b9 && b10 && b11 && b12 && b13 && b14 && b15 && b16 && b17 && b18) {
      System.out.println("hello");
    }
    if(!b1 && !b2 && !b3 && !b4 && !b5 && !b6 && !b7 && !b8 && !b9 && !b10 && !b11 && !b12 && !b13 && !b14 && !b15 && !b16 && !b17 && !b18) {
      System.out.println("bye");
    }
  }
}