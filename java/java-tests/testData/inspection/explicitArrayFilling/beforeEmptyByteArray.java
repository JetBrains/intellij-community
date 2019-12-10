// "Remove 'for' statement" "true"

class Test {

  void fillByteArray() {
    byte[] plaintext = new byte[10];
    for (<caret>int counter = 0; counter < plaintext.length; counter++) {
      plaintext[counter] = 0;
    }
  }
}