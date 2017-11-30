// "Split into separate declarations" "true"
class Test {
  {
    String foo <caret>= "foo",
    //c1
           bar        = "bar",//c2
           baz        = "baz";  //c3
  }
}