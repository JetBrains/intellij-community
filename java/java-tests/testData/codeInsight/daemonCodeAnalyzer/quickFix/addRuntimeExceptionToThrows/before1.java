// "Add runtime exception(s) to method signature" "true"
class a {
   int f() {
     throw new RuntimeException()<caret>;
   }
}

