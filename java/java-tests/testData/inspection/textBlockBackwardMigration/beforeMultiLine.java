// "Replace with regular string literal" "true"

class TextBlockMigration {

  String multiLine() {
    return """<caret>
           public static void print(Object o) {
             System.out.println(o);
           }

           public static void main(String[] args) {
             print("test");
           }
           """;
  }
}