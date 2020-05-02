class B {
  void readFile(String s, Class c) {}

  {
    <selection>
     readFile("foo", B.class);
     this.readFile("foo", B.class);
    </selection>
    readFile("bar", B.class);
    this.readFile("bar", B.class);
  }
}
