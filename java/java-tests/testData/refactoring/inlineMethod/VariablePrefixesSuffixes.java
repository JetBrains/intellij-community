class InlineMethodExample {

  public void displayMessage(String theMessage) {
    System.out.println("The message is: " + theMessage);
  }

  public void testInline() {
    String message = "Hello, world!";
    display<caret>Message(message);
  }
}