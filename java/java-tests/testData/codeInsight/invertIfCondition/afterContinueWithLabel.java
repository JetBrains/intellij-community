// "Invert 'if' condition" "true-preview"
class A {
    void foo () {
      outer: for(int i = 0; i < 5; i++) {
        for(int j = 0; j < 5; j++) {
            if (j != 3) {
                System.out.println("J:" + j);
            }
            else {
                continue outer;
            }
        }
        System.out.println("I:" + i);
      }
    }
}