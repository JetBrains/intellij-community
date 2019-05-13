class Test {

  public static class Node {
    public int x;
    public boolean condition;
    public Node next;
  }

  public static int test(Node cur) {
    Node prev = null;
    int total = 0;
    while (cur != null) {
      if (cur.condition) {
        if (prev != null) {
          total += prev.x;
        }
        prev = cur;
        cur = cur.next;
      } else {<selection>
        if (prev != null) {
          total += prev.x;
        }
        prev = cur;
        cur = cur.next;</selection>
      }
    }
    return total;
  }
}