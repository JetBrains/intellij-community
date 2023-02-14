// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    int i = Integer.parseInt(args[2]);
    int j = Integer.parseInt(args[4]);

    boolean value = args[0].charAt(4) == '_';

    if (args[0].charAt(4) == '_') { }
    if (args[0].charAt(4) != '_') { }
    if (args[0].charAt(4) == '_') { }
    if (args[0].charAt(4) != '_') { }

    if (/* one */args/* two */[/* three */ 0 /* four */].charAt(i /* five */ + 1) == 'x') { }
      /* one */
      if (args/* two */[/* three */ 0 /* four */].charAt(i /* five */ + 1) != 'x') { }
      /* one */
      if (args/* two */[/* three */ 0 /* four */].charAt(i /* five */ + 1) != 'x') { }

    if (args[0].charAt(i - ((j - 1) - (j + 1))) == '\'') {}
  }
}