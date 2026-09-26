import java.util.*;

public class IntLongTypeConversion {
  public static void main(String[] args) {
    int i = Integer.MAX_VALUE;
    int j = Integer.MAX_VALUE;
    long k = 2L;
    System.out.println(<warning descr="Condition 'i + j + k == j + k + i' is always 'false'"><warning descr="Result of 'i + j + k' is always '0'">i + j + k</warning> == j + k + i</warning>);
  }
}