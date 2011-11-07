class f{
  @org.testng.annotations.BeforeMethod
  String foo() {return "";}
}

class ff extends f {
    @Override
    String foo() {
        <selection>return super.foo();    //To change body of overridden methods use File | Settings | File Templates.</selection>
    }
}