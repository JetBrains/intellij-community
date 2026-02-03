class ExpressionsWithComments {
  String foo1(int n) {
    return switch (n) {
      //comment1
      //comment2
      //comment3
      case 1 -> "A"; //comment4
      case 2 -> "B";
      //comment1
      //comment2
      //comment3
      case 3 -> <weak_warning descr="Duplicate branch in 'switch'">"A";</weak_warning> //comment4
      default -> "";
    };
  }

  String foo2(int n) {
    return switch (n) {
      //comment1
      case 1 -> "A";
      case 2 -> "B";
      //comment1
      case 3 ->"A";//comment2
      default -> "";
    };
  }

  String foo3(int n) {
    return switch (n) {
      //comment1
      case 1 -> <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">"A";</weak_warning> //comment2
      case 2 -> "B";
      case 3 -> "C";
      //comment1
      default -> "A"; //comment2
    };
  }

  String foo4(int n) {
    return switch (n) {
      //comment1
      case 1 -> {
        yield "A";//comment2
      }
      case 2 -> "B";
      //comment1
      case 3 -> <weak_warning descr="Duplicate branch in 'switch'">{
        yield "A";//comment2
      }</weak_warning>
      default -> "";
    };
  }
}