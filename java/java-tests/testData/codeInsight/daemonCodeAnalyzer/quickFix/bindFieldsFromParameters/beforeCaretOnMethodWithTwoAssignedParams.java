// "Bind method parameters to fields" "false"

class A{
      private final String myP1;
      private final String myP2;
  
      void <caret>f(String p1, String p2){
          myP1 = p1;
          myP2 = p2;
      }
}

