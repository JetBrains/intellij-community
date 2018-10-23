class C {
  int foo(String s) {
    if (<weak_warning descr="Multiple occurrences of 's.split(\",\").length'">s.split(",").length</weak_warning> > 2) {
      return (<weak_warning descr="Multiple occurrences of 's.split(\",\").length'">s.split(",").length</weak_warning>);
    }
    return 0;
  }
}