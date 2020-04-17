// "Fix all 'Redundant String operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {

    boolean value = args[0].charAt(4) == '_';

    if(args[0].charAt(4) == '_') { }
    if(args[0].charAt(4) != '_') { }
    if(args[0].charAt(4) == '_') { }
    if(args[0].charAt(4) != '_') { }
  }
}