import java.util.Collection;

class A {
  String join(Collection<String> strings) {
    return StringUtil.join(strings, ",");
  }

  void method(Collection<String> param) {
    new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String[] param = {""}; // hides the parameter
        Collection<String> params = Arrays.asList(param);
        join(<caret>)
      }
    };
  }
}
