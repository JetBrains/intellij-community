class Test {
  {
    MyBuilder b;
    b.x().b().addInt(1).putLong(2).mayCallManyTimes().mayCallManyTimes().<caret>
  }
}

interface MyBuilder {
  MyBuilder x();
  MyBuilder b();
  MyBuilder c();
  MyBuilder d();
  MyBuilder addInt(int a);
  MyBuilder putLong(long a);
  MyBuilder mayCallManyTimes();
}