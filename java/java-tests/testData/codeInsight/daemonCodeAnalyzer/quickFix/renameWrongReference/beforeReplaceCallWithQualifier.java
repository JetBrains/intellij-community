// "Replace with qualifier" "true-preview"
class C {
  void () {
    int i = 1;
    System.out.println(i.toString<caret>/*1*/());
  }
}