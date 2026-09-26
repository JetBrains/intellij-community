// "Invert 'if' condition" "true-preview"
class A {
    void foo () {
      outer: for(int i = 0; i < 5; i++) {
        for(int j = 0; j < 5; j++) {
          i<caret>f (j == 3)
            continue outer;
          System.out.println("J:" + j);
        }
        System.out.println("I:" + i);
      }
    }
}