// "Add Runtime Exception(s) to Method Signature" "false"
class a {
   int f() throws RuntimeException{
     throw new RuntimeException()<caret>;
   }
}

