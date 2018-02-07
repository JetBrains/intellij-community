class Pair<A, B> {
  Pair(A a, B b){}
}

enum MyEnum {
  C1(
    new Pair<>("", 1),
    new Pair<>("", 1),
    new Pair<>("", 1),
    new Pair<>("", 1)
  ),
  C2(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C3(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C4(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C5(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C6(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C7(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C8(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C9(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  ),
  C10(
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2),
    new Pair<>("", 2)
  );
  MyEnum(Pair<String, Integer>... pairs) {}
  MyEnum(String displayName, Pair<String, Integer>... pairs) {}
}