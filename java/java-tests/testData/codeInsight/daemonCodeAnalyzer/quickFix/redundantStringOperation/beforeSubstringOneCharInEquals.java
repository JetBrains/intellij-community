// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    int i = Integer.parseInt(args[2]);
    int j = Integer.parseInt(args[4]);

    boolean value = args[0].substring(4, 5).equals("_");

    if (args[0].sub<caret>string(4, 5).equals("_")) { }
    if (!args[0].substring(4, 5).equals("_")) { }
    if (!!args[0].substring(4, 5).equals("_")) { }
    if (!!!!!args[0].substring(4, 5).equals("_")) { }

    if (/* one */args/* two */[/* three */ 0 /* four */].substring(i /* five */ + 1, i + 2).equals("x")) { }
    if (!/* one */args/* two */[/* three */ 0 /* four */].substring(i /* five */ + 1, i + 2).equals("x")) { }
    if (!/* one */(!!args/* two */[/* three */ 0 /* four */].substring(i /* five */ + 1, i + 2).equals("x"))) { }

    if (args[0].substring(i - ((j - 1) - (j + 1)), i - ((j - 2) - (j + 1))).equals("\'")) {}
  }
}