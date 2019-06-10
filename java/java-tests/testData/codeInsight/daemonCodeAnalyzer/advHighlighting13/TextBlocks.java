class C {
  String empty = """
      """;
  String invalid1 = <error descr="Illegal text block start: missing new line after opening quotes">""""""</error>;
  String invalid2 = <error descr="Illegal text block start: missing new line after opening quotes">""" """</error>;
  String invalid3 = <error descr="Illegal text block start: missing new line after opening quotes">"""\\n """</error>;
}