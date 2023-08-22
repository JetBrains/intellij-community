import org.jetbrains.annotations.*;

class Test {
  public static void main(String[] args) {
    try {
      int[] array = {1, 2, 3, 4};
      System.out.println(array[<warning descr="Array index is out of bounds">4</warning>]);
    } finally {
      // False-positive, see IDEA-270306
      System.out.<warning descr="Method invocation 'println' may produce 'NullPointerException'">println</warning>("finally"); // IDEA warning: Method invocation 'println' may produce 'NullPointerException'
    }
  }
}