// "Remove redundant call" "true"
class X {
  String s = "a$b"+"c".<caret>replace("$", "/");
}