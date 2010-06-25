// "Add Runtime Exception(s) to Method Signature" "true"
class a {
   int f() throws RuntimeException {
     throw new RuntimeException()<caret>;
   }
}

