class MyTest {
  void m(int i) {
    int j = switch(i) {
      case 1:
         System.out.println();
      def<caret>ault:
         System.out.println();
         break 42;
      case 2: 
        System.out.println();
        break 45;
    }
  }
}