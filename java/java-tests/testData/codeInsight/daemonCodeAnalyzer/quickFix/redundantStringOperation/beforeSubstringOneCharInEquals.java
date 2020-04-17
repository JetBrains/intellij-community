// "Fix all 'Redundant String operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {

    boolean value = args[0].substring(4, 5).equals("_");

    if(args[0].sub<caret>string(4, 5).equals("_")) { }
    if(!args[0].substring(4, 5).equals("_")) { }
    if(!!args[0].substring(4, 5).equals("_")) { }
    if(!!!!!args[0].substring(4, 5).equals("_")) { }
  }
}