// "Create field for parameter 'p1'" "false"

class Test{
      private final String myP1;
  
      <T extends String> void <caret>f(T p1){
          myP1 = p1;
      }
}

