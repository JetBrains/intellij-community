// "Bind method parameters to fields" "false"

class A{
      private final String myP1;
  
      void <caret>f(String p1){
          myP1 = p1;
      }
}

