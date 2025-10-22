// "Add 'int' as 1st parameter to constructor 'Friend()'" "true"
class Friend {
  Friend() {
    System.out.println(0);
  }
}
class Flexible extends Friend {

  Flexible() {
    System.out.println("before");
    super(<caret>1);
    System.out.println("after");
  }
}