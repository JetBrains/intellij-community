// "Replace Stream API chain with loop" "false"

import java.util.List;

public class Main {
  public void test2(List<? extends CharSequence> list) {
    list.stream().map(String::length).for<caret>Each(System.out::println);
  }
}