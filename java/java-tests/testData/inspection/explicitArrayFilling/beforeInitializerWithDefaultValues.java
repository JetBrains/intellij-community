// "Remove 'for' statement" "true"

class Test {

  void fillByteArray() {
    byte[] plaintext = {0,0,0,0,0};
    for (<caret>int counter = 0; counter < plaintext.length; counter++) {
      plaintext[counter] = 0;
    }
  }
}