class Test {
  {
    MyBuilder b;
    b.x().b().addInt(1).putLong(2).mayCallManyTimes().mayCallManyTimes().append("a").<caret>
  }
}

interface MyBuilder {
  MyBuilder x();
  MyBuilder b();
  MyBuilder c();
  MyBuilder d();
  MyBuilder append(String s);
  MyBuilder addInt(int a);
  MyBuilder putLong(long a);
  MyBuilder mayCallManyTimes();
}