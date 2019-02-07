class C {
  String test(int i) {
    switch (i) {
      case 0: return null;
      case 1: <weak_warning descr="Duplicate branch in 'switch' statement">return (null);</weak_warning>
    }
    return "";
  }
}