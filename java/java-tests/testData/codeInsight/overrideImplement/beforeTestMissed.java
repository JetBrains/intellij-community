class f{
  @org.testng.annotations.BeforeMethod
  String foo() {return "";}
}

class ff extends f {
  <caret>
}