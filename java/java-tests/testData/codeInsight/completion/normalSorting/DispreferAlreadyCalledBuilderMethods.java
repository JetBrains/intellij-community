class Test {
  {
    MyBuilder b;
    b.a().b().<caret>
  }
}

interface MyBuilder {
  MyBuilder a();
  MyBuilder b();
  MyBuilder c();
  MyBuilder d();
}