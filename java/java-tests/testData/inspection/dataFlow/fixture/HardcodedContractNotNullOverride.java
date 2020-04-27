import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;

class MyLinkedList extends LinkedList {
  @Override
  @NotNull
  public String peek() {
    return "aaa";
  }
}

class Usage {
  public static void main(String[] args) {
    MyLinkedList myLinkedList = new MyLinkedList();
    String v = myLinkedList.peek();
    System.out.println(v.toUpperCase());

  }
}