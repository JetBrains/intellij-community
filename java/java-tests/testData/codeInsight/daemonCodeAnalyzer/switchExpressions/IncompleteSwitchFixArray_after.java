class IncompleteSwitchFixColon {

  public void testStatement(char o) {

    switch (o) {
      case '2'-> System.out.println("1");
      case '1' -><caret>
    }
  }
}
