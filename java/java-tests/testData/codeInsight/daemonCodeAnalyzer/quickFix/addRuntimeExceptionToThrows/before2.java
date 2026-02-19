// "Add 'throws RuntimeException' to method signature" "false"
class a {
   int f() throws RuntimeException{
     throw new RuntimeException()<caret>;
   }
}

