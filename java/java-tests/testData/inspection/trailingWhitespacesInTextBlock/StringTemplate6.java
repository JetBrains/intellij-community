class StringTemplate1 {
  public void foo(String name, String phone, String address) {
    // only one highlight, although multiple fragments with trailing whitespace
    String s = STR."""
    {
        "name":    "\{name}",
        "phone":   "\{phone}",<warning descr="Trailing whitespace characters inside text block"><caret>   </warning>
        
        "address": "\{address}"  
    } 
    """;
  }
}