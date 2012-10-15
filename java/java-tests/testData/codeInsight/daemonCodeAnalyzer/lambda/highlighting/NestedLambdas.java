interface I {
  Integer _();
}

interface I1 {
  String _();
}

class Test {
  I i = () -> {
    I1 i1 = () -> {return "";};
    return 1;
  };
}