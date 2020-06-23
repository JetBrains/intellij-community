class Test {
    String s1 = getString();    
    String s = s1 != null ? <selection>s1.trim()</selection> : "";
      
    native String getString();
}
