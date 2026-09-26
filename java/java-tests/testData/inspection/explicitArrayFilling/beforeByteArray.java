// "Replace loop with 'Arrays.fill()' method call" "true"

class Test {

  void fillByteArray() {
    byte[] plaintext = {1,2,3,4,5};
    for (<caret>int counter = 0; counter < plaintext.length; counter++) {
      plaintext[counter] = 0;
    }
  }
}