// "Replace with enhanced 'switch' statement" "true"

class A {
  void test(int x) {
      switch (x) {
          case 1 -> {
              System.out.println(1);

              //test1


              //test1

              System.out.println(2); //test2


              System.out.println(3); //test3
              System.out.println(4);

              System.out.println(5);
          }
      }
  }
}