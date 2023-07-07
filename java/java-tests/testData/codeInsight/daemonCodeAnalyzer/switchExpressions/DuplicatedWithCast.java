import java.util.Random;

class DuplicatedWithCast {

  void m(String... args) {

    final byte b = 127;
    switch (new Random().nextInt()) {
      case <error descr="Duplicate label '127'">b</error> -> System.out.println("b=" + b + ";");
      case <error descr="Duplicate label '127'">127</error>-> System.out.println("sweet spot");
    }
  }
}