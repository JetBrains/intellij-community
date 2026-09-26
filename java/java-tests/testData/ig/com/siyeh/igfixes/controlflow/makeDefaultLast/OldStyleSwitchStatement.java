class MyTest {
  void m(int i) {
    int j = switch(i) {
      def<caret>ault:
         System.out.println();
         yield 4;//comment
      case 2: 
        System.out.println();
        yield 45;
    }
  }
}