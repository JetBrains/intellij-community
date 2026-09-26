// "Replace with 'list != null ?:'" "true-preview"

import org.jetbrains.annotations.NotNull;

class A{
  void test(@NotNull List l) {
    final List list = Math.random() > 0.5 ? new List() : null;
    test(list != null ? list : null<caret>);
  }
}