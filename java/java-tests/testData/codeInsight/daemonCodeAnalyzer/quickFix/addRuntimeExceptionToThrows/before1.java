// "Add Runtime Exception(s) to Method Signature" "true"
class a {
   int f() {
     throw new RuntimeException()<caret>;
   }
}

