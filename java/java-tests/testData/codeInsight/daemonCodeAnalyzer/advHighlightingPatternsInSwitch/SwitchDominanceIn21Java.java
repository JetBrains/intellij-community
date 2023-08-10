import java.util.List;

record RecordInterface(I x, I y) {
}

interface Super {
}

record RecordSuper(int x) implements Super {
}

sealed interface I permits C, D {
}

final class C implements I {
}

final class D implements I {
}

class Dominance {

  Integer integer;
  Object object;

  void test() {
    switch (integer) {
      case Integer i -> {
      }
      case <error descr="Label is dominated by a preceding case label 'Integer i'">1</error> -> {
      } 
      default -> {
      }
    }
    switch (integer) {
      case Integer i when i > 0 -> {
      }
      case 1 -> {
      } 
      default -> {
      }
    }
    switch (integer) {
      case 1 -> {
      }
      case Integer i when i > 0 -> {
      }
      default -> {
      }
    }
    switch (integer) {
      case 1 -> {
      }
      case Integer i -> {
      }
    }
    switch (object) {
      case CharSequence s -> {
      }
      case <error descr="Label is dominated by a preceding case label 'CharSequence s'">String c</error> -> {
      } 
    }
    switch (object) {
      case CharSequence s -> {
      }
      case <error descr="Label is dominated by a preceding case label 'CharSequence s'">String c when c.length() > 0</error> -> {
      } 
    }
    switch (object) {
      case CharSequence s when true -> {
      }
      case <error descr="Label is dominated by a preceding case label 'CharSequence s when true'">String c</error> -> {
      } 
    }
    switch (object) {
      case CharSequence s when true -> {
      }
      case <error descr="Label is dominated by a preceding case label 'CharSequence s when true'">String c when c.length() > 0</error> -> {
      } 
    }
    switch (object) {
      case RecordInterface(C z, D y) -> {
      }
      case RecordInterface(I z, I y) -> {
      }
      default -> {
      }
    }
    switch (object) {
      case RecordInterface(I z, D y) -> {
      }
      case RecordInterface(C z, C y) -> {
      }
      default -> {
      }
    }
    switch (object) {
      case List<?> l -> System.out.println();
      case <error descr="Label is dominated by a preceding case label 'List<?> l'">List<?> l  when l.size() == 2</error> -> System.out.println(); 
        default -> throw new IllegalStateException("Unexpected value: " + object);
    }
    switch (integer) {
      case Integer i -> {
      }
      case null -> {
      }
    }
  }
}