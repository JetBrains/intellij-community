class f{
  @org.testng.annotations.BeforeMethod
  String foo() {return "";}
}

class ff extends f {
    @Override
    String foo() {
        <selection>return super.foo();</selection>
    }
}