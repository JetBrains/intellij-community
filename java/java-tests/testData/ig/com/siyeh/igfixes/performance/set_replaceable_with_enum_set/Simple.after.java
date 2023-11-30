import java.util.*;

public class EnumSetDemo {
  public enum Numbers {
    ONE, TWO, THREE
  }

  public static void main(String[] args) {
    Set<Numbers> set = EnumSet.noneOf(Numbers.class);
  }
}