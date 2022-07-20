// "Add runtime exception(s) to method signature" "true-preview"
class a {
   int f() throws RuntimeException {
     throw new RuntimeException()<caret>;
   }
}

