// "Bind method parameters to fields" "true-preview"

class A{
      private final String myP;
      private final String myLongParameterName;
      private final String myName;
  
      void <caret>f(String p, String longParameterName, String shortParameterName){
      }
}

