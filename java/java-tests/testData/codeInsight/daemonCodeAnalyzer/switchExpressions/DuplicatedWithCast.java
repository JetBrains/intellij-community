import java.util.Random;

class DuplicatedWithCast {

  void m(String... args) {

    final byte b = 127;
    switch (new Random().nextInt()) {
      case <error descr="Duplicate label '127'">b</error> -> System.out.println("b=" + b + ";");
      case <error descr="Duplicate label '127'">127</error>-> System.out.println("sweet spot");
    }
    switch (args[0]) {
      case <error descr="Duplicate label 'null'">null</error> -> System.out.println("null1");
      case <error descr="Duplicate label 'null'">null</error> -> System.out.println("null2");
      default -> System.out.println("default");
    }
  }
}