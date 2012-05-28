// "Replace with 'list != null ?:'" "true"

import org.jetbrains.annotations.NotNull;

class A{
  void test(@NotNull List l) {
    final List list = null;
    test(list != null ? list : <selection>null</selection>);
  }
}