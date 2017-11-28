interface I {
  Integer m();
}

interface I1 {
  String m();
}

class Test {
  I i = () -> {
    I1 i1 = () -> {return "";};
    return 1;
  };
}