// "Generate overloaded method with default parameter values" "true"
class Test {
    void method() {
        method(null, (Integer) null);
    }

    void method(String s, Integer i) {}
  
  void method(Integer i, Double d) {}
}