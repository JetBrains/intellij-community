// "Bind method parameters to fields" "true"

class A{
      private final String myP;
      private final String myLongParameterName;
      private final String myName;
  
      void f(String p, String longParameterName, String shortParameterName){
          myP = p;
          myLongParameterName = longParameterName;
          myName = shortParameterName;
      }
}

