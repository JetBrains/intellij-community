class Test {
     <T extends String> int getValue() {
          return 3;
     }

     void cc() {
          <caret>getValue();
     }
}