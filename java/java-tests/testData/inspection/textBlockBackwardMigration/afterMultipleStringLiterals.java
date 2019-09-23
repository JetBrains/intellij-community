// "Replace with regular string literal" "true"

class TextBlockMigration {

  String multipleLiterals() {
    return "public static void print(Object o) {\n" +
           "  System.out.println(o);\n" +
           "}\n" +
           "\n" +
           "public static void main(String[] args) {\n" +
           "  print(\"test\");\n" +
           "}\n";
  }
}