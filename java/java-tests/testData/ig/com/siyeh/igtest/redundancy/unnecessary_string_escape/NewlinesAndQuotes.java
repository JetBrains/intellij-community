class X {
  String s = """
<warning descr="'\'' is unnecessarily escaped">\'</warning>""";
  String t = """
<warning descr="'\n' is unnecessarily escaped"><caret>\n</warning>""";
  String u = """
<warning descr="'\\\\"' is unnecessarily escaped">\"</warning> ""\" "\"" \"""
<warning descr="'\\\\"' is unnecessarily escaped">\"</warning>"
"<warning descr="'\\\\"' is unnecessarily escaped">\"</warning>
""";
  String v = """
    abc<warning descr="'\n' is unnecessarily escaped">\n</warning>def""";
}