class C {
  void foo(int n) {
    switch (n) {
      case 1 -> {}
      case 2 -> <weak_warning descr="Duplicate branch in 'switch'">{}</weak_warning>
      case 3 -> <weak_warning descr="Duplicate branch in 'switch'">{}</weak_warning>
    }
  }
}