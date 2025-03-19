class StringTemplate {

  String x(String verb, String gerund) {
    return STR. """   
      It is difficult 
      <caret>to get a person to \{verb} something,
      when their salary depends upon their not \{gerund} it!""";
  }
}