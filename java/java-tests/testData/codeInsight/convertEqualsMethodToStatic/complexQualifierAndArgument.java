import java.lang.String;

class X {
  void m() {

    if ("asd".intern().equa<caret>ls(String.valueOf("qwe"))) {
      // do something
    }

  }
}