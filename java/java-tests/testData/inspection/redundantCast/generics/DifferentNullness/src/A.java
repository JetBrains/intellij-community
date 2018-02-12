import org.jetbrains.annotations.*;

class A {
  static String doit(A a) {
    String d = ((AA)a).danuna();
    String notNull = ((AA)a).doadd();
    return notNull + d;
  }

  @Nullable
  String doadd() {
    return null;
  }

  @NotNull
  String danuna() {
    return "";
  }
}

class AA extends A {
  @NotNull
  String doadd() {
    return "";
  }

  @Override
  @NotNull
  String danuna() {
    return "";
  }
}