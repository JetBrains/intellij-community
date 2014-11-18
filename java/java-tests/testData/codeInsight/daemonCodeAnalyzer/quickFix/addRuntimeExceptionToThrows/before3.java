// "Add Runtime Exception(s) to Method Signature" "false"
class a {
   int f() {
     Runnable r = () -> {
       throw new RuntimeException()<caret>;
     }
   }
}

