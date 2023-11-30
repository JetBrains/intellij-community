// "Merge with 'case Integer _'" "true-preview"
class C {
  void foo2(Object o) {
    switch (o) {
      case Integer _, StringBuffer _ when o.hashCode()==1:
        case String _:
            System.out.println("hello");
        break;
        case CharSequence _:
        System.out.println("hello3");
        break;
      default:
        System.out.println("hello2");
        break;
    }
  }
}