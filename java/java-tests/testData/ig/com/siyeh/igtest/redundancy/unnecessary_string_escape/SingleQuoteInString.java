class X {
  String s = "'";
  char c = '\'';
  String t = "\\'";
  String u = "<warning descr="'\'' is unnecessarily escaped"><caret>\'</warning>";
  String v = "Escape  <warning descr="'\'' is unnecessarily escaped">\'</warning>'{0}'<warning descr="'\'' is unnecessarily escaped">\'</warning>";
  String w = "<warning descr="'\'' is unnecessarily escaped">\'</warning>'\\'";
}