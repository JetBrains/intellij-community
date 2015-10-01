import java.lang.String;

class X {
  void m() {

    if (java.util.Objects.equals("asd".intern(), String.valueOf("qwe"))) {
      // do something
    }

  }
}