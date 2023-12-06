class StringTemplate1 {

  String x(int i) {
    return STR." <warning descr="'\'' is unnecessarily escaped"><caret>\'</warning>\{i}<warning descr="'\'' is unnecessarily escaped">\'</warning><warning descr="'\'' is unnecessarily escaped">\'</warning>\{i}<warning descr="'\'' is unnecessarily escaped">\'</warning><warning descr="'\'' is unnecessarily escaped">\'</warning><warning descr="'\'' is unnecessarily escaped">\'</warning>";
  }
}