// "Add runtime exception(s) to method signature" "true-preview"
class a {
   int f() {
     throw new RuntimeException()<caret>;
   }
}

