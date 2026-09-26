import static java.util.Arrays.copyOf;

import java.util.AbstractList;

// IDEA-355152
class Main<T> extends AbstractList<T> {

  @Override
  public T get(int index) {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  protected static class Node {
    protected void copy() {
      int[] ary = new int[]{1,2,3,4,5};
      copyOf(ary, 6);
    }
  }

  public static void main(String[] args) {
    Main<String> main = new Main<>();
  }
}