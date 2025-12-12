// "Implement methods" "true-preview"
class Container {

  private int brushColor = 0x1;

  private enum Content {
    Value1 {
      @Override
      public int method1(Container e) {
        return 0;
      }
    },
    Value2 {
      @Override
      public int method1(Container e) {
        return 0xff000000;
      }
    },
    Value3 {
        @Override
        public int method1(Container e) {
            return 0;
        }

        @Override
      public void method2(
        Container iconEditor,
        Object g
      ) {

      }
    };

    public abstract int method1(Container e);

    public void method2(
      Container iconEditor,
      Object g
    ) {

    }
  }

}