class C {
  String empty = """
      """;
  String invalid1 = <error descr="Illegal text block start: missing new line after opening quotes">""""""</error>;
  String invalid2 = <error descr="Illegal text block start: missing new line after opening quotes">""" """</error>;
  String invalid3 = <error descr="Illegal text block start: missing new line after opening quotes">"""\\n """</error>;

  String s9 = "\s";
  String s10 = <error descr="Illegal escape character in string literal">" \ "</error>;

  String valid1 = """
    \s
    """;

  String valid2 = """
    \
    """;

  String backSlash1 = """
    \u005c\""";
}