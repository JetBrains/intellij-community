// "Move switch branch 'Integer i' before 'Number n'" "true"
class Main {
  void test(Object o) {
    switch (o) {
        case Integer i:
            System.out.println(1);
            break;
        case Number n:
        System.out.println(2);
        break;
      case String s:
        System.out.println(3);
        break;
        default:
        System.out.println(4);
        break;
    }
  }
}