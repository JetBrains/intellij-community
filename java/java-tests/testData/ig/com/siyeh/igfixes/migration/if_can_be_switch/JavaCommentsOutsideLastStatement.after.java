class Comments {
  String foo(int par) {
      <caret>return switch (par) {
          case 11 -> "ciao";/* case 1*/
          case 14 -> "fourteen";//case 2
          case 15 -> "fifteen"; //case 3
          case 16 ->
              //case 4
                  "sixteen";
          default -> "default";
      };


  }
}