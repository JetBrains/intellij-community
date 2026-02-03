class RegexTest {
  void testRegexNamedGroups() {
    // Non-ASCII characters in named group names should be highlighted
    // language=regexp
    String a = "(?<<error descr="Invalid group name"><warning descr="Non-ASCII characters">水</warning></error>>a)";
    // language=regexp
    String b = "(?<<error descr="Invalid group name">group_<warning descr="Non-ASCII characters">水</warning></error>>a)";
    // language=regexp
    String c = "(?<<error descr="Invalid group name"><warning descr="Non-ASCII characters">水</warning>_group</error>>a)";
    // language=regexp
    String d = "(?<<error descr="Invalid group name">my_<warning descr="Non-ASCII characters">группа</warning>_123</error>>a)";
    
    // Multiple named groups with non-ASCII
    // language=regexp
    String e = "(?<<error descr="Invalid group name"><warning descr="Non-ASCII characters">первая</warning></error>>a)(?<<error descr="Invalid group name"><warning descr="Non-ASCII characters">вторая</warning></error>>b)";
    
    // ASCII only group names should not be highlighted
    // language=regexp
    String f = "(?<group>a)";
    // language=regexp
    String g = "(?<<error descr="Invalid group name">my_group_123</error>>a)";
    
    // Non-ASCII in pattern body (not in group name) - these are not checked by this test
    // language=regexp
    String h = "(?<group>水)";
    // language=regexp
    String i = "水";
  }
}
