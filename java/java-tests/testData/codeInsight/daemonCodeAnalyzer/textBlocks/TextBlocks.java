class C {
  String empty = """
      """;
  String invalid1 = <error descr="Illegal text block start: missing new line after opening quotes">""""""</error>;
  String invalid2 = <error descr="Illegal text block start: missing new line after opening quotes">""" """</error>;
  String invalid3 = <error descr="Illegal text block start: missing new line after opening quotes">"""\\n """</error>;
  String invalid4 = """
                    invalid escape <error descr="Illegal escape character in string literal">\</error>
                    continue;
                    """;
  String invalid5 = """
                    \n\n\n\n<error descr="Illegal escape character in string literal">\</error>
                    """;
}